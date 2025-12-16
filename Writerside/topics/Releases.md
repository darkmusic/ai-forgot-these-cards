# Installing from GitHub Releases

This project publishes **release artifacts** on GitHub Releases.

- Releases page: https://github.com/darkmusic/ai-forgot-these-cards/releases
- Example release: https://github.com/darkmusic/ai-forgot-these-cards/releases/tag/v0.1.2

A typical release includes:

- `ai-forgot-these-cards-<version>-exec.war` (runnable WAR; run with `java -jar`)
- `ai-forgot-these-cards-<version>.war` (standard WAR; deploy to an external Tomcat)
- `checksums.sha256` (SHA-256 checksums for artifacts)

> If you want to run containers instead of WAR files, see [Container-images.md](Container-images.md).

## Verify downloads

Download the artifacts you want (for example via the GitHub UI).

Then verify checksums.

Linux:

```bash
sha256sum -c checksums.sha256
```

macOS:

```bash
shasum -a 256 -c checksums.sha256
```

If `checksums.sha256` contains multiple entries, the checker will validate all files that are present in the current directory.

## Option A: Run the runnable WAR (`*-exec.war`)

### Requirements

- Java 17+ (required by Spring Boot 3)
- A database:
  - PostgreSQL (default), or
  - SQLite single-file mode (optional)

### SQLite single-file mode (simplest standalone)

This runs without Postgres.

```bash
DB_VENDOR=sqlite \
SQLITE_DB_PATH=./cards.db \
java -jar ai-forgot-these-cards-<version>-exec.war
```

Notes:

- `SQLITE_DB_PATH` is optional; default is `./db/cards.db`.
- Ensure the directory is writable; the app will create the DB file if it doesn’t exist.

### Postgres mode

Run with an existing Postgres instance.

```bash
DB_VENDOR=postgres \
DB_URL=jdbc:postgresql://localhost:5432/cards \
POSTGRES_USER=cards \
POSTGRES_PASSWORD=cards \
java -jar ai-forgot-these-cards-<version>-exec.war
```

Notes:

- The schema is managed by Hibernate (`ddl-auto=update`).
- If you want the app to listen on a different port, set `APP_SERVER_PORT`.

### Optional: AI configuration

Hosted provider (easy mode):

```bash
SPRING_AI_OPENAI_API_KEY=... java -jar ai-forgot-these-cards-<version>-exec.war
```

Local/remote OpenAI-compatible server (e.g. llama.cpp):

```bash
SPRING_AI_OPENAI_CHAT_BASE_URL=http://localhost:8087 java -jar ai-forgot-these-cards-<version>-exec.war
```

## Option B: Deploy the standard WAR (`*.war`) to Tomcat

### Requirements

- An external Tomcat 10+ (Jakarta / Servlet 6)
- Java 17+
- A database (Postgres or SQLite)

### Deploy

1) Copy the WAR into Tomcat’s `webapps/` directory.

2) (Optional) If you want the app at `/` instead of `/<war-name>/`, rename it to `ROOT.war`.

3) Configure environment variables for Tomcat.

On Linux/macOS, create or edit `TOMCAT/bin/setenv.sh`:

```bash
export DB_VENDOR=sqlite
export SQLITE_DB_PATH=/var/lib/aiforgot/cards.db

# Optional AI:
# export SPRING_AI_OPENAI_API_KEY=...
# export SPRING_AI_OPENAI_CHAT_BASE_URL=http://host.docker.internal:8087
```

Then start Tomcat.

### Verify

- UI: `http://localhost:8080/` (or Tomcat’s configured port)
- Swagger UI: `/swagger-ui/index.html`
- Actuator: `/actuator`

Default admin credentials:

- Username: `cards`
- Password: `cards`

## Versioning tips

- Prefer using a matching version across components (WAR, images, etc.).
- Pin exact versions (e.g. `v0.1.2`) for repeatable deployments.
