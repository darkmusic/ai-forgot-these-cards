# Architecture

Ai Forgot These Cards is built as a SPA + backend + database, with optional AI integration.

## Application architecture

- **Frontend**: React/Vite SPA
- **Backend**: Spring Boot application packaged as a WAR and run on Tomcat
- **Persistence**: JPA/Hibernate, backed by Postgres (default) or SQLite single-file mode
- **AI (optional)**: Spring AI ChatClient talking to an OpenAI-compatible API (hosted provider or llama.cpp)
- **TTS (optional)**: Java-owned TTS API and WAV cache backed by a Python/FastAPI PyTorch sidecar

```mermaid
flowchart TD
   subgraph Frontend
      SPA[React SPA]
   end

   subgraph Backend
      APP["Java Spring Boot\nTomcat exec WAR"]
      JPA[JPA/Hibernate]

      subgraph DB["Database - choose one"]
         PG[(PostgreSQL)]
         SQLITE[(SQLite single-file DB)]
      end

      subgraph AI["AI provider - optional"]
         CHAT["Spring AI ChatClient\nOpenAI-compatible API"]
         OPENAI["OpenAI hosted"]
         LLAMA["Llama.cpp server\nOpenAI-compatible"]
         TTS["Python TTS sidecar\nFastAPI + PyTorch"]
      end

      NEXUS["Sonatype Nexus - optional build-time cache"]
   end

   SPA -->|HTTP| APP
   APP --> JPA
   JPA -->|DB_VENDOR=postgres| PG
   JPA -->|DB_VENDOR=sqlite| SQLITE

   APP -->|Chat requests optional| CHAT
   CHAT -->|Hosted| OPENAI
   CHAT -->|Local/remote| LLAMA
   APP -->|TTS synthesis optional| TTS

   APP -.->|Maven deps optional| NEXUS
```

## Deployment topologies

- **Core stack**: app + database (app serves UI + API)
- **Full stack**: Nginx serves the SPA and proxies `/api` to the app
- **SQLite variants**: replace Postgres with a mounted SQLite `.db` file
- **TTS sidecar**: optional FastAPI/PyTorch container enabled with `ENABLE_TTS=1`; CPU by default, GPU-capable with CUDA PyTorch wheels and `TTS_DOCKER_RUN_FLAGS=--gpus all`

See: [Deployment.md](Deployment.md)

For the full TTS data flow and configuration model, see [Text-to-speech.md](Text-to-speech.md).
