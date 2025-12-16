# Using the prebuilt container images (GHCR)

This project publishes prebuilt images to GitHub Container Registry (GHCR):

- App image: https://github.com/users/darkmusic/packages/container/package/ai-forgot-these-cards-app
- Web image (Nginx SPA + `/api` reverse proxy): https://github.com/users/darkmusic/packages/container/package/ai-forgot-these-cards-web

Pull examples:

```bash
docker pull ghcr.io/darkmusic/ai-forgot-these-cards-app:latest
docker pull ghcr.io/darkmusic/ai-forgot-these-cards-web:latest
```

> If you want to run without containers, see [Releases.md](Releases.md).

## Choose a topology

You have two common topologies:

- **Core stack**: app + database (no Nginx). App serves the UI and `/api`.
- **Full stack**: web (Nginx) + app + database. Web serves the SPA and proxies `/api/*` to the app.

The examples below use Docker CLI directly. If you prefer Docker Compose, you can translate the same settings into a compose file.

## Recommended: run the full stack (web + app + Postgres)

1) Create a network:

```bash
docker network create cards-net
```

2) Start Postgres:

```bash
docker volume create pgdata

docker run -d \
  --name db \
  --network cards-net \
  -e POSTGRES_DB=cards \
  -e POSTGRES_USER=cards \
  -e POSTGRES_PASSWORD=cards \
  -v pgdata:/var/lib/postgresql/data \
  -p 5433:5432 \
  postgres:16
```

3) Start the app container.

Important: name this container `app` so the web container can reach it as `http://app:8080`.

```bash
docker run -d \
  --name app \
  --network cards-net \
  -e DB_VENDOR=postgres \
  -e DB_URL=jdbc:postgresql://db:5432/cards \
  -e POSTGRES_USER=cards \
  -e POSTGRES_PASSWORD=cards \
  -p 8080:8080 \
  ghcr.io/darkmusic/ai-forgot-these-cards-app:latest
```

4) Start the web container (Nginx).

```bash
docker run -d \
  --name web \
  --network cards-net \
  -p 8086:80 \
  ghcr.io/darkmusic/ai-forgot-these-cards-web:latest
```

5) Open the app:

- Full stack URL: http://localhost:8086

## Core stack (app + Postgres only)

If you don’t need Nginx, you can skip the web container and use the app directly.

- URL: http://localhost:8080

Use the same `db` and `app` `docker run` commands above, but omit the `web` container.

## SQLite mode with the app image (no Postgres)

SQLite mode uses a persisted `.db` file; you must mount a writable directory.

```bash
mkdir -p ./db

docker run -d \
  --name app \
  -e DB_VENDOR=sqlite \
  -e SQLITE_DB_PATH=/data/cards.db \
  -v "$PWD/db:/data" \
  -p 8080:8080 \
  ghcr.io/darkmusic/ai-forgot-these-cards-app:latest
```

Then open: http://localhost:8080

## Configuration

The containers are configured via environment variables.

Common ones:

- `APP_SERVER_PORT` (default: 8080)
- `DB_VENDOR` (`postgres` or `sqlite`)
- `DB_URL` (Postgres JDBC URL)
- `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `SQLITE_DB_PATH` (SQLite file path)
- `SPRING_AI_OPENAI_API_KEY` (hosted AI)
- `SPRING_AI_OPENAI_CHAT_BASE_URL` (local/remote OpenAI-compatible server)

Tip: if you already have a `.env` file, Docker can read it:

```bash
docker run --env-file .env ...
```

## Default login

- Username: `cards`
- Password: `cards`

Then create a normal `USER` account via the Admin UI.

## Versioning and tags

For repeatable deployments:

- Prefer pinning a specific tag instead of `latest`.
- Keep `ai-forgot-these-cards-app` and `ai-forgot-these-cards-web` on the same version.

Example:

```bash
docker pull ghcr.io/darkmusic/ai-forgot-these-cards-app:v0.1.2
docker pull ghcr.io/darkmusic/ai-forgot-these-cards-web:v0.1.2
```

If your registry tags use a different scheme (e.g. `0.1.2`), use the tags shown in GHCR.
