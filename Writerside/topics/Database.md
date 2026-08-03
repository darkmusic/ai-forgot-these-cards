# Database

Ai Forgot These Cards supports two database modes:

- **PostgreSQL** (default; typically run via the provided Docker targets)
- **SQLite single-file mode** (optional)

## PostgreSQL (default)

In the container stacks, Postgres runs in a container named `db`, and a named Docker volume persists data.

## Versioned schema migrations

Schema changes are managed by Flyway, and pending migrations are applied automatically when the app starts.

If Flyway sees an existing non-empty database with no migration history table, it baselines that database at V1. This lets databases originally created by Hibernate start cleanly after upgrading to the Flyway-enabled app. Empty databases still run `V1__initial_schema.sql` normally.

Useful explicit commands:

```bash
make db-migrate-postgres
make db-validate-postgres
make db-info-postgres

make db-migrate-sqlite
make db-validate-sqlite
make db-info-sqlite
```

You can also baseline explicitly if you are running one-off migration commands against an existing database:

```bash
make db-baseline-postgres
# or
make db-baseline-sqlite
```

New schema changes should be added as paired migrations under `db/migration/postgresql` and `db/migration/sqlite`.

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
