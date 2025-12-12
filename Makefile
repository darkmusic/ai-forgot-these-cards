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
export USE_NEXUS_MAVEN NEXUS_MAVEN_MIRROR_URL POSTGRES_USER POSTGRES_DB
export NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL NEXUS_APT_MIRROR_SECURITY_UBUNTU_NOBLE_URL
export NEXUS_APT_MIRROR_DEBIAN_BOOKWORM_URL NEXUS_APT_MIRROR_SECURITY_DEBIAN_BOOKWORM_URL
endif

# Optional Maven cache via Nexus (disabled by default)
# Set USE_NEXUS_MAVEN=1 to enable Maven downloads through a local Nexus proxy.
# On Linux, we map host.docker.internal using --add-host for the build container.
USE_NEXUS_MAVEN ?= 0
# Default mirror URL points to Nexus 3's "maven-public" group (default installation).
# For a custom group named maven-group, use: http://host.docker.internal:8081/repository/maven-group/
NEXUS_MAVEN_MIRROR_URL ?= http://host.docker.internal:8081/repository/maven-public

# Optional flags for Docker build commands (e.g., --no-cache)
APP_DOCKER_BUILD_FLAGS ?=
WEB_DOCKER_BUILD_FLAGS ?=

# Linux helper so containers can reach services running on the host
DOCKER_HOST_GATEWAY ?= --add-host=host.docker.internal:host-gateway

# Allow overriding ports via .env (APP_SERVER_PORT already exists there)
APP_SERVER_PORT ?= 8080
WEB_HOST_PORT ?= 8086

.PHONY: clean test \
	drop-and-recreate-db export-db import-db export-db-container import-db-container \
	build up down restart build-deploy delete-redeploy down-with-volumes tail-tomcat-logs \
	redeploy-watch build-app-image build-web-image export-delete-redeploy \
	build-app-image-nocache build-web-image-nocache build-nocache \
	redeploy-app build-deploy-nocache

########################################################################
# Local Build Helpers
########################################################################

clean:
	@echo "Cleaning target directory."
	@if [ -d target ]; then rm -rf target; fi
	@if [ -d web ]; then rm -rf web; fi

test:
	@echo "Running unit tests."
	@./mvnw test

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
		postgres:17 postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'

export-db:
	@mkdir -p db
	@pg_dump -h localhost -p 5433 -U "$(POSTGRES_USER)" -W -F c -b -v -f /tmp/backup.sql "$(POSTGRES_DB)"
	@rm -f db/backup.sql
	@mv /tmp/backup.sql db/backup.sql

export-db-container:
	@mkdir -p db
	@docker exec -t "$(DB_CONTAINER)" sh -lc 'rm -f /tmp/backup.sql'
	@docker exec -it "$(DB_CONTAINER)" sh -lc "pg_dump -h localhost -U \"$(POSTGRES_USER)\" -W -F c -b -v -f /tmp/backup.sql \"$(POSTGRES_DB)\""
	@rm -f db/backup.sql
	@docker cp "$(DB_CONTAINER):/tmp/backup.sql" "db/backup.sql"

import-db: drop-and-recreate-db
	# Pause for a few seconds to ensure the DB is ready to accept connections
	@sleep 5
	@pg_restore -h localhost -p 5433 -U "$(POSTGRES_USER)" -W -F c -v -d "$(POSTGRES_DB)" db/backup.sql

import-db-container: drop-and-recreate-db
	# Pause for a few seconds to ensure the DB is ready to accept connections
	@sleep 5
	@docker cp "db/backup.sql" "$(DB_CONTAINER):/tmp/backup.sql"
	@docker exec -it "$(DB_CONTAINER)" sh -lc "pg_restore -h localhost -U \"$(POSTGRES_USER)\" -W -F c -v -d \"$(POSTGRES_DB)\" /tmp/backup.sql"

#######################################################################
# Docker Commands (no docker compose)
#######################################################################

build-app-image:
	@# Conditionally pass a Maven mirror for dependency caching
	@if [ "$(USE_NEXUS_MAVEN)" = "1" ]; then \
		echo "Building app image with Maven mirror: $(NEXUS_MAVEN_MIRROR_URL)"; \
		docker build $(APP_DOCKER_BUILD_FLAGS) --add-host=host.docker.internal:host-gateway \
			--build-arg MAVEN_MIRROR_URL="$(NEXUS_MAVEN_MIRROR_URL)" \
			--build-arg NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL="$(NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL)" \
			--build-arg NEXUS_APT_MIRROR_SECURITY_UBUNTU_NOBLE_URL="$(NEXUS_APT_MIRROR_SECURITY_UBUNTU_NOBLE_URL)" \
			-t "$(APP_IMAGE)" -f dockerfiles/app/Dockerfile .; \
	else \
		docker build $(APP_DOCKER_BUILD_FLAGS) \
			--build-arg NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL="$(NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL)" \
			--build-arg NEXUS_APT_MIRROR_SECURITY_UBUNTU_NOBLE_URL="$(NEXUS_APT_MIRROR_SECURITY_UBUNTU_NOBLE_URL)" \
			-t "$(APP_IMAGE)" -f dockerfiles/app/Dockerfile .; \
	fi

build-app-image-nocache: APP_DOCKER_BUILD_FLAGS=--no-cache
build-app-image-nocache: build-app-image

build-web-image:
	@docker build $(WEB_DOCKER_BUILD_FLAGS) -t "$(WEB_IMAGE)" \
		--build-arg NEXUS_APT_MIRROR_DEBIAN_BOOKWORM_URL="$(NEXUS_APT_MIRROR_DEBIAN_BOOKWORM_URL)" \
		--build-arg NEXUS_APT_MIRROR_SECURITY_DEBIAN_BOOKWORM_URL="$(NEXUS_APT_MIRROR_SECURITY_DEBIAN_BOOKWORM_URL)" \
		-f dockerfiles/web/Dockerfile .

build-web-image-nocache: WEB_DOCKER_BUILD_FLAGS=--no-cache
build-web-image-nocache: build-web-image

build: build-app-image build-web-image

build-nocache: build-app-image-nocache build-web-image-nocache

up:
	@# ensure network and volume
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null

	@# db: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
		docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
			--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
			postgres:17 postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
	fi

	@# app: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then \
		docker run $(DOCKER_HOST_GATEWAY) -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "$(APP_SERVER_PORT):8080" -p "9090:9090" \
			--env-file .env "$(APP_IMAGE)"; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker start "$(APP_CONTAINER)"; fi; \
	fi

	@# web: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(WEB_CONTAINER)"; then \
		docker run -d --name "$(WEB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "$(WEB_HOST_PORT):80" --env-file .env \
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


redeploy-app:
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker rm -f "$(APP_CONTAINER)"; fi
	@docker run $(DOCKER_HOST_GATEWAY) -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "$(APP_SERVER_PORT):8080" -p "9090:9090" \
		--env-file .env "$(APP_IMAGE)"

build-deploy:
	@$(MAKE) build
	@$(MAKE) up
	@$(MAKE) redeploy-app

build-deploy-nocache:
	@$(MAKE) build-nocache
	@$(MAKE) up
	@$(MAKE) redeploy-app

delete-redeploy: down-with-volumes build up

export-delete-redeploy: up export-db delete-redeploy

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
	@echo "  clean                         - Clean target directories."
	@echo "  drop-and-recreate-db          - Drop and recreate the PostgreSQL database."
	@echo "  export-db                     - Export the PostgreSQL database to db/backup.sql."
	@echo "  export-db-container           - Export the PostgreSQL database from the DB container to db/backup.sql."
	@echo "  import-db                     - Import the PostgreSQL database from db/backup.sql."
	@echo "  import-db-container           - Import the PostgreSQL database from db/backup.sql into the DB container."
	@echo "  build-app-image               - Build the application Docker image."
	@echo "  build-app-image-nocache       - Build the application Docker image (no cache)."
	@echo "  build-web-image               - Build the web Docker image."
	@echo "  build-web-image-nocache       - Build the web Docker image (no cache)."
	@echo "  build                         - Build both application and web Docker images."
	@echo "  build-nocache                 - Build both images (no cache)."
	@echo "  test                          - Run unit tests."
	@echo "  up                            - Start the application, database, and web containers."
	@echo "  up-core                       - Start only the application + database containers (no Nginx)."
	@echo "  build-core-nocache            - Build the application Docker image (no cache) (core stack)."
	@echo "  down                          - Stop and remove the application, database, and web containers."
	@echo "  down-core                     - Stop and remove only the application + database containers."
	@echo "  stop                          - Stop the application, database, and web containers."
	@echo "  stop-core                     - Stop only the application + database containers."
	@echo "  restart                       - Restart the application, database, and web containers."
	@echo "  build-deploy                  - Build images and deploy the application container."
	@echo "  build-deploy-core             - Build the app image and deploy the core stack (app + db only)."
	@echo "  build-deploy-core-nocache     - Build the app image (no cache) and deploy the core stack (app + db only)."
	@echo "  build-deploy-nocache          - Build images (no cache) and deploy the application container."
	@echo "  delete-redeploy               - Delete containers and volumes, then rebuild and redeploy."
	@echo "  export-delete-redeploy        - Export DB, delete containers/volumes, redeploy, and import DB."
	@echo "  down-with-volumes             - Stop and remove containers and associated volumes."
	@echo "  tail-tomcat-logs              - Tail the logs of the application container."
	@echo "  tail-core-logs                - Tail the logs of the application container (core stack)."
	@echo "  redeploy-watch                - Redeploy and watch application logs."
	@echo "  nexus-up                      - Start Sonatype Nexus (data + server) for Maven caching on port 8081."
	@echo "  nexus-status                  - Show status of Nexus containers."
	@echo "  nexus-logs                    - Tail Nexus logs."
	@echo "  nexus-down                    - Stop and remove Nexus containers."
	@echo "  build-llamacpp-cuda           - Build the llama.cpp CUDA server."
	@echo "  build-llamacpp-cpu            - Build the llama.cpp CPU server."
	@echo "  start-llamacpp                - Start the llama.cpp server with specified model and port."

#######################################################################
# Llamacpp Commands
#######################################################################

.PHONY: build-llamacpp-cpu
build-llamacpp-cpu:
	@echo "Building llama.cpp CPU server..."
	cd dep/llama.cpp && \
	   git checkout master && \
	   git pull && \
		 cmake -B build && \
		 cmake --build build --config Release -j 4

.PHONY: build-llamacpp-cuda
build-llamacpp-cuda:
	@echo "Building llama.cpp CUDA server..."
	cd dep/llama.cpp && \
	   git checkout master && \
	   git pull && \
		 cmake -B build -DGGML_CUDA=ON && \
		 cmake --build build --config Release -j 4

.PHONY: start-llamacpp
start-llamacpp:
	@./dep/llama.cpp/build/bin/llama-server --model $(LLAMA_MODEL_PATH) --port $(LLAMACPP_PORT) --host 0.0.0.0

.PHONY: up-core down-core stop-core build-core build-core-nocache build-deploy-core build-deploy-core-nocache tail-core-logs

build-core: build-app-image

build-core-nocache: build-app-image-nocache

up-core:
	@# ensure network and volume
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null

	@# db: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
		docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
			--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
			postgres:17 postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
	fi

	@# app: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then \
		docker run $(DOCKER_HOST_GATEWAY) -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "$(APP_SERVER_PORT):8080" -p "9090:9090" \
			--env-file .env "$(APP_IMAGE)"; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker start "$(APP_CONTAINER)"; fi; \
	fi

	@echo "Core stack is up."
	@echo "UI + API: http://localhost:$(APP_SERVER_PORT)"
	@echo "API base: http://localhost:$(APP_SERVER_PORT)/api"

down-core:
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker rm -f "$(APP_CONTAINER)"; fi
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker rm -f "$(DB_CONTAINER)"; fi

stop-core:
	@docker stop "$(APP_CONTAINER)" "$(DB_CONTAINER)"

tail-core-logs:
	@docker logs -f "$(APP_CONTAINER)"

build-deploy-core:
	@$(MAKE) build-core
	@$(MAKE) up-core
	@$(MAKE) redeploy-app

build-deploy-core-nocache:
	@$(MAKE) build-core-nocache
	@$(MAKE) up-core
	@$(MAKE) redeploy-app