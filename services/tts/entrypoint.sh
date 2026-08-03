#!/bin/sh
set -eu

BAKED_HF_HOME="${BAKED_HF_HOME:-/opt/tts-model-cache/huggingface}"
RUNTIME_HF_HOME="${HF_HOME:-/models/huggingface}"

if [ -d "$BAKED_HF_HOME" ]; then
  mkdir -p "$RUNTIME_HF_HOME"
  if [ -d "$BAKED_HF_HOME/hub" ] && find "$BAKED_HF_HOME/hub" -mindepth 3 -maxdepth 3 -type d -path '*/snapshots/*' | grep -q .; then
    echo "Seeding Hugging Face model cache from image into $RUNTIME_HF_HOME"
    cp -an "$BAKED_HF_HOME/." "$RUNTIME_HF_HOME/"
  else
    echo "No baked Hugging Face model cache found; runtime will use/download models in $RUNTIME_HF_HOME"
  fi
fi

exec "$@"
