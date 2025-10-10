# Copilot Instructions for ai-forgot-these-cards

This repo is a containerized Spring Boot backend (WAR on Tomcat) plus a React/Vite SPA served by Nginx. The SPA calls the backend via /api, and the backend uses PostgreSQL (JPA/Hibernate) and Llama.cpp via OpenAI-compatible Spring AI.

## Architecture and flow
- Frontend: `dep/ai-forgot-this-frontend` builds to `web/` (served by Nginx). SPA routes fall back to `index.html`.
- Reverse proxy: `dockerfiles/web/nginx.conf` proxies `/api/*` to the app container at `http://app:8080` (note: no trailing slash on proxy_pass to keep the `/api` prefix).
- Backend: Spring Boot 3.5, packaged as `war`, deployed by `dockerfiles/app/Dockerfile` into Tomcat. Security is configured in `AiForgotTheseCardsApplication.java`.
- Data: PostgreSQL via JPA/Hibernate. Schema managed by `spring.jpa.hibernate.ddl-auto=update` (no Flyway/Liquibase).
- AI: Spring AI chat client used in `web/controller/AiController.java`.

Key paths:
- Backend code: `src/main/java/com/darkmusic/aiforgotthesecards/**`
- REST controllers: `web/controller/*.java`; DTOs: `web/contracts/*.java`
- Entities/DAOs: `business/entities/*.java`, `business/entities/repositories/*`
- Docker compose stack: `docker-compose.yml` (services: db, app, web)

## Build and run
- Containerized: run the Just target that builds the backend, bundles the SPA, and starts the stack:
  - `just build-deploy` (requires PowerShell Core `pwsh`; Justfile sets `set shell := ["pwsh", "-c"]`).
  - Open http://localhost:8086 (Nginx). App is on http://localhost:8080.
- Backend only (local): `./mvnw spring-boot:run` (ensure Postgres is up, e.g., `docker compose up -d db`).
- Frontend dev: in `dep/ai-forgot-this-frontend`, run `npm install`, then `npm run dev` for Vite. Production build is driven by Maven via `frontend-maven-plugin` (Node v23.6.1, npm 10.9.2) running `build:dev` then `deploy` (copies to `../../web`).

Notes on scripts/compose naming:
- Compose services are `db`, `app`, `web`. Just recipes target the `db` service for export/import and lifecycle tasks.

## Security, auth, and CSRF
- SecurityFilterChain in `AiForgotTheseCardsApplication.java`:
  - Public: `/`, `/index.html`, `/assets/**`, `/favicon*`, `/vite*`, `/api/csrf`, `/v3/api-docs/**`, `/actuator/**`.
  - Auth: `/api/**` requires login; `/admin/**` requires role `ROLE_ADMIN`.
  - Form login posts to `/api/login`; success returns 200 (no redirect), failure 401. Logout posts to `/api/logout` and returns 204.
- CSRF: SPA fetches token from `/api/csrf` and includes header from response headerName for unsafe methods. See `dep/ai-forgot-this-frontend/src/lib/api.ts`.

## REST/API patterns
- Controllers under `web/controller` expose JSON endpoints, e.g.:
  - AI: `POST /api/ai/chat` with body `{ model, question, userId }` -> `{ answer }`.
  - Users: `GET /api/current-user`, `GET /api/user/{id}`, admin-only `POST/DELETE /api/user`.
  - Decks/Tags/Cards follow `/api/{resource}/...` with typical CRUD.
- For new endpoints:
  - Add `@RestController` in `web/controller`, return DTOs from `web/contracts`.
  - If path is under `/api/**`, it will be secured by default.

## Data access conventions
- Entities in `business/entities` use Lombok and JPA annotations. Example: `User` maps to table name "`user`" to avoid reserved word issues.
- DAO pattern: each entity has a `*DAO` interface (extends `CrudRepository`) plus a manual `*DAOImpl` using `EntityManager` for queries (e.g., `DeckDAOImpl`). Prefer adding typed queries there.

## Llama.cpp integration
- `AiController` uses `org.springframework.ai.chat.client.ChatClient`:
  - `POST /api/ai/chat` sends a single user message and persists Q/A to `AiChat`.
- Base URL configured via `spring.ai.openai.chat.base-url` (defaults to `http://localhost:8080/v1`). Ensure `llama-server -m <model path>` is running.

## Testing and profiles
- Unit tests via Surefire (`mvn test`), includes `**/*Test*.java`.
- `application-test.properties` uses H2 in-memory DB and port 8086 for tests.

## Gotchas
- Keep Nginx `proxy_pass` for `/api/` without trailing slash to preserve the `/api` prefix.
- Just recipes require `pwsh` even on Linux/macOS. If unavailable, run Maven/compose commands manually.
- The compose `db` service name must match JDBC URL host in `application.properties` (currently `jdbc:postgresql://db:5432/cards`).

## Frontend notes
- Avoid creating inline styles; instead add styles to scss files and reference them.

## Common tasks
- Add a new API endpoint
  1) Create a DTO in `web/contracts` if needed.
  2) Add a `@RestController` under `web/controller` and expose `/api/...` routes.
  3) Use DAOs in `business/entities/repositories` (prefer adding queries in `*DAOImpl` with `EntityManager`).
  4) Security: endpoints under `/api/**` are authenticated by default; use `@Secured("ROLE_ADMIN")` for admin-only.
  5) Frontend: call via `apiFetch` in `dep/.../src/lib/api.ts` (ensure `primeCsrf()` for unsafe methods) and wire to a component.

- Persist AI interactions
  1) Use `ChatClient` in `AiController` to call the model.
  2) Save Q/A to `AiChat` using `AiChatDAO` as shown in `POST /api/ai/ask`.

- Build and redeploy the stack
  - `just build-deploy` to rebuild backend, build SPA, and start `db`,`app`,`web`.
  - `just redeploy-watch` to do a clean rebuild and tail app logs.

- Export/import the database (requires password prompt)
  - Export: `just export-db` -> writes `db/backup.sql`.
  - Import: `just import-db` -> drops/recreates and restores from `db/backup.sql`.

### Example: add a Deck search endpoint (end-to-end)
1) DAO method (add to `business/entities/repositories/DeckDAO.java` and implement in `DeckDAOImpl`):
  - Interface: `List<Deck> query(TypedQuery<Deck> query);`
  - Impl example query: `em.createQuery("from Deck where lower(name) like lower(:q)", Deck.class).setParameter("q", "%"+q+"%").getResultList();`
2) Controller (add to `web/controller/DeckController.java`):
  - `@GetMapping("/api/deck/search") public List<Deck> search(@RequestParam String q) { return deckDAO.query(em.createQuery(...)); }`
  - For response shaping, return a DTO from `web/contracts` (e.g., `DeckSummary { Long id; String name; }`).
3) Security: path under `/api/**` is already authenticated. Use `@Secured("ROLE_ADMIN")` if limiting access.
4) Frontend (use `src/lib/api.ts`):
  - `type DeckSummary = { id: number; name: string }`
  - `const results = await getJson<DeckSummary[]>("/api/deck/search?q=" + encodeURIComponent(q));`
  - Render in a component as needed.

### Example: add a POST endpoint with CSRF (create Tag)
1) DTO (optional): If shaping request/response, add a DTO under `web/contracts` (e.g., `CreateTagRequest { String name; Long userId; }`).
2) Controller (`web/controller/TagController.java`):
  - `@PostMapping("/api/tag") public Tag create(@RequestBody Tag tag) { return tagDAO.save(tag); }`
  - Admin-only? Use `@Secured("ROLE_ADMIN")`.
3) Validation: Use bean validation annotations on entity/DTO (e.g., `@NotBlank`) and `@Valid` on the handler method param.
4) CSRF on SPA: call `primeCsrf()` once, then use `postJson` from `src/lib/api.ts`:
  - `await primeCsrf(); const created = await postJson<Tag>("/api/tag", { name, user: { id: userId } });`
5) Verify security: `/api/**` requires login; handler returns 401 on unauthenticated and 403 if CSRF missing/invalid (the wrapper retries once on 403).
