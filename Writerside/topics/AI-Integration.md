# AI Integration

AI features are optional. When enabled, the backend uses Spring AI’s ChatClient to talk to an **OpenAI-compatible** API.

## Easy mode (hosted provider)

Set an API key in `.env`:

- `SPRING_AI_OPENAI_API_KEY=...`

This is the simplest path if you already use a hosted provider.

## Local mode (llama.cpp)

You can run llama.cpp in OpenAI-compatible server mode and point the app at it.

### 1) Build llama.cpp (CPU)

```bash
make build-llamacpp-cpu
```

### 2) Start llama-server

You need a GGUF model file. Then run:

```bash
make start-llamacpp LLAMA_MODEL_PATH=/path/to/model.gguf LLAMACPP_PORT=8087
```

> **Tip:** Avoid port 8080 to prevent conflicts with the backend.

### 3) Point the app at llama.cpp

In `.env` set:

- `SPRING_AI_OPENAI_CHAT_BASE_URL=http://host.docker.internal:8087`

This value must be reachable **from inside the app container**. The Makefile adds a host gateway mapping on Linux to make `host.docker.internal` work in most Docker setups.

## Performance note

Model loading can be slow for larger models. First request latency may be high while tensors load.
