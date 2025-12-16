# Configuration

Configuration is primarily done through a `.env` file in the repo root.

```bash
cp .env.example .env
```

The Makefile loads `.env` automatically when present.

## Common settings

| Variable            | Purpose                                      | Typical values                                     |
|---------------------|----------------------------------------------|----------------------------------------------------|
| `APP_SERVER_PORT`   | Host port mapped to the app container’s 8080 | `8080`                                             |
| `WEB_HOST_PORT`     | Host port for Nginx (full stack)             | `8086`                                             |
| `DB_VENDOR`         | Database vendor selection                    | `postgres` *(default)* or `sqlite`                 |
| `DB_URL`            | JDBC URL for Postgres                        | e.g. `jdbc:postgresql://db:5432/cards` (container) |
| `POSTGRES_USER`     | Postgres username                            | `cards`                                            |
| `POSTGRES_PASSWORD` | Postgres password                            | (set in `.env`)                                    |
| `POSTGRES_DB`       | Postgres database name                       | `cards`                                            |

## SQLite single-file mode

SQLite mode avoids Postgres entirely and stores data in a single `.db` file.

Key variables:

- `DB_VENDOR=sqlite`
- `SQLITE_DB_PATH` (default is `./db/cards.db` when running standalone; container targets mount `./db` into the app container)

Related commands:

- Containerized: `make up-core-sqlite` or `make build-deploy-sqlite`
- Containerless: `make run-standalone-sqlite`

## AI settings (optional)

| Variable                         | Purpose                                                    |
|----------------------------------|------------------------------------------------------------|
| `SPRING_AI_OPENAI_API_KEY`       | API key for hosted “easy mode” providers                   |
| `SPRING_AI_OPENAI_CHAT_BASE_URL` | Base URL for an OpenAI-compatible server (e.g. llama.cpp). |

If you run llama.cpp on the host, the container targets use a host gateway mapping so `http://host.docker.internal:<port>` can work on Linux.

See: [AI-Integration.md](AI-Integration.md)

## Build-time caches (optional)

- `USE_NEXUS_MAVEN=1` enables Maven dependency caching via a local Nexus.
- `NEXUS_MAVEN_MIRROR_URL` points Maven at your Nexus group URL.

There are also optional Nexus APT proxy variables used only during Docker builds.

See: [Deployment.md](Deployment.md)
