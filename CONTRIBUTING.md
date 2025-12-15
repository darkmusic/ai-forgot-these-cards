# Contributing

Thanks for contributing!

## Releases

This repo publishes release artifacts via GitHub Actions:

- Docker images to GHCR via [.github/workflows/docker.yml](.github/workflows/docker.yml)
- WAR files attached to the GitHub Release via [.github/workflows/release.yml](.github/workflows/release.yml)

### Versioning

- Use semver-like git tags in the format `vMAJOR.MINOR.PATCH` (example: `v1.2.3`).
- The Docker workflow only triggers for tags matching `v*.*.*`.

### Release steps

1.  Make sure `main` is up to date and green
    -  `git checkout main`
    -  `git pull`
    -  Recommended: `./mvnw clean test`
2.  Create and push the tag
    -  `git tag v1.2.3`
    -  `git push origin v1.2.3`
3.  Create the GitHub Release
    -  GitHub UI: **Releases → Draft a new release**
    -  Select the existing tag `v1.2.3`
    -  Publish the release (or create it as a draft)

Creating the release triggers the WAR workflow.

### What CI publishes

#### Docker images (GHCR)

On pushes to `main` and on semver-like tags, CI builds:

- `ghcr.io/<owner>/ai-forgot-these-cards-app:<tag>`
- `ghcr.io/<owner>/ai-forgot-these-cards-web:<tag>`

Tag behavior:

- On `main`: publishes `:latest` and `:sha-...`
- On `vMAJOR.MINOR.PATCH` tags: publishes `:vMAJOR.MINOR.PATCH` (and `:sha-...`)
- On pull requests: builds but does **not** push

#### WAR artifacts (attached to the GitHub Release)

On `release: created`, CI runs `./mvnw -DskipTests package` and uploads:

- `target/*.war` (includes the standard deployable WAR and the runnable `*-exec.war`)
- `target/checksums.sha256`

### Verification checklist

- GitHub Actions shows both workflows succeeded for the release
- The GitHub Release page has the WAR files and `checksums.sha256`
- GHCR shows `vMAJOR.MINOR.PATCH` tags for the two images
