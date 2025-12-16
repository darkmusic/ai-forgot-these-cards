# Standalone (no Docker)

This project produces both a standard WAR and a runnable executable WAR.

- `target/ai-forgot-these-cards-<version>.war` (deploy to Tomcat)
- `target/ai-forgot-these-cards-<version>-exec.war` (run with `java -jar`)

## SQLite single-file mode (recommended)

```bash
make run-standalone-sqlite
```

This runs an executable WAR locally and persists a SQLite DB file (default `./db/cards.db`).

## External Postgres (advanced)

```bash
make run-standalone-postgres
```

You may need to override `DB_URL` if your Postgres runs on a different host/port.
