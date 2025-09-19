set shell := ["pwsh", "-c"]

export JAVA_HOME := "/usr/lib/jvm/java-21-graalvm"

########################################################################
# Maven Lifecycle Commands
########################################################################

# Does a Maven clean
clean:
    @& ./mvnw clean

# Does a Maven Compile
compile: clean
    @& ./mvnw compile

# Does a Maven install
install: clean
    @& ./mvnw "-Dmaven.test.skip=true" install

# Runs the application
run: compile
    @& ./mvnw spring-boot:run

#######################################################################
# Database Commands
#######################################################################

# Drops and re-creates the database
drop-and-recreate-db:
    @& docker compose down -v db
    @& docker compose up -d db

# Exports the database via pg_dump
# This uses DB parameters in src/main/resources/application.properties
# Note: manual password entry is still required, as this is not part of the command line options
export-db:
    @& docker compose exec -t db rm -f /tmp/backup.sql
    @& docker compose exec -t db pg_dump -h $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split ":")[0] -U $((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.username*" } | Select-Object -Index 0) -Split "=")[1] -W -F c -b -v -f "/tmp/backup.sql" $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split "/")[1]
    @& Remove-Item db/backup.sql
    @New-Item -ItemType Directory -Path db -Force
    @& docker compose cp db:/tmp/backup.sql db/backup.sql

# Imports the database via pg_restore
# This uses DB parameters in src/main/resources/application.properties
# Note: manual password entry is still required, as this is not part of the command line options
import-db: drop-and-recreate-db
    @& docker compose exec -t db rm -f /tmp/backup.sql
    @& docker compose cp db/backup.sql db:/tmp/backup.sql
    @& docker compose exec -t db pg_restore -h $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split ":")[0] -U $((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.username*" } | Select-Object -Index 0) -Split "=")[1] -W -F c -v -d $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split "/")[1] "/tmp/backup.sql"


#######################################################################
# Docker-Compose Commands
#######################################################################

# Brings up the entire docker-compose stack
up:
    @& docker compose up -d

# Builds the entire docker-compose stack without using cache
build:
    @& docker compose build --no-cache

# Brings down the entire docker-compose stack
down:
    @& docker compose down

# Restarts the entire docker-compose stack
restart: down up

# Builds and deploys the entire docker-compose stack without using cache
build-deploy: install build up

# Builds and redeploys the entire docker-compose stack without using cache, dropping and recreating the database
# Note: This will delete all data in the database!
delete-redeploy: install down-with-volumes build up

# Brings down the entire docker-compose stack, including volumes
down-with-volumes:
    @& docker compose down -v

# Tails the Tomcat logs from the app container
tail-tomcat-logs:
    @& docker compose logs -f app

#######################################################################

#######################################################################
# Debugging Commands
#######################################################################

# Redeploys the application by doing a Maven install, bringing down the stack with volumes, rebuilding the images, bringing the stack back up, and tailing the Tomcat logs
redeploy-watch: install down-with-volumes build up tail-tomcat-logs

#######################################################################
