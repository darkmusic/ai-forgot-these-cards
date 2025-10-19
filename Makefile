SHELL := bash

# All build steps are containerized; no local JDK/Node/Maven required
DOCKER_NETWORK := cards-net
DB_CONTAINER := db
APP_CONTAINER := app
WEB_CONTAINER := web
NEXUS_DATA_CONTAINER := nexus-data
NEXUS_CONTAINER := nexus
APP_IMAGE := aiforgot/app:latest
WEB_IMAGE := aiforgot/web:latest
DB_VOLUME := pgdata

# Load optional environment overrides from .env (if present)
ifneq (,$(wildcard .env))
include .env
export USE_NEXUS NEXUS_MIRROR_URL POSTGRES_USER POSTGRES_DB
endif

# Optional Maven cache via Nexus (disabled by default)
# Set USE_NEXUS=1 to enable Maven downloads through a local Nexus proxy.
# On Linux, we map host.docker.internal using --add-host for the build container.
USE_NEXUS ?= 0
# Default mirror URL points to Nexus 3's "maven-public" group (default installation).
# For a custom group named maven-group, use: http://host.docker.internal:8081/repository/maven-group/
NEXUS_MIRROR_URL ?= http://host.docker.internal:8081/repository/maven-public

.PHONY: clean \
	drop-and-recreate-db export-db import-db \
	build up down restart build-deploy delete-redeploy down-with-volumes tail-tomcat-logs \
	redeploy-watch build-app-image build-web-image

########################################################################
# Local Build Helpers (noop) – kept for compatibility
########################################################################

clean:
	@echo "Clean target directory (no local build)."
	@if [ -d target ]; then rm -rf target; fi
	@if [ -d web ]; then rm -rf web; fi

#######################################################################
# Database Commands
#######################################################################

drop-and-recreate-db:
	@# remove existing db container if present
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker rm -f "$(DB_CONTAINER)"; fi
	@# remove and recreate volume
	@docker volume rm -f "$(DB_VOLUME)" >/dev/null 2>&1 || true
	@docker volume create "$(DB_VOLUME)" >/dev/null
	@# ensure network exists
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@# start db
	@docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
		--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
		postgres:latest postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'

export-db:
	@mkdir -p db
	@docker exec -t "$(DB_CONTAINER)" sh -lc 'rm -f /tmp/backup.sql'
	@docker exec -it "$(DB_CONTAINER)" sh -lc "pg_dump -h localhost -U \"$(POSTGRES_USER)\" -W -F c -b -v -f /tmp/backup.sql \"$(POSTGRES_DB)\""
	@rm -f db/backup.sql
	@docker cp "$(DB_CONTAINER):/tmp/backup.sql" "db/backup.sql"
import-db: drop-and-recreate-db
	@docker cp "db/backup.sql" "$(DB_CONTAINER):/tmp/backup.sql"
	@docker exec -it "$(DB_CONTAINER)" sh -lc "pg_restore -h localhost -U \"$(POSTGRES_USER)\" -W -F c -v -d \"$(POSTGRES_DB)\" /tmp/backup.sql"

#######################################################################
# Docker Commands (no docker compose)
#######################################################################

build-app-image:
	@# Conditionally pass a Maven mirror for dependency caching
	@if [ "$(USE_NEXUS)" = "1" ]; then \
		echo "Building app image with Maven mirror: $(NEXUS_MIRROR_URL)"; \
		docker build --add-host=host.docker.internal:host-gateway \
			--build-arg MAVEN_MIRROR_URL="$(NEXUS_MIRROR_URL)" \
			-t "$(APP_IMAGE)" -f dockerfiles/app/Dockerfile .; \
	else \
		docker build -t "$(APP_IMAGE)" -f dockerfiles/app/Dockerfile .; \
	fi

build-web-image:
	@docker build -t "$(WEB_IMAGE)" -f dockerfiles/web/Dockerfile .

build: build-app-image build-web-image

up:
	@# ensure network and volume
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null

	@# db: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
		docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
			--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
			postgres:latest postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
	fi

	@# app: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then \
		docker run -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "8080:8080" -p "9090:9090" \
			--env-file .env "$(APP_IMAGE)"; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker start "$(APP_CONTAINER)"; fi; \
	fi

	@# web: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(WEB_CONTAINER)"; then \
		docker run -d --name "$(WEB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "8086:80" --env-file .env \
			"$(WEB_IMAGE)"; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(WEB_CONTAINER)"; then docker start "$(WEB_CONTAINER)"; fi; \
	fi

down:
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(WEB_CONTAINER)"; then docker rm -f "$(WEB_CONTAINER)"; fi
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker rm -f "$(APP_CONTAINER)"; fi
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker rm -f "$(DB_CONTAINER)"; fi

restart: down up

stop:
	@docker stop "$(WEB_CONTAINER)" "$(APP_CONTAINER)" "$(DB_CONTAINER)"

build-deploy: build up
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker rm -f "$(APP_CONTAINER)"; fi
	@docker run -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "8080:8080" -p "9090:9090" \
		--env-file .env "$(APP_IMAGE)"

delete-redeploy: down-with-volumes build up

down-with-volumes:
	@$(MAKE) down
	@docker volume rm -f "$(DB_VOLUME)" >/dev/null 2>&1 || true
	@docker network rm "$(DOCKER_NETWORK)" >/dev/null 2>&1 || true

tail-tomcat-logs:
	@docker logs -f "$(APP_CONTAINER)"

#######################################################################
# Debugging Commands
#######################################################################

redeploy-watch: down-with-volumes build up tail-tomcat-logs

#######################################################################
# Nexus (Maven cache) Commands — runs independently of other targets
#######################################################################

.PHONY: nexus-up nexus-status nexus-logs nexus-down

# Start a Nexus 3 server with a persistent named volume
nexus-up:
	@docker volume inspect "$(NEXUS_DATA_CONTAINER)" >/dev/null 2>&1 || docker volume create "$(NEXUS_DATA_CONTAINER)" >/dev/null
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(NEXUS_CONTAINER)"; then \
		echo "Starting Nexus 3 on port 8081..."; \
		docker run -d -p 8081:8081 --name $(NEXUS_CONTAINER) -v $(NEXUS_DATA_CONTAINER):/nexus-data sonatype/nexus3; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(NEXUS_CONTAINER)"; then docker start "$(NEXUS_CONTAINER)"; fi; \
	fi
	@echo "Nexus 3 is starting. It can take ~1-2 minutes on first run."
	@echo "UI: http://localhost:8081"

nexus-status:
	@docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E "^(NAME|$(NEXUS_CONTAINER))"

nexus-logs:
	@docker logs -f "$(NEXUS_CONTAINER)"

# Optional: stop/remove Nexus. Note: other targets never affect Nexus.
nexus-down:
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(NEXUS_CONTAINER)"; then docker rm -f "$(NEXUS_CONTAINER)"; fi

#######################################################################
# Informative Commands
#######################################################################

.PHONY: help
help:
	@echo "Available make targets:"
	@echo "  clean                     - Clean target directories (no local build)."
	@echo "  drop-and-recreate-db      - Drop and recreate the PostgreSQL database."
	@echo "  export-db                 - Export the PostgreSQL database to db/backup.sql."
	@echo "  import-db                 - Import the PostgreSQL database from db/backup.sql."
	@echo "  build-app-image           - Build the application Docker image."
	@echo "  build-web-image           - Build the web Docker image."
	@echo "  build                     - Build both application and web Docker images."
	@echo "  up                        - Start the application, database, and web containers."
	@echo "  down                      - Stop and remove the application, database, and web containers."
	@echo "  stop                      - Stop the application, database, and web containers."
	@echo "  restart                   - Restart the application, database, and web containers."
	@echo "  build-deploy              - Build images and deploy the application container."
	@echo "  delete-redeploy           - Delete containers and volumes, then rebuild and redeploy."
	@echo "  down-with-volumes         - Stop and remove containers and associated volumes."
	@echo "  tail-tomcat-logs          - Tail the logs of the application container."
	@echo "  redeploy-watch            - Redeploy and watch application logs."
	@echo "  nexus-up                  - Start Sonatype Nexus (data + server) for Maven caching on port 8081."
	@echo "  nexus-status              - Show status of Nexus containers."
	@echo "  nexus-logs                - Tail Nexus logs."
	@echo "  nexus-down                - Stop and remove Nexus containers (not called by any other target)."
	@echo "  build-llamacpp-cpu        - Build the llama.cpp CPU server."
	@echo "  start-llamacpp            - Start the llama.cpp server with specified model and port."

#######################################################################
# Llamacpp Commands
#######################################################################

.PHONY: build-llamacpp-cpu
build-llamacpp-cpu:
	@echo "Building llama.cpp CPU server..."
	cd dep/llama.cpp && \
		 cmake -B build && \
		 cmake --build build --config Release

.PHONY: start-llamacpp
start-llamacpp:
	@./dep/llama.cpp/build/bin/llama-server --model $(LLAMA_MODEL_PATH) --port $(LLAMACPP_PORT) --host 0.0.0.0