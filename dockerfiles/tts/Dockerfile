# syntax=docker/dockerfile:1.7
FROM python:3.12-slim AS setup

WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive
ENV BAKED_HF_HOME=/opt/tts-model-cache/huggingface
ENV HF_HOME=/models/huggingface
ARG TTS_PRELOAD_MODELS=
ARG TTS_TORCH_INDEX_URL=https://download.pytorch.org/whl/cpu
ARG TTS_TORCH_VERSION=2.6.0+cpu
ARG TTS_TORCHAUDIO_VERSION=2.6.0+cpu

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

FROM setup AS torch
RUN pip install --no-cache-dir --index-url "$TTS_TORCH_INDEX_URL" \
    torch=="$TTS_TORCH_VERSION" \
    torchaudio=="$TTS_TORCHAUDIO_VERSION"

FROM torch AS model-bake
COPY services/tts/prefetch_models.py /app/prefetch_models.py
COPY services/tts/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

RUN --mount=type=secret,id=hf_token,required=false \
    mkdir -p "$BAKED_HF_HOME" \
    && HF_HOME="$BAKED_HF_HOME" TTS_PRELOAD_MODELS="$TTS_PRELOAD_MODELS" \
    python /app/prefetch_models.py

FROM model-bake AS app
COPY services/tts/app.py /app/app.py
COPY services/tts/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8091
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8091"]
