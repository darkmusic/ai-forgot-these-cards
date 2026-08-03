# Text-to-speech

Ai Forgot These Cards includes optional multilingual text-to-speech (TTS) support for decks. The Java backend owns authentication, deck/card configuration, request orchestration, WAV caching, and audio streaming. Actual model inference runs in a separate Python/FastAPI sidecar so Hugging Face PyTorch models can be used even when no GGUF build exists.

TTS is disabled by default. When enabled for a deck, cards can expose one or more playable audio items. Pressing a TTS control asks the backend to resolve the configured text and voice, call the sidecar if no cached WAV exists, store audio metadata, and return an embedded browser audio player for replay.

## Architecture

The TTS flow has three layers:

- **Frontend**: deck/card configuration screens, script-aware Markdown rendering, and playback controls in card view, review, and cram.
- **Java backend**: `/api/deck/{id}/tts`, `/api/tts/card/{cardId}/items`, `/api/tts/card/{cardId}/generate`, `/api/tts/audio/{audioId}`; owner checks; cache key generation; filesystem storage.
- **Python sidecar**: FastAPI service with `/health` and `/synthesize`; loads `parler-tts`, `transformers`, `torch`, and `soundfile`; returns `audio/wav`.

Generated audio is cached under `TTS_STORAGE_DIR`. Cache keys include the model id, target, variant, language, text source, resolved text, caption, speaker, generation config, display side, speed, and output format. Changing any of those inputs produces a different cached WAV. The regenerate button uses `force=true` to create a fresh file even when a matching cache entry exists.

## Runtime configuration

Set these in `.env` when using the Makefile-managed sidecar:

| Variable | Purpose | Default/example |
|---|---|---|
| `ENABLE_TTS` | Build and run the TTS sidecar in Makefile container workflows | `0`; set to `1` |
| `TTS_SERVICE_URL` | URL the Java backend uses to reach the sidecar | `http://tts:8091` in Docker, `http://localhost:8091` locally |
| `TTS_STORAGE_DIR` | Directory where the Java backend stores generated WAV files | `/data/tts` in containers |
| `TTS_REQUEST_TIMEOUT` | Backend wait time for sidecar synthesis | `30m` |
| `TTS_HOST_PORT` | Host port mapped to sidecar port `8091` | `8091` |
| `HF_TOKEN` | Optional Hugging Face token for gated/private models | unset |
| `TTS_PRELOAD_MODELS` | Optional comma-separated model ids to bake into the image at build time | `ai4bharat/indic-parler-tts` |
| `TTS_DOCKER_RUN_FLAGS` | Extra `docker run` flags for the sidecar | `--gpus all` for NVIDIA GPU runtime |

The Java properties are also available directly:

```text
tts.service-url=${TTS_SERVICE_URL:http://localhost:8091}
tts.storage-dir=${TTS_STORAGE_DIR:./data/tts}
tts.request-timeout=${TTS_REQUEST_TIMEOUT:PT30M}
```

## Build and run

For the default CPU image:

```bash
ENABLE_TTS=1 make build-deploy
```

To build only the sidecar image:

```bash
make build-tts-image
```

The sidecar image installs CPU PyTorch wheels by default:

```bash
TTS_TORCH_INDEX_URL=https://download.pytorch.org/whl/cpu
TTS_TORCH_VERSION=2.6.0+cpu
TTS_TORCHAUDIO_VERSION=2.6.0+cpu
```

Model downloads are stored in the `tts-model-cache` Docker volume mounted at `/models`. The sidecar uses `HF_HOME=/models/huggingface`.

## GPU support

For NVIDIA GPUs, build the image with CUDA PyTorch wheels and run the sidecar with GPU access:

```bash
TTS_TORCH_INDEX_URL=https://download.pytorch.org/whl/cu124
TTS_TORCH_VERSION=2.6.0
TTS_TORCHAUDIO_VERSION=2.6.0
make build-tts-image
```

Then set:

```bash
ENABLE_TTS=1
TTS_DOCKER_RUN_FLAGS=--gpus all
```

The sidecar chooses `cuda:0` when `torch.cuda.is_available()` is true; otherwise it falls back to CPU. Check the sidecar health endpoint:

```bash
curl http://localhost:8091/health
```

The response includes `device`, `cudaAvailable`, `cudaDeviceCount`, and `torchVersion`.

## Preloading models

Large Hugging Face models can make the first synthesis request slow. To download model files during image build:

```bash
TTS_PRELOAD_MODELS=ai4bharat/indic-parler-tts make build-tts-image
```

For gated models, set `HF_TOKEN` and accept the model terms on Hugging Face first. The Dockerfile passes `HF_TOKEN` as a BuildKit secret when it is present. Preloaded files are baked into `/opt/tts-model-cache/huggingface`; at container startup, `entrypoint.sh` seeds the runtime `/models/huggingface` cache if it is empty or missing those snapshots.

The sidecar also keeps an in-memory LRU cache for up to two loaded models. Restarting the sidecar clears the in-memory cache but not the Docker volume or baked model files.

## Deck configuration

In deck edit:

- Enable **TTS Enabled**.
- Set **TTS Model** to a Hugging Face model id, for example `ai4bharat/indic-parler-tts`.
- Edit **TTS Config JSON** to define semantic targets and voice variants.

The default deck config is:

```json
{
  "provider": "indic-parler-tts",
  "targets": {
    "word": { "enabled": true, "displaySide": "FRONT", "voices": ["hindi", "urdu"] },
    "example": { "enabled": true, "displaySide": "BACK", "voices": ["hindi", "urdu"] }
  },
  "variants": {
    "hindi": {
      "language": "hi",
      "textSource": "devanagari",
      "caption": "",
      "speaker": "",
      "generationConfig": {}
    },
    "urdu": {
      "language": "ur",
      "textSource": "urduScript",
      "caption": "",
      "speaker": "",
      "generationConfig": {}
    }
  },
  "defaultVariant": "hindi",
  "showVariantControls": "both"
}
```

Important fields:

- `targets`: semantic audio locations such as `word` or `example`.
- `targets.<name>.enabled`: disables a target when false.
- `targets.<name>.displaySide`: `FRONT` or `BACK`; defaults to `BACK`.
- `targets.<name>.voices`: list of variant names to generate for that target.
- `targets.<name>.textSource`: optional explicit source name; `auto` or blank lets the backend infer from target and variant.
- `variants.<name>.language`: language code passed to the sidecar.
- `variants.<name>.textSource`: source field used for that variant.
- `variants.<name>.caption`: style/voice prompt. If blank, the sidecar builds a generic clear-speech caption.
- `variants.<name>.speaker`: speaker description/name used in the default caption.
- `variants.<name>.generationConfig`: model generation kwargs passed to `model.generate`.
- `variants.<name>.speed`: stored in cache metadata; the current sidecar does not apply time stretching.

Example with tuned Parler generation:

```json
{
  "targets": {
    "word": { "enabled": true, "displaySide": "FRONT", "voices": ["hindiSlow"] },
    "example": { "enabled": true, "displaySide": "BACK", "voices": ["urduClear"] }
  },
  "variants": {
    "hindiSlow": {
      "language": "hi",
      "textSource": "devanagari",
      "speaker": "A female Hindi teacher",
      "caption": "A female Hindi teacher speaks slowly and clearly in a quiet studio.",
      "generationConfig": {
        "do_sample": true,
        "temperature": 0.7,
        "top_p": 0.9,
        "max_new_tokens": 768
      }
    },
    "urduClear": {
      "language": "ur",
      "textSource": "example.urduScript",
      "speaker": "A male Urdu teacher",
      "caption": "A male Urdu teacher speaks clearly with natural pacing and no background noise.",
      "generationConfig": {
        "do_sample": true,
        "temperature": 0.8,
        "top_p": 0.9
      }
    }
  },
  "defaultVariant": "hindiSlow"
}
```

## Card configuration

Cards do not need a separate TTS text field. The backend resolves text from card Markdown fields. Common source labels include:

```text
Hindi: नमस्ते
Urdu Script: سلام
Romanized: namaste

Example:
Hindi: नमस्ते, आप कैसे हैं?
Urdu Script: سلام، آپ کیسے ہیں؟
Romanized: namaste, aap kaise hain?
```

The source keys are normalized, so labels such as `Urdu Script`, `urdu-script`, and `urdu_script` become `urduScript`. Example sources get the `example.` prefix when they appear under an `Example:` section.

Built-in aliases:

- `hindi` and `devanagari`
- `urdu` and `urduScript`
- `romanized` and `romanization`
- `example.hindi` and `example.devanagari`
- `example.urdu` and `example.urduScript`

Card edit also exposes **TTS Override JSON** when the deck has TTS enabled. Card JSON is deeply merged into the deck JSON. Use it to override a target, change which variants apply to one card, or disable TTS for a target without changing the deck.

Example card override:

```json
{
  "targets": {
    "word": { "voices": ["urdu"] },
    "example": { "enabled": false }
  }
}
```

## Inline TTS blocks

A card can include a fenced TTS metadata block to override target voices without editing JSON:

```text
Hindi: किताब
Urdu Script: کتاب

::: tts
word:
  voices: [hindi, urdu]
example:
  voices: [urdu]
:::
```

The backend strips `::: tts ... :::` blocks from resolved text sources before synthesis. The block is currently parsed for `voices` overrides by target.

## Script-aware card rendering

Decks have **Presentation Config JSON** for script-specific font styling. Markdown rendering detects Unicode scripts in text nodes and wraps matching runs with safe inline styles. This is useful for multilingual cards where Arabic-script Urdu, Devanagari Hindi, Japanese, or other scripts need different fonts or direction.

Built-in defaults include:

- `Arab`: Nastaliq/Naskh-style Arabic-script font stack, `direction: rtl`, taller line height.
- `Deva`: Devanagari font stack.
- `Jpan`: Japanese font stack.

Example deck presentation config:

```json
{
  "scriptStyles": {
    "Arab": {
      "fontFamily": "'Noto Nastaliq Urdu', 'Jameel Noori Nastaleeq', 'Noto Naskh Arabic', serif",
      "lineHeight": "2",
      "direction": "rtl"
    },
    "Deva": {
      "fontFamily": "'Noto Sans Devanagari', 'Nirmala UI', sans-serif"
    },
    "Jpan": {
      "fontFamily": "'Noto Sans JP', 'Yu Gothic', 'Hiragino Sans', sans-serif"
    }
  }
}
```

Supported script keys include common ISO 15924 codes and names such as `Arab`, `Arabic`, `Deva`, `Devanagari`, `Beng`, `Gujarati`, `Gurmukhi`, `Kannada`, `Malayalam`, `Oriya`, `Sinhala`, `Tamil`, `Telugu`, `Hebrew`, `Greek`, `Cyrillic`, `Thai`, `Han`, `Hiragana`, `Katakana`, and `Jpan`.

Allowed style keys are intentionally limited: `direction`, `fontFamily`, `fontFeatureSettings`, `fontSize`, `fontStyle`, `fontVariantLigatures`, `fontWeight`, `letterSpacing`, `lineHeight`, and `textAlign`.

Script styling is applied to regular Markdown text, list items, table cells, and headings. Code, preformatted blocks, script/style tags, and KaTeX output are skipped.

## Playback behavior

The UI resolves available TTS items from:

```http
GET /api/tts/card/{cardId}/items
```

Each item includes target, variant, label, language, text source, resolved text, side, and any cached audio URL. If there is no cached audio, pressing **Generate** starts:

```http
POST /api/tts/card/{cardId}/generate?target=word&variant=hindi
```

Generation uses server-sent events:

- `status`: progress messages and heartbeats.
- `done`: final `TtsAudioResponse`.
- `error`: failure message.

Playback streams through:

```http
GET /api/tts/audio/{audioId}
```

The audio endpoint validates deck ownership before returning `audio/wav`.

## Sidecar API

The Java backend calls:

```http
POST /synthesize
Content-Type: application/json
```

Request body:

```json
{
  "modelId": "ai4bharat/indic-parler-tts",
  "text": "नमस्ते",
  "caption": "A Hindi teacher speaks clearly in a quiet studio.",
  "speaker": "A Hindi teacher",
  "language": "hi",
  "generationConfig": {
    "do_sample": true,
    "temperature": 0.8,
    "top_p": 0.9
  }
}
```

The response is `audio/wav`.

Sidecar environment knobs:

| Variable | Purpose | Default |
|---|---|---|
| `LOG_LEVEL` | Python logging level | `INFO` |
| `HF_TOKEN` | Hugging Face token | unset |
| `HF_HOME` | Model cache directory | `/models/huggingface` |
| `TTS_GENERATION_ATTEMPTS` | Retry count when generated audio fails quality checks | `3` |
| `TTS_DEFAULT_DO_SAMPLE` | Default `do_sample` when omitted | unset |
| `TTS_DEFAULT_TEMPERATURE` | Sampling temperature when `do_sample` is true | `0.8` |
| `TTS_DEFAULT_TOP_P` | Nucleus sampling value when `do_sample` is true | `0.9` |
| `TTS_SEED` | Fixed seed override | stable hash when unset |
| `TTS_MAX_NEW_TOKENS` | Maximum generated tokens; also enables text-length-based defaulting | unset, default cap `768` when enabled |
| `TTS_TARGET_DBFS` | Normalization target | `-18.0` |
| `TTS_MAX_AUDIO_SECONDS` | Quality gate maximum duration | `15.0` |
| `TTS_MIN_AUDIO_SECONDS` | Quality gate minimum duration | `0.25` |
| `TTS_MIN_OUTPUT_ACTIVE_RMS` | Silence detection threshold | `0.015` |

## Database changes

TTS schema is managed by versioned Flyway migrations for both PostgreSQL and SQLite:

- `V2__add_tts_schema.sql`: initial TTS fields and `tts_audio` metadata.
- `V3__semantic_tts_config.sql`: semantic deck/card JSON config, target/variant metadata, resolved text metadata.
- `V4__remove_legacy_tts_preset_schema.sql`: removes the early preset/text-field schema after migrating default config forward.
- `V5__deck_presentation_config.sql`: deck-level script/font presentation config.
- `V6__deck_always_applied_templates.sql`: deck render-only front/back templates.

The app now uses `spring.jpa.hibernate.ddl-auto=validate`; schema changes should be added as paired migration scripts under:

- `src/main/resources/db/migration/postgresql`
- `src/main/resources/db/migration/sqlite`

SQLite mode is selected with `DB_VENDOR=sqlite`. `DatabaseVendorEnvironmentPostProcessor` switches Flyway to the SQLite migration location, creates the SQLite parent directory when needed, enables WAL-related JDBC settings, and keeps the connection pool small for single-writer behavior.

Portable dumps include `tts_audio` metadata. The generated WAV files themselves live on the filesystem under `TTS_STORAGE_DIR`, so include that directory in backups if cached audio should survive a restore.

## Troubleshooting

- **First request is slow**: the model may be downloading or loading. Use `TTS_PRELOAD_MODELS` to bake model files into the image.
- **401/403/gated model errors**: set `HF_TOKEN` and accept the model license/access terms on Hugging Face.
- **CPU is too slow**: build with CUDA PyTorch wheels and run with `TTS_DOCKER_RUN_FLAGS=--gpus all`.
- **Audio is silent or too short**: tune `generationConfig`, speaker/caption prompts, or sidecar quality thresholds such as `TTS_GENERATION_ATTEMPTS`, `TTS_MIN_OUTPUT_ACTIVE_RMS`, and `TTS_MAX_AUDIO_SECONDS`.
- **No TTS controls appear**: ensure deck TTS is enabled, a model id is set, the card has source text matching the configured `textSource`, and the target `displaySide` matches the currently visible card side.
- **Cached audio is stale**: use the regenerate button; it calls the backend with `force=true`.
