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
- Docker containers: `db`, `app`, `web` (managed via Makefile, not docker-compose)

## Build and run
- Containerized: run Make targets that build the backend, bundle the SPA, and start containers:
  - `make build-deploy` – builds both Docker images and deploys the app container.
  - `make build-deploy-nocache` – same, but forces a clean rebuild (`--no-cache`).
  - Open http://localhost:8086 (Nginx). App is on http://localhost:8080.
- Backend only (local): `./mvnw spring-boot:run` (ensure Postgres is up, e.g., `make up` to start db container).
- Frontend dev: in `dep/ai-forgot-this-frontend`, run `npm install`, then `npm run dev` for Vite. Production build is driven by Maven via `frontend-maven-plugin` (Node v23.6.1, npm 10.9.2) running `build:dev` then `deploy` (copies to `../../web`).

Notes on container naming:
- Containers are `db`, `app`, `web`. Network is `cards-net`. DB volume is `pgdata`.
- Make targets use plain `docker run` commands, not docker-compose.
- Optional `.env` file for environment overrides (e.g., `POSTGRES_USER`, `POSTGRES_DB`, `USE_NEXUS_MAVEN`, `NEXUS_MAVEN_MIRROR_URL`).

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
- Base URL configured via `spring.ai.openai.chat.base-url` (defaults to `http://localhost:8087`). Ensure llama-server is running (see `make build-llamacpp-cpu` and `make start-llamacpp`).
  **Note:** By default, the backend uses port 8080; to avoid conflicts, run llama-server on a different port (e.g., 8087) and update the base URL accordingly.
  Note: `make start-llamacpp` requires the parameters `LLAMA_MODEL_PATH` and `LLAMACPP_PORT`, e.g.
  `make start-llamacpp LLAMA_MODEL_PATH=/path/to/model.gguf LLAMACPP_PORT=8080`

## Testing and profiles
- Unit tests via Surefire (`mvn test`), includes `**/*Test*.java`.
- `application-test.properties` uses H2 in-memory DB and port 8086 for tests.

## Gotchas
- Keep Nginx `proxy_pass` for `/api/` without trailing slash to preserve the `/api` prefix.
- The `db` container name must match host portion of the JDBC URL configured in the `.env` file with setting name DB_URL.
- DB container publishes PostgreSQL on port 5433 (host) → 5432 (container) to avoid conflicts.

## Frontend notes
- Avoid creating inline styles; instead add styles to scss files and reference them.
- Cram and Review sessions support filtering by card tags; the session UIs use `TagWidget` with an optional `availableTags` prop to scope suggestions to tags present in the selected deck (or the current review queue).
- Cram and Review also show a `TagCloud` widget (see `dep/ai-forgot-this-frontend/src/components/Main/Shared/TagCloud.tsx`) that visualizes tag frequency and can be used to toggle the tag filter.

## Common tasks

### Build and deployment
- **Build images**: `make build` – builds both `app` and `web` Docker images.
- **Start containers**: `make up` – starts `db`, `app`, `web` (creates if needed, otherwise starts existing).
- **Stop containers**: `make down` – removes all containers (keeps volumes).
- **Stop without removing**: `make stop` – stops containers without removing them.
- **Restart**: `make restart` – equivalent to `make down up`.
- **Build and deploy**: `make build-deploy` – builds images, starts stack, and redeploys the app container.
- **Clean rebuild**: `make delete-redeploy` – removes containers and volumes, rebuilds, and starts fresh.
- **Clean rebuild + watch logs**: `make redeploy-watch` – does a full clean rebuild and tails Tomcat logs.

### Database operations (requires password prompt)
- **Export**: `make export-db` – dumps database to `db/backup.sql`.
- **Import**: `make import-db` – drops/recreates database and restores from `db/backup.sql`.
- **Drop and recreate**: `make drop-and-recreate-db` – removes container and volume, recreates fresh database. Prompt user first before proceeding with this.

### Maven dependency caching (optional Nexus)
- **Start Nexus**: `make nexus-up` – starts Sonatype Nexus 3 on port 8081 with persistent volume.
- **Enable caching**: Set `USE_NEXUS_MAVEN=1` in `.env` (or export in shell). Default mirror URL is `http://host.docker.internal:8081/repository/maven-public`.
- **Check status**: `make nexus-status` – shows Nexus container status.
- **View logs**: `make nexus-logs` – tails Nexus logs.
- **Stop Nexus**: `make nexus-down` – removes Nexus container (never called by other targets).

APT mirrors (optional, used during app/web Dockerfile builds):
- You can also proxy APT traffic via Nexus to speed up Docker builds.
- Set any of these in `.env` to the Nexus repo URLs you created:
  - `NEXUS_APT_MIRROR_ARCHIVE_UBUNTU_NOBLE_URL`
  - `NEXUS_APT_MIRROR_SECURITY_UBUNTU_NOBLE_URL`
  - `NEXUS_APT_MIRROR_DEBIAN_BOOKWORM_URL`
  - `NEXUS_APT_MIRROR_SECURITY_DEBIAN_BOOKWORM_URL`
- Example values (adjust host/paths to your Nexus):
  - `http://localhost:8081/repository/archive.ubuntu.com_noble/`
  - `http://localhost:8081/repository/security.ubuntu.com_noble/`
  - `http://localhost:8081/repository/deb.debian.org-bookworm-apt-proxy/`
  - `http://localhost:8081/repository/deb.debian.org-bookworm-security-apt-proxy/`
- If set, the app/web image builds rewrite APT sources to use these proxies; otherwise they use public upstream mirrors.

Note: Nexus targets are independent; other Make targets never start or stop Nexus.

- **Start server**: `make start-llamacpp LLAMA_MODEL_PATH=/path/to/model.gguf LLAMACPP_PORT=8087` – runs llama-server on specified port (avoid using 8080 to prevent conflict with backend).
- **Build CPU server**: `make build-llamacpp-cpu` – compiles llama.cpp in `dep/llama.cpp/build`.
- **Start server**: `make start-llamacpp LLAMA_MODEL_PATH=/path/to/model.gguf LLAMACPP_PORT=8080` – runs llama-server on specified port.

### Debugging
- **Tail app logs**: `make tail-tomcat-logs` – follows logs from the `app` container.

### Add a new API endpoint
1) Create a DTO in `web/contracts` if needed.
2) Add a `@RestController` under `web/controller` and expose `/api/...` routes.
3) Use DAOs in `business/entities/repositories` (prefer adding queries in `*DAOImpl` with `EntityManager`).
4) Security: endpoints under `/api/**` are authenticated by default; use `@Secured("ROLE_ADMIN")` for admin-only.
5) Frontend: call via `apiFetch` in `dep/.../src/lib/api.ts` (ensure `primeCsrf()` for unsafe methods) and wire to a component.

### Persist AI interactions
1) Use `ChatClient` in `AiController` to call the model.
2) Save Q/A to `AiChat` using `AiChatDAO` as shown in `POST /api/ai/chat`.

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
