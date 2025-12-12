# Nim live smoke tests (testament)

These are **manual, container-dependent** live tests. They hit the running backend over HTTP and verify:

- Session cookies (`JSESSIONID`) + CSRF (`/api/csrf`)
- Login via `POST /api/login`
- Admin vs non-admin authorization rules on `/api/user/**`
- `password_hash` is not leaked in JSON responses

## Prerequisites

- Containers running for the target you want to test
  - **Core backend only**: app container reachable on `http://localhost:8080`
  - **Full stack via Nginx**: web container reachable on `http://localhost:8086`
- An admin account (defaults to `cards/cards`)
- Nim + testament available (this repo uses them under `src/test/nim`)

## Running

From the `src/test/nim` directory:

- Run core smoke test (direct-to-app):
  - `testament run ./tests/smoke/core.nim`

- Run nginx smoke test (through reverse proxy):
  - `testament run ./tests/smoke/nginx.nim`

If you prefer, you can also run them from any directory by `cd`-ing first:

- `cd src/test/nim && testament run ./tests/smoke/core.nim`

## Configuration (environment variables)

Both smoke tests accept the same env vars:

- `ADMIN_USER` (default: `cards`)
- `ADMIN_PASS` (default: `cards`)
- `NONADMIN_PASS` (default: `testpass`)
- `NONADMIN_PASS_HASH_BCRYPTJS_2B` (default: bcryptjs hash for `testpass`)

Base URL selection:

- Core test uses `BASE_URL_CORE` (fallback: `BASE_URL`, then `http://localhost:8080`)
- Nginx test uses `BASE_URL_NGINX` (fallback: `BASE_URL`, then `http://localhost:8086`)

Examples:

- Hit a different core URL:
  - `BASE_URL_CORE=http://localhost:18080 testament run ./tests/smoke/core.nim`

- Hit nginx with shared `BASE_URL`:
  - `BASE_URL=http://localhost:8086 testament run ./tests/smoke/nginx.nim`

## Notes

- These tests create a temporary non-admin user and delete it at the end (cleanup is attempted even if later steps fail).
- If you run the Nginx test without the `web` container running, it will fail fast with a clear "connection refused" message.
