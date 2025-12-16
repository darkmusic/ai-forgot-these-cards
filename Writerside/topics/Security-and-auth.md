# Security and authentication

The backend uses Spring Security.

## Roles

- Regular users typically have `ROLE_USER`
- Admin features require `ROLE_ADMIN`

## Login/logout behavior

- Login: `POST /api/login`
  - Success returns HTTP 200 (no redirect)
  - Failure returns HTTP 401
- Logout: `POST /api/logout` returns HTTP 204

## CSRF

The SPA uses a CSRF token fetched from `GET /api/csrf` and sends it as a header for unsafe methods.

If you’re building custom clients or scripts, you must follow the same flow:

1) Fetch `/api/csrf`
2) Read the CSRF header name/value
3) Include it on `POST/PUT/DELETE` requests

## Public vs protected endpoints

- `/api/**` requires authentication by default
- Some endpoints are public (for example: `/api/csrf`, actuator, OpenAPI docs)

For the authoritative configuration, see the Spring Security filter chain in the backend.
