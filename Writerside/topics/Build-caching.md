# Build caching (optional)

If you rebuild the Docker images frequently, you can speed things up by caching dependencies.

## Maven dependency caching (Nexus)

Start Nexus:

```bash
make nexus-up
```

Enable caching in `.env`:

```bash
USE_NEXUS_MAVEN=1
# Optional override:
# NEXUS_MAVEN_MIRROR_URL=http://host.docker.internal:8081/repository/maven-public
```

Notes:

- Nexus can take 1–2 minutes to become ready on first run.
- On Linux, the build maps `host.docker.internal` using Docker’s host gateway mapping.

## APT proxy caching (Nexus)

You can also proxy APT traffic through Nexus during Docker builds by setting the APT mirror variables in `.env`.

These are used only during image builds; if unset, builds fall back to upstream mirrors.
