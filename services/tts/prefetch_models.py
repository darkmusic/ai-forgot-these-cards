from __future__ import annotations

import logging
import os
import time

import torch
from transformers import AutoTokenizer

from parler_tts import ParlerTTSForConditionalGeneration


logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s [tts-prefetch] %(message)s",
    force=True,
)
logger = logging.getLogger("tts-prefetch")


def token_from_env_or_secret() -> str | None:
    if os.getenv("HF_TOKEN"):
        return os.getenv("HF_TOKEN")
    secret_path = "/run/secrets/hf_token"
    if os.path.exists(secret_path):
        with open(secret_path, encoding="utf-8") as handle:
            value = handle.read().strip()
        return value or None
    return None


def main() -> None:
    raw_models = os.getenv("TTS_PRELOAD_MODELS", "")
    model_ids = [model.strip() for model in raw_models.split(",") if model.strip()]
    if not model_ids:
        logger.info("No TTS_PRELOAD_MODELS configured; skipping model prefetch")
        return

    token = token_from_env_or_secret()
    logger.info(
        "Prefetching %s TTS model(s) into HF_HOME=%s hfTokenConfigured=%s",
        len(model_ids),
        os.getenv("HF_HOME"),
        bool(token),
    )

    for model_id in model_ids:
        started_at = time.monotonic()
        logger.info("Prefetch started modelId=%s", model_id)
        model = ParlerTTSForConditionalGeneration.from_pretrained(model_id, token=token)
        AutoTokenizer.from_pretrained(model_id, token=token)
        description_model = model.config.text_encoder._name_or_path
        AutoTokenizer.from_pretrained(description_model, token=token)
        del model
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        logger.info(
            "Prefetch complete modelId=%s descriptionModel=%s durationSec=%.1f",
            model_id,
            description_model,
            time.monotonic() - started_at,
        )


if __name__ == "__main__":
    main()
