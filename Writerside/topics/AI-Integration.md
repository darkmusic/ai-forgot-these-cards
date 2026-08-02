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

## Optional TTS mode

Decks can optionally enable text-to-speech. The Java backend owns deck/card settings,
authentication, request timeouts, and filesystem WAV caching, then calls a Python
sidecar for direct PyTorch/Transformers inference.

### 1) Enable the sidecar

In `.env` set:

- `ENABLE_TTS=1`
- `TTS_SERVICE_URL=http://tts:8091`
- `TTS_STORAGE_DIR=/data/tts`
- `TTS_REQUEST_TIMEOUT=10m`

If the Hugging Face model is gated, also set `HF_TOKEN` after accepting the model
access terms in Hugging Face.

### 2) Build and run

```bash
make build-deploy
```

When `ENABLE_TTS=1`, the Makefile builds and runs the `aiforgot/tts` FastAPI
container. Model downloads are cached in the `tts-model-cache` Docker volume.

### 3) Configure a deck

In deck edit, enable TTS, set a model id such as `ai4bharat/indic-parler-tts`,
and add one or more named presets. Presets can specify speaker, language,
caption/style prompt, and advanced model generation JSON.
