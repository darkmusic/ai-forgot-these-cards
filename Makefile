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
export DB_VENDOR SQLITE_DB_PATH
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

# Database vendor selection (default: postgres). If DB_VENDOR=sqlite, we skip creating the Postgres container.
DB_VENDOR ?= postgres

# SQLite single-file mode helpers (used by up-sqlite targets)
SQLITE_HOST_DIR ?= ./db
SQLITE_CONTAINER_DIR ?= /db
SQLITE_CONTAINER_DB_PATH ?= $(SQLITE_CONTAINER_DIR)/cards.db
SQLITE_HOST_DIR_ABS = $(abspath $(SQLITE_HOST_DIR))

# Portable dump helpers (export/import between DB vendors)
PORTABLE_DUMP_HOST_DIR ?= ./db
PORTABLE_DUMP_HOST_DIR_ABS = $(abspath $(PORTABLE_DUMP_HOST_DIR))
PORTABLE_DUMP_CONTAINER_DIR ?= /db
PORTABLE_DUMP_FILE ?= portable-dump.zip
PORTABLE_DUMP_CONTAINER_PATH ?= $(PORTABLE_DUMP_CONTAINER_DIR)/$(PORTABLE_DUMP_FILE)
PORTABLE_DUMP_HOST_PATH ?= $(PORTABLE_DUMP_HOST_DIR)/$(PORTABLE_DUMP_FILE)
PORTABLE_IMPORT_MODE ?= truncate

.PHONY: clean test \
	drop-and-recreate-db export-db import-db export-db-container import-db-container \
	list-backups \
	build up down restart build-deploy delete-redeploy down-with-volumes tail-tomcat-logs \
	redeploy-watch build-app-image build-web-image export-delete-redeploy \
	build-app-image-nocache build-web-image-nocache build-nocache \
	redeploy-app build-deploy-nocache \
	up-sqlite up-core-sqlite redeploy-app-sqlite build-deploy-sqlite build-deploy-sqlite-nocache \
	run-standalone-sqlite run-standalone-postgres \
	portable-export-postgres portable-export-sqlite portable-import-postgres portable-import-sqlite validate-portable \
	migrate-postgres-to-sqlite migrate-sqlite-to-postgres

########################################################################
# Local Build Helpers
########################################################################

# True containerless, single-file mode: runs the executable WAR with SQLite.
# - Reads optional settings from .env (AI provider config, ports, etc)
# - Persists the DB as a local file (default: ./db/cards.db)
run-standalone-sqlite:
	@set -euo pipefail; \
		mkdir -p db; \
		war="$$(ls -1 target/*-exec.war 2>/dev/null | head -n 1 || true)"; \
		if [ -z "$$war" ]; then \
			echo "No executable WAR found under target/. Building with ./mvnw -DskipTests package ..."; \
			./mvnw -DskipTests package; \
			war="$$(ls -1 target/*-exec.war 2>/dev/null | head -n 1 || true)"; \
		fi; \
		if [ -z "$$war" ]; then \
			echo "ERROR: executable WAR not found (expected target/*-exec.war)"; \
			exit 1; \
		fi; \
		set -a; \
			[ -f .env ] && . ./.env || true; \
		set +a; \
		sqlite_path="$${SQLITE_DB_PATH:-./db/cards.db}"; \
		echo "Running $$war"; \
		echo "SQLite DB: $$sqlite_path"; \
		DB_VENDOR=sqlite SQLITE_DB_PATH="$$sqlite_path" java -jar "$$war"

# True containerless mode with an external Postgres.
# - Reads optional settings from .env (AI provider config, DB_URL override, ports, etc)
# - Defaults to localhost:5433 to match the Makefile's db container mapping, but you can override with DB_URL.
run-standalone-postgres:
	@set -euo pipefail; \
		war="$$(ls -1 target/*-exec.war 2>/dev/null | head -n 1 || true)"; \
		if [ -z "$$war" ]; then \
			echo "No executable WAR found under target/. Building with ./mvnw -DskipTests package ..."; \
			./mvnw -DskipTests package; \
			war="$$(ls -1 target/*-exec.war 2>/dev/null | head -n 1 || true)"; \
		fi; \
		if [ -z "$$war" ]; then \
			echo "ERROR: executable WAR not found (expected target/*-exec.war)"; \
			exit 1; \
		fi; \
		set -a; \
			[ -f .env ] && . ./.env || true; \
		set +a; \
		db_url="$${DB_URL:-jdbc:postgresql://localhost:5433/$${POSTGRES_DB:-cards}}"; \
		echo "Running $$war"; \
		echo "Postgres: $$db_url"; \
		DB_VENDOR=postgres DB_URL="$$db_url" java -jar "$$war"

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
	@set -e; \
		mkdir -p db; \
		if [ -f db/backup.sql ]; then \
			i=1; \
			while [ -f "db/backup$${i}.sql" ]; do i=$$((i+1)); done; \
			mv db/backup.sql "db/backup$${i}.sql"; \
			echo "Archived existing db/backup.sql -> db/backup$${i}.sql"; \
		fi; \
		rm -f /tmp/backup.sql; \
		pg_dump -h localhost -p 5433 -U "$(POSTGRES_USER)" -W -F c -b -v -f /tmp/backup.sql "$(POSTGRES_DB)"; \
		mv /tmp/backup.sql db/backup.sql

export-db-container:
	@set -e; \
		mkdir -p db; \
		if [ -f db/backup.sql ]; then \
			i=1; \
			while [ -f "db/backup$${i}.sql" ]; do i=$$((i+1)); done; \
			mv db/backup.sql "db/backup$${i}.sql"; \
			echo "Archived existing db/backup.sql -> db/backup$${i}.sql"; \
		fi; \
		docker exec -t "$(DB_CONTAINER)" sh -lc 'rm -f /tmp/backup.sql'; \
		docker exec -it "$(DB_CONTAINER)" sh -lc "pg_dump -h localhost -U \"$(POSTGRES_USER)\" -W -F c -b -v -f /tmp/backup.sql \"$(POSTGRES_DB)\""; \
		docker cp "$(DB_CONTAINER):/tmp/backup.sql" "db/backup.sql"

import-db: drop-and-recreate-db
	# Pause for a few seconds to ensure the DB is ready to accept connections
	@sleep 5
	@pg_restore -h localhost -p 5433 -U "$(POSTGRES_USER)" -W -F c -v -d "$(POSTGRES_DB)" db/backup.sql

import-db-container: drop-and-recreate-db
	# Pause for a few seconds to ensure the DB is ready to accept connections
	@sleep 5
	@docker cp "db/backup.sql" "$(DB_CONTAINER):/tmp/backup.sql"
	@docker exec -it "$(DB_CONTAINER)" sh -lc "pg_restore -h localhost -U \"$(POSTGRES_USER)\" -W -F c -v -d \"$(POSTGRES_DB)\" /tmp/backup.sql"

list-backups:
	@mkdir -p db
	@if ls db/backup*.sql >/dev/null 2>&1; then \
		ls -1 db/backup*.sql 2>/dev/null | sort -V; \
	else \
		echo "No backups found in db/"; \
	fi

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
	@if [ "$(DB_VENDOR)" != "sqlite" ]; then \
		docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null; \
	fi

	@# db (postgres): create or start (skipped when DB_VENDOR=sqlite)
	@if [ "$(DB_VENDOR)" != "sqlite" ]; then \
		if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
			docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
				--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
				postgres:17 postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
		else \
			if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
		fi; \
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

# SQLite mode: mount ./db into the app container and write the DB there
up-sqlite:
	@# ensure network and local sqlite directory
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@mkdir -p "$(SQLITE_HOST_DIR_ABS)"

	@# app: create or start (sqlite)
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then \
		docker run $(DOCKER_HOST_GATEWAY) -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "$(APP_SERVER_PORT):8080" -p "9090:9090" \
			--env-file .env -e DB_VENDOR=sqlite -e SQLITE_DB_PATH="$(SQLITE_CONTAINER_DB_PATH)" \
			-v "$(SQLITE_HOST_DIR_ABS):$(SQLITE_CONTAINER_DIR)" \
			"$(APP_IMAGE)"; \
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

up-core-sqlite:
	@# ensure network and local sqlite directory
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@mkdir -p "$(SQLITE_HOST_DIR_ABS)"

	@# app: create or start (sqlite)
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then \
		docker run $(DOCKER_HOST_GATEWAY) -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "$(APP_SERVER_PORT):8080" -p "9090:9090" \
			--env-file .env -e DB_VENDOR=sqlite -e SQLITE_DB_PATH="$(SQLITE_CONTAINER_DB_PATH)" \
			-v "$(SQLITE_HOST_DIR_ABS):$(SQLITE_CONTAINER_DIR)" \
			"$(APP_IMAGE)"; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker start "$(APP_CONTAINER)"; fi; \
	fi

	@echo "Core sqlite stack is up."
	@echo "UI + API: http://localhost:$(APP_SERVER_PORT)"
	@echo "API base: http://localhost:$(APP_SERVER_PORT)/api"

redeploy-app-sqlite:
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker rm -f "$(APP_CONTAINER)"; fi
	@mkdir -p "$(SQLITE_HOST_DIR_ABS)"
	@docker run $(DOCKER_HOST_GATEWAY) -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "$(APP_SERVER_PORT):8080" -p "9090:9090" \
		--env-file .env -e DB_VENDOR=sqlite -e SQLITE_DB_PATH="$(SQLITE_CONTAINER_DB_PATH)" \
		-v "$(SQLITE_HOST_DIR_ABS):$(SQLITE_CONTAINER_DIR)" \
		"$(APP_IMAGE)"

build-deploy:
	@$(MAKE) build
	@$(MAKE) up
	@$(MAKE) redeploy-app

build-deploy-nocache:
	@$(MAKE) build-nocache
	@$(MAKE) up
	@$(MAKE) redeploy-app

build-deploy-sqlite:
	@$(MAKE) build
	@$(MAKE) up-sqlite
	@$(MAKE) redeploy-app-sqlite

build-deploy-sqlite-nocache:
	@$(MAKE) build-nocache
	@$(MAKE) up-sqlite
	@$(MAKE) redeploy-app-sqlite

#######################################################################
# Portable DB Export/Import (Postgres <-> SQLite)
#######################################################################

# Export from Postgres (db container) to a portable ZIP at ./db/portable-dump.zip
portable-export-postgres:
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null
	@mkdir -p "$(PORTABLE_DUMP_HOST_DIR_ABS)"
	@# Ensure Postgres is running
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
		docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
			--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
			postgres:17 postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
	fi
	@docker run --rm --network "$(DOCKER_NETWORK)" --env-file .env \
		-v "$(PORTABLE_DUMP_HOST_DIR_ABS):$(PORTABLE_DUMP_CONTAINER_DIR)" \
		"$(APP_IMAGE)" \
		java -jar /usr/local/tomcat/ROOT-exec.war \
			--server.port=0 --management.server.port=0 \
			--aiforgot.portableDump.command=export \
			--aiforgot.portableDump.file="$(PORTABLE_DUMP_CONTAINER_PATH)"
	@echo "Wrote $(PORTABLE_DUMP_HOST_PATH)"

# Export from SQLite (./db/cards.db) to a portable ZIP at ./db/portable-dump.zip
portable-export-sqlite:
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@mkdir -p "$(SQLITE_HOST_DIR_ABS)"
	@docker run --rm --network "$(DOCKER_NETWORK)" --env-file .env \
		-e DB_VENDOR=sqlite -e SQLITE_DB_PATH="$(SQLITE_CONTAINER_DB_PATH)" \
		-v "$(SQLITE_HOST_DIR_ABS):$(SQLITE_CONTAINER_DIR)" \
		"$(APP_IMAGE)" \
		java -jar /usr/local/tomcat/ROOT-exec.war \
			--server.port=0 --management.server.port=0 \
			--aiforgot.portableDump.command=export \
			--aiforgot.portableDump.file="$(PORTABLE_DUMP_CONTAINER_PATH)"
	@echo "Wrote $(PORTABLE_DUMP_HOST_PATH)"

# Import portable ZIP into Postgres (mode: truncate or fail-if-not-empty)
portable-import-postgres:
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null
	@mkdir -p "$(PORTABLE_DUMP_HOST_DIR_ABS)"
	@# Ensure Postgres is running
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
		docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
			--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
			postgres:17 postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
	fi
	@docker run --rm --network "$(DOCKER_NETWORK)" --env-file .env \
		-v "$(PORTABLE_DUMP_HOST_DIR_ABS):$(PORTABLE_DUMP_CONTAINER_DIR)" \
		"$(APP_IMAGE)" \
		java -jar /usr/local/tomcat/ROOT-exec.war \
			--server.port=0 --management.server.port=0 \
			--aiforgot.portableDump.command=import \
			--aiforgot.portableDump.mode="$(PORTABLE_IMPORT_MODE)" \
			--aiforgot.portableDump.file="$(PORTABLE_DUMP_CONTAINER_PATH)"

# Import portable ZIP into SQLite (mode: truncate or fail-if-not-empty)
portable-import-sqlite:
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@mkdir -p "$(SQLITE_HOST_DIR_ABS)"
	@docker run --rm --network "$(DOCKER_NETWORK)" --env-file .env \
		-e DB_VENDOR=sqlite -e SQLITE_DB_PATH="$(SQLITE_CONTAINER_DB_PATH)" \
		-v "$(SQLITE_HOST_DIR_ABS):$(SQLITE_CONTAINER_DIR)" \
		"$(APP_IMAGE)" \
		java -jar /usr/local/tomcat/ROOT-exec.war \
			--server.port=0 --management.server.port=0 \
			--aiforgot.portableDump.command=import \
			--aiforgot.portableDump.mode="$(PORTABLE_IMPORT_MODE)" \
			--aiforgot.portableDump.file="$(PORTABLE_DUMP_CONTAINER_PATH)"

validate-portable:
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@mkdir -p "$(PORTABLE_DUMP_HOST_DIR_ABS)"
	@docker run --rm --network "$(DOCKER_NETWORK)" --env-file .env \
		-v "$(PORTABLE_DUMP_HOST_DIR_ABS):$(PORTABLE_DUMP_CONTAINER_DIR)" \
		"$(APP_IMAGE)" \
		java -jar /usr/local/tomcat/ROOT-exec.war \
			--server.port=0 --management.server.port=0 \
			--aiforgot.portableDump.command=validate \
			--aiforgot.portableDump.file="$(PORTABLE_DUMP_CONTAINER_PATH)"

# Wrapper targets must run sequentially (some environments set MAKEFLAGS=-j)
migrate-postgres-to-sqlite:
	@$(MAKE) portable-export-postgres
	@$(MAKE) portable-import-sqlite

migrate-sqlite-to-postgres:
	@$(MAKE) portable-export-sqlite
	@$(MAKE) portable-import-postgres

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
	@echo "  build                         - Build both application and web Docker images."
	@echo "  build-app-image               - Build the application Docker image."
	@echo "  build-app-image-nocache       - Build the application Docker image (no cache)."
	@echo "  build-core-nocache            - Build the application Docker image (no cache) (core stack)."
	@echo "  build-deploy                  - Build images and deploy the application container."
	@echo "  build-deploy-core             - Build the app image and deploy the core stack (app + db only)."
	@echo "  build-deploy-core-nocache     - Build the app image (no cache) and deploy the core stack (app + db only)."
	@echo "  build-deploy-nocache          - Build images (no cache) and deploy the application container."
	@echo "  build-deploy-sqlite           - Build images and deploy using SQLite single-file mode (mounts ./db)."
	@echo "  build-deploy-sqlite-nocache   - Build images (no cache) and deploy using SQLite single-file mode (mounts ./db)."
	@echo "  build-llamacpp-cpu            - Build the llama.cpp CPU server."
	@echo "  build-llamacpp-cuda           - Build the llama.cpp CUDA server."
	@echo "  build-nocache                 - Build both images (no cache)."
	@echo "  build-web-image               - Build the web Docker image."
	@echo "  build-web-image-nocache       - Build the web Docker image (no cache)."
	@echo "  clean                         - Clean target directories."
	@echo "  delete-redeploy               - Delete containers and volumes, then rebuild and redeploy."
	@echo "  down                          - Stop and remove the application, database, and web containers."
	@echo "  down-core                     - Stop and remove only the application + database containers."
	@echo "  down-with-volumes             - Stop and remove containers and associated volumes."
	@echo "  drop-and-recreate-db          - Drop and recreate the PostgreSQL database."
	@echo "  export-db                     - Export the PostgreSQL database to db/backup.sql."
	@echo "  export-db-container           - Export the PostgreSQL database from the DB container to db/backup.sql."
	@echo "  export-delete-redeploy        - Export DB, delete containers/volumes, redeploy, and import DB."
	@echo "  import-db                     - Import the PostgreSQL database from db/backup.sql."
	@echo "  import-db-container           - Import the PostgreSQL database from db/backup.sql into the DB container."
	@echo "  list-backups                  - List archived backups under db/ (backup.sql, backup1.sql, ...)."
	@echo "  migrate-postgres-to-sqlite    - Export Postgres -> import into SQLite."
	@echo "  migrate-sqlite-to-postgres    - Export SQLite -> import into Postgres."
	@echo "  nexus-down                    - Stop and remove Nexus containers."
	@echo "  nexus-logs                    - Tail Nexus logs."
	@echo "  nexus-status                  - Show status of Nexus containers."
	@echo "  nexus-up                      - Start Sonatype Nexus (data + server) for Maven caching on port 8081."
	@echo "  portable-export-postgres      - Export Postgres DB to ./db/portable-dump.zip."
	@echo "  portable-export-sqlite        - Export SQLite DB (./db/cards.db) to ./db/portable-dump.zip."
	@echo "  portable-import-postgres      - Import ./db/portable-dump.zip into Postgres (PORTABLE_IMPORT_MODE=truncate|fail-if-not-empty)."
	@echo "  portable-import-sqlite        - Import ./db/portable-dump.zip into SQLite (./db/cards.db) (PORTABLE_IMPORT_MODE=truncate|fail-if-not-empty)."
	@echo "  redeploy-watch                - Redeploy and watch application logs."
	@echo "  restart                       - Restart the application, database, and web containers."
	@echo "  run-standalone-postgres       - Run the executable WAR locally with an external Postgres (no containers)."
	@echo "  run-standalone-sqlite         - Run the executable WAR locally with SQLite single-file DB (no containers)."
	@echo "  start-llamacpp                - Start the llama.cpp server with specified model and port."
	@echo "  stop                          - Stop the application, database, and web containers."
	@echo "  stop-core                     - Stop only the application + database containers."
	@echo "  tail-core-logs                - Tail the logs of the application container (core stack)."
	@echo "  tail-tomcat-logs              - Tail the logs of the application container."
	@echo "  test                          - Run unit tests."
	@echo "  up                            - Start the application, database, and web containers."
	@echo "  up-core                       - Start only the application + database containers (no Nginx)."
	@echo "  up-core-sqlite                - Start only the app with SQLite single-file DB (mounts ./db)."
	@echo "  up-sqlite                     - Start app+web with SQLite single-file DB (mounts ./db into app container)."
	@echo "  validate-portable             - Validate ./db/portable-dump.zip structure."

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
	@if [ "$(DB_VENDOR)" != "sqlite" ]; then \
		docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null; \
	fi

	@# db (postgres): create or start (skipped when DB_VENDOR=sqlite)
	@if [ "$(DB_VENDOR)" != "sqlite" ]; then \
		if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
			docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
				--env-file .env -v "$(DB_VOLUME):/var/lib/postgresql/data" \
				postgres:17 postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
		else \
			if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
		fi; \
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