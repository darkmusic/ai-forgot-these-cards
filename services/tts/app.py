from __future__ import annotations

import io
import hashlib
import logging
import os
import time
from itertools import count
from functools import lru_cache
from pathlib import Path
from typing import Any

import soundfile as sf
import numpy as np
import torch
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field
from transformers import AutoTokenizer

from parler_tts import ParlerTTSForConditionalGeneration


logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s [tts] %(message)s",
    force=True,
)
logger = logging.getLogger("tts")
request_counter = count(1)


class AudioQualityError(ValueError):
    pass


class SynthesisRequest(BaseModel):
    modelId: str = Field(min_length=1)
    text: str = Field(min_length=1)
    caption: str | None = None
    speaker: str | None = None
    language: str | None = None
    generationConfig: dict[str, Any] | None = None


class LoadedModel:
    def __init__(self, model_id: str):
        started_at = time.monotonic()
        self.device = selected_device()
        token = os.getenv("HF_TOKEN") or None
        disk_cached = has_hf_snapshot(model_id)
        logger.info(
            "Loading model modelId=%s device=%s cudaAvailable=%s cudaDeviceCount=%s hfTokenConfigured=%s hfHome=%s diskSnapshotPresent=%s",
            model_id,
            self.device,
            torch.cuda.is_available(),
            torch.cuda.device_count(),
            bool(token),
            os.getenv("HF_HOME"),
            disk_cached,
        )
        try:
            model_started_at = time.monotonic()
            self.model = ParlerTTSForConditionalGeneration.from_pretrained(
                model_id,
                token=token,
            ).to(self.device)
            logger.info(
                "Loaded Parler model modelId=%s durationSec=%.1f",
                model_id,
                time.monotonic() - model_started_at,
            )

            tokenizer_started_at = time.monotonic()
            self.tokenizer = AutoTokenizer.from_pretrained(model_id, token=token)
            logger.info(
                "Loaded prompt tokenizer modelId=%s durationSec=%.1f",
                model_id,
                time.monotonic() - tokenizer_started_at,
            )

            description_model = self.model.config.text_encoder._name_or_path
            description_started_at = time.monotonic()
            self.description_tokenizer = AutoTokenizer.from_pretrained(
                description_model,
                token=token,
            )
            logger.info(
                "Loaded description tokenizer modelId=%s descriptionModel=%s durationSec=%.1f totalDurationSec=%.1f",
                model_id,
                description_model,
                time.monotonic() - description_started_at,
                time.monotonic() - started_at,
            )
        except Exception as exc:
            if looks_like_hf_access_error(exc):
                logger.exception(
                    "Model load failed for modelId=%s. This looks like a Hugging Face auth/access issue. "
                    "Check HF_TOKEN and confirm the model terms have been accepted.",
                    model_id,
                )
            else:
                logger.exception("Model load failed for modelId=%s", model_id)
            raise


app = FastAPI(title="Ai Forgot These Cards TTS")


@lru_cache(maxsize=2)
def load_model(model_id: str) -> LoadedModel:
    logger.info(
        "In-memory model cache miss modelId=%s diskSnapshotPresent=%s; loading model object",
        model_id,
        has_hf_snapshot(model_id),
    )
    return LoadedModel(model_id)


@app.get("/health")
def health() -> dict[str, str | bool | int]:
    return {
        "status": "ok",
        "device": selected_device(),
        "cudaAvailable": torch.cuda.is_available(),
        "cudaDeviceCount": torch.cuda.device_count(),
        "torchVersion": torch.__version__,
    }


@app.post("/synthesize")
def synthesize(request: SynthesisRequest) -> Response:
    request_id = next(request_counter)
    started_at = time.monotonic()
    logger.info(
        "Synthesis request started requestId=%s modelId=%s textChars=%s language=%s speaker=%s captionSet=%s generationConfigKeys=%s",
        request_id,
        request.modelId,
        len(request.text),
        request.language,
        request.speaker or "",
        bool(request.caption),
        sorted((request.generationConfig or {}).keys()),
    )
    try:
        logger.info("Loading or reusing model requestId=%s modelId=%s", request_id, request.modelId)
        loaded = load_model(request.modelId)
        logger.info(
            "Model ready requestId=%s modelId=%s elapsedSec=%.1f",
            request_id,
            request.modelId,
            time.monotonic() - started_at,
        )
        caption = resolved_caption(request)
        generation_config = resolved_generation_config(request)
        seed = int(generation_config.pop("seed"))

        tokenization_started_at = time.monotonic()
        description = loaded.description_tokenizer(caption, return_tensors="pt").to(loaded.device)
        prompt = loaded.tokenizer(request.text, return_tensors="pt").to(loaded.device)
        logger.info(
            "Tokenization complete requestId=%s durationSec=%.2f promptTokens=%s descriptionTokens=%s caption=%r",
            request_id,
            time.monotonic() - tokenization_started_at,
            prompt.input_ids.shape[-1],
            description.input_ids.shape[-1],
            caption,
        )

        encode_started_at = time.monotonic()
        audio = generate_valid_audio(
            loaded,
            description,
            prompt,
            generation_config,
            seed,
            request_id,
        )
        buffer = io.BytesIO()
        sf.write(buffer, audio, loaded.model.config.sampling_rate, format="WAV")
        content = buffer.getvalue()
        logger.info(
            "Synthesis request complete requestId=%s durationSec=%.1f encodeSec=%.2f wavBytes=%s",
            request_id,
            time.monotonic() - started_at,
            time.monotonic() - encode_started_at,
            len(content),
        )
        return Response(content=content, media_type="audio/wav")
    except Exception as exc:
        logger.exception(
            "Synthesis request failed requestId=%s modelId=%s durationSec=%.1f",
            request_id,
            request.modelId,
            time.monotonic() - started_at,
        )
        raise HTTPException(status_code=500, detail=str(exc)) from exc


def generate_valid_audio(
    loaded: LoadedModel,
    description: Any,
    prompt: Any,
    generation_config: dict[str, Any],
    seed: int,
    request_id: int,
) -> np.ndarray:
    attempts = int(os.getenv("TTS_GENERATION_ATTEMPTS", "3"))
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        attempt_seed = seed + ((attempt - 1) * 9973)
        if generation_config.get("do_sample"):
            torch.manual_seed(attempt_seed)
            if torch.cuda.is_available():
                torch.cuda.manual_seed_all(attempt_seed)
        generation_started_at = time.monotonic()
        logger.info(
            "Generation started requestId=%s attempt=%s/%s device=%s seed=%s generationConfig=%s",
            request_id,
            attempt,
            attempts,
            loaded.device,
            attempt_seed,
            generation_config,
        )
        with torch.inference_mode():
            generation = loaded.model.generate(
                input_ids=description.input_ids,
                attention_mask=description.attention_mask,
                prompt_input_ids=prompt.input_ids,
                prompt_attention_mask=prompt.attention_mask,
                **generation_config,
            )
        logger.info(
            "Generation complete requestId=%s attempt=%s durationSec=%.1f",
            request_id,
            attempt,
            time.monotonic() - generation_started_at,
        )
        audio = normalize_audio(generation.cpu().numpy().squeeze(), request_id=request_id)
        audio = trim_silence(audio, loaded.model.config.sampling_rate, request_id)
        try:
            validate_audio(audio, loaded.model.config.sampling_rate, request_id)
            return audio
        except AudioQualityError as exc:
            last_error = exc
            logger.warning(
                "Generated audio failed quality gate requestId=%s attempt=%s/%s error=%s",
                request_id,
                attempt,
                attempts,
                exc,
            )
    raise last_error or AudioQualityError(f"Generated audio failed quality gate requestId={request_id}")


def resolved_caption(request: SynthesisRequest) -> str:
    caption = (request.caption or "").strip()
    return caption or default_caption(request)


def default_caption(request: SynthesisRequest) -> str:
    speaker = (request.speaker or "").strip()
    language = language_name(request.language) if request.language else None
    if request.speaker:
        language_phrase = f" in {language}" if language else ""
        return (
            f"{speaker} speaks{language_phrase} with clear audio, natural pacing, moderate pitch, "
            "and a very high quality close-sounding recording with no background noise."
        )
    return (
        "A speaker delivers natural speech with clear audio, natural pacing, moderate pitch, "
        "and a very high quality close-sounding recording with no background noise."
    )


def language_name(language: str) -> str:
    return {
        "hi": "Hindi",
        "ur": "Urdu",
    }.get(language.lower(), language)


def resolved_generation_config(request: SynthesisRequest) -> dict[str, Any]:
    resolved = dict(request.generationConfig or {})
    if "do_sample" not in resolved and os.getenv("TTS_DEFAULT_DO_SAMPLE"):
        resolved["do_sample"] = os.getenv("TTS_DEFAULT_DO_SAMPLE", "false").lower() == "true"
    if "temperature" not in resolved and resolved.get("do_sample"):
        resolved["temperature"] = float(os.getenv("TTS_DEFAULT_TEMPERATURE", "0.8"))
    if "top_p" not in resolved and resolved.get("do_sample"):
        resolved["top_p"] = float(os.getenv("TTS_DEFAULT_TOP_P", "0.9"))
    if "max_new_tokens" not in resolved and os.getenv("TTS_MAX_NEW_TOKENS"):
        resolved["max_new_tokens"] = default_max_new_tokens(request.text)
    if "seed" not in resolved:
        resolved["seed"] = stable_seed(request)
    return resolved


def default_max_new_tokens(text: str) -> int:
    base = int(os.getenv("TTS_MIN_NEW_TOKENS", "256"))
    per_char = int(os.getenv("TTS_NEW_TOKENS_PER_CHAR", "18"))
    limit = int(os.getenv("TTS_MAX_NEW_TOKENS", "768"))
    return min(limit, max(base, len(text.strip()) * per_char))


def stable_seed(request: SynthesisRequest) -> int:
    configured = os.getenv("TTS_SEED")
    if configured:
        return int(configured)
    material = "|".join(
        [
            request.modelId,
            request.text,
            request.caption or "",
            request.speaker or "",
            request.language or "",
            repr(sorted((request.generationConfig or {}).items())),
        ]
    )
    return int.from_bytes(hashlib.sha256(material.encode("utf-8")).digest()[:4], "big")


def looks_like_hf_access_error(exc: Exception) -> bool:
    text = str(exc).lower()
    return any(marker in text for marker in ("gated", "401", "403", "unauthorized", "forbidden", "access"))


def selected_device() -> str:
    return "cuda:0" if torch.cuda.is_available() else "cpu"


def normalize_audio(audio: Any, request_id: int | None = None) -> np.ndarray:
    samples = np.asarray(audio, dtype=np.float32).squeeze()
    if samples.size == 0:
        return samples
    samples = samples - float(np.mean(samples))

    peak_before = float(np.max(np.abs(samples)))
    active_threshold = max(peak_before * 0.01, 10 ** (-45.0 / 20.0))
    active_samples = samples[np.abs(samples) >= active_threshold]
    if active_samples.size < max(32, samples.size // 100):
        active_samples = samples

    rms_before = float(np.sqrt(np.mean(np.square(samples))))
    active_rms_before = float(np.sqrt(np.mean(np.square(active_samples))))
    gain = 1.0
    if active_rms_before > 1e-6:
        target_dbfs = float(os.getenv("TTS_TARGET_DBFS", "-18.0"))
        target_rms = 10 ** (target_dbfs / 20.0)
        max_gain = float(os.getenv("TTS_MAX_GAIN", "16.0"))
        gain = min(target_rms / active_rms_before, max_gain)
        samples = samples * gain

    peak = float(np.max(np.abs(samples)))
    peak_limit = float(os.getenv("TTS_PEAK_LIMIT", "0.98"))
    if peak > peak_limit:
        samples = samples * (peak_limit / peak)
        peak = peak_limit

    rms_after = float(np.sqrt(np.mean(np.square(samples))))
    active_after = samples[np.abs(samples) >= active_threshold]
    active_rms_after = float(np.sqrt(np.mean(np.square(active_after)))) if active_after.size else rms_after
    logger.info(
        "Audio normalized requestId=%s samples=%s peakBefore=%.4f rmsBefore=%.4f activeRmsBefore=%.4f gain=%.2f peakAfter=%.4f rmsAfter=%.4f activeRmsAfter=%.4f",
        request_id,
        samples.size,
        peak_before,
        rms_before,
        active_rms_before,
        gain,
        peak,
        rms_after,
        active_rms_after,
    )
    return samples


def trim_silence(audio: np.ndarray, sampling_rate: int, request_id: int | None = None) -> np.ndarray:
    if audio.size == 0:
        return audio
    frame_size = max(1, int(float(os.getenv("TTS_TRIM_FRAME_SECONDS", "0.03")) * sampling_rate))
    frame_count = int(np.ceil(audio.size / frame_size))
    padded = np.pad(audio, (0, frame_count * frame_size - audio.size))
    frames = padded.reshape(frame_count, frame_size)
    frame_rms = np.sqrt(np.mean(np.square(frames), axis=1))
    max_frame_rms = float(np.max(frame_rms)) if frame_rms.size else 0.0
    if max_frame_rms <= 0:
        return audio

    threshold = max(
        max_frame_rms * float(os.getenv("TTS_TRIM_RMS_RATIO", "0.08")),
        10 ** (float(os.getenv("TTS_TRIM_DBFS", "-46.0")) / 20.0),
    )
    active_frames = np.flatnonzero(frame_rms >= threshold)
    if active_frames.size == 0:
        return audio

    pad = int(float(os.getenv("TTS_TRIM_PAD_SECONDS", "0.08")) * sampling_rate)
    start = max(0, int(active_frames[0]) * frame_size - pad)
    end = min(audio.size, (int(active_frames[-1]) + 1) * frame_size + pad)
    trimmed = audio[start:end]
    min_duration = float(os.getenv("TTS_MIN_AUDIO_SECONDS", "0.25"))
    if trimmed.size / sampling_rate < min_duration:
        logger.info(
            "Audio trim skipped requestId=%s originalSamples=%s candidateSamples=%s reason=below-min-duration threshold=%.5f",
            request_id,
            audio.size,
            trimmed.size,
            threshold,
        )
        return audio
    logger.info(
        "Audio trimmed requestId=%s originalSamples=%s trimmedSamples=%s removedStartSec=%.2f removedEndSec=%.2f threshold=%.5f",
        request_id,
        audio.size,
        trimmed.size,
        start / sampling_rate,
        (audio.size - end) / sampling_rate,
        threshold,
    )
    return trimmed


def validate_audio(audio: np.ndarray, sampling_rate: int, request_id: int) -> None:
    duration_sec = float(audio.size) / float(sampling_rate)
    rms = float(np.sqrt(np.mean(np.square(audio)))) if audio.size else 0.0
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    speech_duration_sec, active_rms = speech_metrics(audio, sampling_rate)
    min_duration = float(os.getenv("TTS_MIN_AUDIO_SECONDS", "0.25"))
    max_duration = float(os.getenv("TTS_MAX_AUDIO_SECONDS", "15.0"))
    min_active_rms = float(os.getenv("TTS_MIN_OUTPUT_ACTIVE_RMS", "0.015"))
    min_speech_duration = float(os.getenv("TTS_MIN_SPEECH_SECONDS", "0.20"))
    if duration_sec < min_duration:
        raise AudioQualityError(
            f"Generated audio was too short requestId={request_id} durationSec={duration_sec:.2f} minDurationSec={min_duration:.2f}"
        )
    if duration_sec > max_duration:
        raise AudioQualityError(
            f"Generated audio was too long requestId={request_id} durationSec={duration_sec:.1f} maxDurationSec={max_duration:.1f}"
        )
    if peak < 0.01 or active_rms < min_active_rms:
        raise AudioQualityError(
            f"Generated audio appears silent requestId={request_id} peak={peak:.4f} rms={rms:.4f} activeRms={active_rms:.4f} minActiveRms={min_active_rms:.4f}"
        )
    if speech_duration_sec < min_speech_duration:
        raise AudioQualityError(
            f"Generated audio had too little speech requestId={request_id} speechDurationSec={speech_duration_sec:.2f} minSpeechDurationSec={min_speech_duration:.2f}"
        )
    logger.info(
        "Audio validated requestId=%s durationSec=%.2f speechDurationSec=%.2f peak=%.4f rms=%.4f activeRms=%.4f",
        request_id,
        duration_sec,
        speech_duration_sec,
        peak,
        rms,
        active_rms,
    )


def speech_metrics(audio: np.ndarray, sampling_rate: int) -> tuple[float, float]:
    if audio.size == 0:
        return 0.0, 0.0
    frame_size = max(1, int(float(os.getenv("TTS_VALIDATE_FRAME_SECONDS", "0.03")) * sampling_rate))
    frame_count = int(np.ceil(audio.size / frame_size))
    padded = np.pad(audio, (0, frame_count * frame_size - audio.size))
    frames = padded.reshape(frame_count, frame_size)
    frame_rms = np.sqrt(np.mean(np.square(frames), axis=1))
    max_frame_rms = float(np.max(frame_rms)) if frame_rms.size else 0.0
    threshold = max(
        max_frame_rms * float(os.getenv("TTS_VALIDATE_RMS_RATIO", "0.08")),
        10 ** (float(os.getenv("TTS_VALIDATE_DBFS", "-46.0")) / 20.0),
    )
    active_frames = frame_rms >= threshold
    active_samples = frames[active_frames].reshape(-1) if np.any(active_frames) else np.array([], dtype=np.float32)
    active_rms = float(np.sqrt(np.mean(np.square(active_samples)))) if active_samples.size else 0.0
    return float(np.count_nonzero(active_frames) * frame_size) / float(sampling_rate), active_rms


def has_hf_snapshot(model_id: str) -> bool:
    hf_home = Path(os.getenv("HF_HOME", "/models/huggingface"))
    repo_dir = hf_home / "hub" / f"models--{model_id.replace('/', '--')}" / "snapshots"
    return repo_dir.is_dir() and any(repo_dir.iterdir())
