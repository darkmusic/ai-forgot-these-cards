# Database

Ai Forgot These Cards supports two database modes:

- **PostgreSQL** (default; typically run via the provided Docker targets)
- **SQLite single-file mode** (optional)

## PostgreSQL (default)

In the container stacks, Postgres runs in a container named `db`, and a named Docker volume persists data.

### Export (Postgres-only)

Exports to `db/backup.sql` (archives existing backups as `backup1.sql`, `backup2.sql`, ...).

```bash
make export-db
```

If you don’t have Postgres client tools installed locally, export via the container:

```bash
make export-db-container
```

### Import (Postgres-only)

> **Warning:** Import drops and recreates the database.

```bash
make import-db
```

Or via the container:

```bash
make import-db-container
```

## Portable migrations (Postgres <-> SQLite)

The project supports a vendor-neutral “portable dump” format (ZIP + JSONL) to move between Postgres and SQLite.

Common workflows:

```bash
# Postgres -> SQLite (overwrites db/cards.db by default)
make migrate-postgres-to-sqlite

# SQLite -> Postgres
make migrate-sqlite-to-postgres
```

Advanced usage:

```bash
# Export only
make portable-export-postgres
make portable-export-sqlite

# Validate structure of db/portable-dump.zip
make validate-portable

# Import only
make portable-import-postgres
make portable-import-sqlite

# Safer import mode (fails if target DB is not empty)
PORTABLE_IMPORT_MODE=fail-if-not-empty make portable-import-postgres
PORTABLE_IMPORT_MODE=fail-if-not-empty make portable-import-sqlite
```

## SQLite single-file mode

SQLite mode uses a local `.db` file (default `./db/cards.db`).

- Containerized: `make up-core-sqlite` or `make build-deploy-sqlite`
- Containerless: `make run-standalone-sqlite`
