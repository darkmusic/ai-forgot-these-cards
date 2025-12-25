# Deployment

This project supports multiple local deployment topologies, driven by Make targets.

If you want to deploy using **prebuilt GHCR images** (instead of building locally), see [Container-images.md](Container-images.md).

## Core stack vs full stack

### Core stack (app + DB)

- Runs: Postgres container + app container
- UI and API are served by the app container
- Default URL: <http://localhost:8080>

Commands:

```bash
make build-deploy-core
# or, if already built:
make up-core
```

### Full stack (Nginx + app + DB)

- Runs: Nginx container + app container + Postgres container
- Nginx serves the SPA and proxies `/api/*` to the app
- Default URL: <http://localhost:8086>

Commands:

```bash
make build-deploy
# or, if already built:
make up
```

## SQLite deployments

SQLite mode replaces Postgres with a local single-file DB.

- Full sqlite stack (app + Nginx): `make build-deploy-sqlite`
- Core sqlite stack (app only): `make up-core-sqlite`

SQLite targets mount the repo’s `./db` directory into the app container.

## Useful operational commands

- Tail app logs: `make tail-tomcat-logs`
- Stop and remove containers: `make down`
- Delete containers + volumes + network (destructive): `make down-with-volumes`
- Rebuild with no cache: `make build-deploy-nocache`

## Optional: Nexus for Maven caching

If you rebuild often, Maven dependency downloads can dominate build time. You can run a local Nexus proxy and enable it for Docker builds.

Start Nexus:

```bash
make nexus-up
```

Enable the mirror in `.env`:

```bash
USE_NEXUS_MAVEN=1
# NEXUS_MAVEN_MIRROR_URL defaults to http://host.docker.internal:8081/repository/maven-public
```
