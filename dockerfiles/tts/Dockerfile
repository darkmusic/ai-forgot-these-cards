FROM python:3.12-slim

WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

COPY services/tts/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu \
    torch==2.6.0+cpu \
    torchaudio==2.6.0+cpu \
    && pip install --no-cache-dir -r /app/requirements.txt

COPY services/tts/app.py /app/app.py

ENV HF_HOME=/models/huggingface
EXPOSE 8091
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8091"]
