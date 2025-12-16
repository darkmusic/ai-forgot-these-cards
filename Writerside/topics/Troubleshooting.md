# Troubleshooting

## Port conflicts

- If the backend can’t start, check whether `APP_SERVER_PORT` is already in use.
- If llama.cpp runs on 8080, it will conflict with the backend; prefer 8087.

## Can’t reach llama.cpp from the container

If llama.cpp is running on the host, the app container must be able to reach it.

- Prefer `SPRING_AI_OPENAI_CHAT_BASE_URL=http://host.docker.internal:<port>`
- Ensure your Docker setup supports the host gateway mapping

## Nexus builds fail on first run

Nexus can take 1–2 minutes to become ready. If builds fail to reach the mirror, wait until Nexus is up and retry.

## Database export/import issues

- `make export-db`/`make import-db` require local Postgres client tools.
- If you don’t have them, use `make export-db-container` and `make import-db-container`.

## First place to look

- App logs: `make tail-tomcat-logs`
