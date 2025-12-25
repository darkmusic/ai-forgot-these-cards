# Operations and monitoring

## Logs

Tail the app container logs:

```bash
make tail-tomcat-logs
```

## Actuator

The backend exposes Spring Boot actuator endpoints for monitoring.

Examples:

- Core stack: <http://localhost:8080/actuator>
- Full stack: typically through Nginx at <http://localhost:8086/api/actuator>

## Swagger / OpenAPI

Swagger UI is available at:

- Core stack: <http://localhost:8080/swagger-ui/index.html>
- Full stack: <http://localhost:8086/api/swagger-ui/index.html>

## API note

SRS statistics can be fetched from `GET /api/srs/stats`.

Some endpoints support optional query parameters (for example, scoping stats by `deckId`).
