# Development workflow

This project can be developed either containerized or partially local.

## Backend (local)

If you want to run the backend locally (instead of in Docker):

```bash
./mvnw spring-boot:run
```

You’ll still need a database available (for example, start the DB container with `make up` or `make up-core`).

## Frontend (dev server)

The frontend lives in the `dep/ai-forgot-this-frontend` submodule.

Typical dev workflow:

```bash
cd dep/ai-forgot-this-frontend
npm install
npm run dev
```

Production builds are typically done via the container/Docker build pipeline.

## Tests

Backend unit/integration tests (from the repo root):

```bash
./mvnw -Dskip.npm -Dskip.installnodenpm -Dtest=DeckAssistServiceTests,DeckAssistControllerTests,BulkAiSaveTests test
```

Opt-in live deck-assist smoke tests against a configured endpoint:

```bash
./mvnw -Dtest=DeckAssistLiveTests -DdeckAssist.smoke=true \
    [-DdeckAssist.url=...] [-DdeckAssist.model=...] [-DdeckAssist.operation=correct] test
```

Hosted endpoints need `DECK_ASSIST_TEST_API_KEY` in the environment. These checks send only synthetic cards and never touch the application database.

Frontend tests (from `dep/ai-forgot-this-frontend`):

```bash
npm run test:deck-assist   # draft changes, merge/undo, SSE parsing
npm run test:browser       # bulk entry workflow with mocked API responses
```

Browser tests require Chromium (`npx playwright install chromium`, or set `PLAYWRIGHT_CHROMIUM_EXECUTABLE`).

See [Bulk-ai-tools.md](Bulk-ai-tools.md) for what these suites cover.
