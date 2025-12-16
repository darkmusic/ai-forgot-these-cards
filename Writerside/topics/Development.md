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
