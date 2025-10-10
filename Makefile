SHELL := bash

export JAVA_HOME := /opt/graalvm-jdk-21.0.8+12.1
DOCKER_NETWORK := cards-net
DB_CONTAINER := db
APP_CONTAINER := app
WEB_CONTAINER := web
APP_IMAGE := aiforgot/app:latest
DB_VOLUME := pgdata

.PHONY: clean compile install run \
	drop-and-recreate-db export-db import-db \
	build up down restart build-deploy delete-redeploy down-with-volumes tail-tomcat-logs \
	redeploy-watch

########################################################################
# Maven Lifecycle Commands
########################################################################

clean:
	@./mvnw clean

compile: clean
	@./mvnw compile

install: clean
	@./mvnw "-Dmaven.test.skip=true" install

run: compile
	@./mvnw spring-boot:run

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
		-e "POSTGRES_DB=cards" -e "POSTGRES_PASSWORD=cards" -e "POSTGRES_USER=cards" -e "POSTGRES_HOST=0.0.0.0" \
		-v "$(DB_VOLUME):/var/lib/postgresql/data" \
		postgres:latest postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'

export-db:
	@mkdir -p db
	@docker exec -t "$(DB_CONTAINER)" sh -lc 'rm -f /tmp/backup.sql'
	@DB_USER="$$(grep -E "^spring\.datasource\.username=" ./src/main/resources/application.properties | head -n1 | cut -d= -f2 | xargs)"; \
	DB_NAME="$$(grep -E "^spring\.datasource\.url=" ./src/main/resources/application.properties | head -n1 | sed -E "s#.*//[^/]+/([^?]+).*#\1#")"; \
	docker exec -it "$(DB_CONTAINER)" sh -lc "pg_dump -h localhost -U \"$$DB_USER\" -W -F c -b -v -f /tmp/backup.sql \"$$DB_NAME\""
	@rm -f db/backup.sql
	@docker cp "$(DB_CONTAINER):/tmp/backup.sql" "db/backup.sql"

import-db: drop-and-recreate-db
	@docker cp "db/backup.sql" "$(DB_CONTAINER):/tmp/backup.sql"
	@DB_USER="$$(grep -E "^spring\.datasource\.username=" ./src/main/resources/application.properties | head -n1 | cut -d= -f2 | xargs)"; \
	DB_NAME="$$(grep -E "^spring\.datasource\.url=" ./src/main/resources/application.properties | head -n1 | sed -E "s#.*//[^/]+/([^?]+).*#\1#")"; \
	docker exec -it "$(DB_CONTAINER)" sh -lc "pg_restore -h localhost -U \"$$DB_USER\" -W -F c -v -d \"$$DB_NAME\" /tmp/backup.sql"

#######################################################################
# Docker Commands (no docker compose)
#######################################################################

build:
	@docker build -t "$(APP_IMAGE)" -f dockerfiles/app/Dockerfile .

up:
	@# ensure network and volume
	@docker network inspect "$(DOCKER_NETWORK)" >/dev/null 2>&1 || docker network create "$(DOCKER_NETWORK)"
	@docker volume inspect "$(DB_VOLUME)" >/dev/null 2>&1 || docker volume create "$(DB_VOLUME)" >/dev/null
	@# db: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then \
		docker run -d --name "$(DB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "5433:5432" \
			-e "POSTGRES_DB=cards" -e "POSTGRES_PASSWORD=cards" -e "POSTGRES_USER=cards" -e "POSTGRES_HOST=0.0.0.0" \
			-v "$(DB_VOLUME):/var/lib/postgresql/data" \
			postgres:latest postgres -c max_locks_per_transaction=1024 -c shared_buffers=1GB -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all -c max_connections=200 -c listen_addresses='*'; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker start "$(DB_CONTAINER)"; fi; \
	fi
	@# app: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then \
		docker run -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "8080:8080" -p "9090:9090" \
			-e "DB_HOST=$(DB_CONTAINER)" -e "DB_PORT=5432" -e "DB_NAME=cards" -e "DB_USER=cards" -e "DB_PASSWORD=cards" \
			"$(APP_IMAGE)"; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker start "$(APP_CONTAINER)"; fi; \
	fi
	@# web: create or start
	@if ! docker ps -a --format '{{.Names}}' | grep -qx "$(WEB_CONTAINER)"; then \
		docker run -d --name "$(WEB_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "8086:80" \
			-v "./dockerfiles/web/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
			-v "./web:/usr/share/nginx/html:ro" \
			nginx:latest; \
	else \
		if ! docker ps --format '{{.Names}}' | grep -qx "$(WEB_CONTAINER)"; then docker start "$(WEB_CONTAINER)"; fi; \
	fi

down:
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(WEB_CONTAINER)"; then docker rm -f "$(WEB_CONTAINER)"; fi
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker rm -f "$(APP_CONTAINER)"; fi
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(DB_CONTAINER)"; then docker rm -f "$(DB_CONTAINER)"; fi

restart: down up

build-deploy: install build up
	@if docker ps -a --format '{{.Names}}' | grep -qx "$(APP_CONTAINER)"; then docker rm -f "$(APP_CONTAINER)"; fi
	@docker run -d --name "$(APP_CONTAINER)" --network "$(DOCKER_NETWORK)" -p "8080:8080" -p "9090:9090" \
		-e "DB_HOST=$(DB_CONTAINER)" -e "DB_PORT=5432" -e "DB_NAME=cards" -e "DB_USER=cards" -e "DB_PASSWORD=cards" \
		"$(APP_IMAGE)"

delete-redeploy: install down-with-volumes build up

down-with-volumes:
	@$(MAKE) down
	@docker volume rm -f "$(DB_VOLUME)" >/dev/null 2>&1 || true
	@docker network rm "$(DOCKER_NETWORK)" >/dev/null 2>&1 || true

tail-tomcat-logs:
	@docker logs -f "$(APP_CONTAINER)"

#######################################################################
# Debugging Commands
#######################################################################

redeploy-watch: install down-with-volumes build up tail-tomcat-logs
