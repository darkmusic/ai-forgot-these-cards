## Ai Forgot These Cards!
This is an AI-assisted flashcard creation and quiz website.

The _AI-assisted_ part is to enable chatting with an LLM during creation/editing of a flashcard.

This project consists of two parts:

1. **Backend** (this repo): [Java/Spring, JPA/Hibernate, PostgreSQL](https://github.com/darkmusic/ai-forgot-these-cards)
2. **Frontend** (submodule of this repo): [React](https://github.com/darkmusic/ai-forgot-this-frontend)

Features:
- Admin management
- Spring Security
- User profiles
- Ollama integration
  - List models
  - Add/pull model
  - Chat with model
- Deck management
- Card management
- Quiz

Runtime Requirements:
- Java Runtime
- Ollama must be installed and running
- Docker/Podman PostgreSQL container must be up and running (see [compose.yaml](compose.yaml)) or another existing PostgreSQL server must be available
