# Overview

**Ai Forgot These Cards** is an AI-assisted flashcard creation/management web app with a built-in Spaced Repetition System (SRS).

It’s designed for people who want a fast workflow for building study materials (often with AI help), while still having **full control over their data**:

- Your data lives in a database you control (**PostgreSQL** by default, or **SQLite single-file** mode).
- You can export/import your data (including a portable format for moving between Postgres and SQLite).

## What you can do

- Create and manage **decks**, **cards**, and **tags**
- Study in two modes:
  - **Review (SRS)**: shows due/new cards and updates scheduling based on your rating
  - **Cram**: studies all cards without changing SRS scheduling
- Use AI as an assistant while creating/editing cards (optional)

## Key features

- **AI assistance (optional)** via an OpenAI-compatible API
- **SRS review sessions** (global or per-deck) with due/new/reviewed/total counts
- **Cram mode** for full-deck study without affecting schedules
- **Markdown** and **LaTeX** rendering in cards
- **Bulk entry/updating** of cards
- **Tag filtering** and a **tag cloud** in Cram and Review
- Containerized build + deployment (no local JDK/Node required for the default workflow)

## How it’s deployed

The project supports multiple deployment topologies:

- **Core stack (simplest)**: app + database only; the app serves both UI and `/api`
- **Full stack (reverse-proxy parity)**: Nginx serves the SPA and proxies `/api` to the app
- **SQLite single-file mode**: run without Postgres (either in containers or truly containerless)

## A note on AI and privacy

AI features are optional. When enabled, questions you send to the AI are sent to whatever provider you configure (hosted API key or local OpenAI-compatible server). Always review AI output before saving it.

## Where to start

- Quick, minimal path: [Quickstart.md](Quickstart.md)
- Full setup and options: [Installation-guide.md](Installation-guide.md)
- Install from GitHub Releases (WAR): [Releases.md](Releases.md)
- Run prebuilt GHCR images (Docker): [Container-images.md](Container-images.md)
- Environment variables and `.env`: [Configuration.md](Configuration.md)
- See the UI: [Screenshots.md](Screenshots.md)
