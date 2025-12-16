# Installation guide

This guide explains how to install and run **Ai Forgot These Cards** locally.

The default workflow is **containerized**: building the backend WAR and the frontend SPA happens inside Docker images, so you typically do **not** need a local JDK, Maven, or Node.js.

## Installation types

| Type | Best for | Command | Default URL |
| --- | --- | --- | --- |
| **Core stack** (app + DB) | Most users / simplest | `make build-deploy-core` | http://localhost:8080 |
| **Full stack** (Nginx + app + DB) | Reverse-proxy parity | `make build-deploy` | http://localhost:8086 |
| **SQLite mode** (no Postgres) | Simplest runtime | `make build-deploy-sqlite` *(full)* or `make up-core-sqlite` *(core)* | http://localhost:8086 or :8080 |
| **Standalone (no containers)** | Advanced | `make run-standalone-sqlite` | http://localhost:8080 |

> **Note:** Your `.env` can change ports and database vendor; see [Configuration.md](Configuration.md).

## System requirements

Minimum requirements:

- Git
- GNU Make
- A container runtime (Docker or compatible)

Optional requirements:

- PostgreSQL client tools (`pg_dump`, `pg_restore`, `psql`) if you use the **local** Postgres backup targets (see [Database.md](Database.md))

## Before you begin

1) Clone the repository:

```bash
git clone https://github.com/darkmusic/ai-forgot-these-cards
cd ai-forgot-these-cards
```

2) Initialize submodules (frontend and llama.cpp are included as submodules):

```bash
git submodule update --init
```

3) Create a `.env` file:

```bash
cp .env.example .env
```

At minimum, ensure your database credentials and `DB_URL` are correct for your environment.

## Install and run (recommended: core stack)

This runs the app + Postgres DB. The app serves both the UI and `/api`.

1) Build and deploy:

```bash
make build-deploy-core
```

2) Open the app:

- UI + API: http://localhost:8080
- API base: http://localhost:8080/api

3) Sign in with the default admin:

- Username: `cards`
- Password: `cards`

4) (Recommended) Create a normal user:

- Go to **Admin** and add a new user with role `USER`.

## Verify installation

Use one (or more) of the following checks:

- Open the UI page in your browser
- Open Swagger UI:
  - Core stack: http://localhost:8080/swagger-ui/index.html
  - Full stack: http://localhost:8086/api/swagger-ui/index.html
- Open Actuator (core stack example): http://localhost:8080/actuator

## Optional: enable AI features

- Hosted provider (easy mode): set `SPRING_AI_OPENAI_API_KEY` in `.env`
- Local mode: run llama.cpp in OpenAI-compatible server mode and set `SPRING_AI_OPENAI_CHAT_BASE_URL`

Full details: [AI-Integration.md](AI-Integration.md)

## Uninstall / clean reset

To remove the containers:

```bash
make down
```

To also remove the Postgres volume and Docker network (destructive):

```bash
make down-with-volumes
```

## Troubleshooting

If things don’t work after a successful `make build-deploy-*`, start here:

- [Troubleshooting.md](Troubleshooting.md)
- `make tail-tomcat-logs`
