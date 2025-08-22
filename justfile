set shell := ["pwsh", "-c"]

########################################################################
# Maven Lifecycle Commands
########################################################################

# Does a Maven clean
clean:
    @./mvnw clean

# Does a Maven Compile
compile:
    @./mvnw clean compile

# Does a Maven install
install:
    @./mvnw clean install

# Runs the application
run:
    @./mvnw clean spring-boot:run

#######################################################################
# Database Commands
#######################################################################

# Starts the database
start-db:
    @docker compose up -d

# Stops the database
stop-db:
    @docker compose stop

# Restarts the database
restart-db:
    @docker compose restart

# Drops and re-creates the database
drop-and-recreate-db:
    @docker compose down -v postgres
    @docker compose up -d postgres

# Exports the database via pg_dump
# This uses DB parameters in src/main/resources/application.properties
# Note: manual password entry is still required, as this is not part of the command line options
export-db:
    @& docker compose exec -t postgres rm -f /tmp/backup.sql
    @& docker compose exec -t postgres pg_dump -h $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split ":")[0] -U $((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.user*" } | Select-Object -Index 0) -Split "=")[1] -W -F c -b -v -f "/tmp/backup.sql" $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split "/")[1]
    @& Remove-Item db/backup.sql
    @New-Item -ItemType Directory -Path db -Force
    @& docker compose cp postgres:/tmp/backup.sql db/backup.sql

# Imports the database via pg_restore
# This uses DB parameters in src/main/resources/application.properties
# Note: manual password entry is still required, as this is not part of the command line options
import-db: drop-and-recreate-db
    @& docker compose exec -t postgres rm -f /tmp/backup.sql
    @& docker compose cp db/backup.sql postgres:/tmp/backup.sql
    @& docker compose exec -t postgres pg_restore -h $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split ":")[0] -U $((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.user*" } | Select-Object -Index 0) -Split "=")[1] -W -F c -v -d $((((Get-Content -Path ./src/main/resources/application.properties | Where-Object { $_ -CLike "spring.datasource.url*" } | Select-Object -Index 0) -Split "//")[1]) -Split "/")[1] "/tmp/backup.sql"
