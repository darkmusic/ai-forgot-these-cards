from __future__ import annotations

import io
import os
from functools import lru_cache
from typing import Any

import soundfile as sf
import torch
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field
from transformers import AutoTokenizer

from parler_tts import ParlerTTSForConditionalGeneration


class SynthesisRequest(BaseModel):
    modelId: str = Field(min_length=1)
    text: str = Field(min_length=1)
    caption: str | None = None
    speaker: str | None = None
    language: str | None = None
    generationConfig: dict[str, Any] | None = None


class LoadedModel:
    def __init__(self, model_id: str):
        self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
        token = os.getenv("HF_TOKEN") or None
        self.model = ParlerTTSForConditionalGeneration.from_pretrained(
            model_id,
            token=token,
        ).to(self.device)
        self.tokenizer = AutoTokenizer.from_pretrained(model_id, token=token)
        description_model = self.model.config.text_encoder._name_or_path
        self.description_tokenizer = AutoTokenizer.from_pretrained(
            description_model,
            token=token,
        )


app = FastAPI(title="Ai Forgot These Cards TTS")


@lru_cache(maxsize=2)
def load_model(model_id: str) -> LoadedModel:
    return LoadedModel(model_id)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/synthesize")
def synthesize(request: SynthesisRequest) -> Response:
    try:
        loaded = load_model(request.modelId)
        caption = request.caption or default_caption(request)
        generation_config = request.generationConfig or {}

        description = loaded.description_tokenizer(caption, return_tensors="pt").to(loaded.device)
        prompt = loaded.tokenizer(request.text, return_tensors="pt").to(loaded.device)
        with torch.inference_mode():
            generation = loaded.model.generate(
                input_ids=description.input_ids,
                attention_mask=description.attention_mask,
                prompt_input_ids=prompt.input_ids,
                prompt_attention_mask=prompt.attention_mask,
                **generation_config,
            )

        audio = generation.cpu().numpy().squeeze()
        buffer = io.BytesIO()
        sf.write(buffer, audio, loaded.model.config.sampling_rate, format="WAV")
        return Response(content=buffer.getvalue(), media_type="audio/wav")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


def default_caption(request: SynthesisRequest) -> str:
    details: list[str] = []
    if request.speaker:
        details.append(f"{request.speaker} speaks")
    else:
        details.append("A speaker speaks")
    if request.language:
        details.append(f"in {request.language}")
    details.append(
        "with clear audio, natural pacing, moderate pitch, and a close-sounding high quality recording."
    )
    return " ".join(details)
