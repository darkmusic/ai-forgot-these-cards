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

## Bulk AI tools errors

Errors from the bulk editor AI tools appear inside the AI tools frame and never apply partial changes. Common cases:

- **Request failed / endpoint errors** — check that the AI endpoint is running and reachable, credentials are valid, the model name is correct, and the model's context limit fits the draft. Try fewer cards.
- **Timeouts** — the client aborts after 12 minutes and the backend stream times out after 15 minutes. Reduce the card count or increase the configured AI timeout.
- **Malformed or incomplete AI responses** — retry; smaller batches produce more reliable output. Incomplete responses (finish reason other than `stop`) are rejected.
- **"The draft changed during the request"** — run the operation again with the current draft.
- **"Duplicate suggestions are no longer valid"** — the draft changed since the analysis; run Find Duplicates again.
- **Undo conflicts** — later manual edits always win; conflicting fields are kept and reported.

## First place to look

- App logs: `make tail-tomcat-logs`
