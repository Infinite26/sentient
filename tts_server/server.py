#!/usr/bin/env python3
"""Kokoro TTS sidecar for the Sentient Minecraft mod.

HTTP contract (used by dev.sentient.tts.TtsClient):
    GET  /health                 -> {"ok":true,"engine":"kokoro","sampleRate":24000,"voices":54}
    GET  /voices                 -> {"voices":["af_heart", ...]}
    POST /tts   {"text","voice","speed","lang","format":"wav"}
                                 -> audio/wav bytes (or audio/ogg if format=ogg)
    POST /tts/speak  (same, convenience)

No cloud, no API key. CPU-friendly (RTF ~0.3 on a Ryzen Z1 Extreme).
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import sys
import threading
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np

# ---------------------------------------------------------------------------

DEFAULT_VOICE = "af_heart"
SAMPLE_RATE = 24000

_LANG_BY_PREFIX = {
    "a": "en-us", "b": "en-gb", "j": "ja", "z": "zh",
    "e": "es", "f": "fr", "h": "hi", "i": "it", "p": "pt-br",
}

_kokoro = None
_lock = threading.Lock()
_cache_dir = None
_voice_cache = None


def lang_for_voice(voice: str) -> str:
    return _LANG_BY_PREFIX.get((voice or "")[:1].lower(), "en-us")


def to_wav_bytes(samples, sr: int) -> bytes:
    pcm = (np.clip(np.asarray(samples, dtype=np.float32), -1.0, 1.0) * 32767.0).astype("<i2")
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(int(sr))
        w.writeframes(pcm.tobytes())
    return buf.getvalue()


def to_ogg_bytes(samples, sr: int) -> bytes:
    import subprocess
    import tempfile
    pcm = (np.clip(np.asarray(samples, dtype=np.float32), -1.0, 1.0) * 32767.0).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as wf:
        with wave.open(wf.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(int(sr))
            w.writeframes(pcm.tobytes())
        src = wf.name
    dst = src + ".ogg"
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-c:a", "libvorbis", "-q:a", "4", dst],
                       check=True)
        with open(dst, "rb") as f:
            return f.read()
    finally:
        for p in (src, dst):
            try:
                os.remove(p)
            except OSError:
                pass


def synthesize(text: str, voice: str, speed: float, lang: str | None, fmt: str) -> bytes:
    global _kokoro
    if _kokoro is None:
        raise RuntimeError("model not loaded")
    voice = voice or DEFAULT_VOICE
    lang = lang or lang_for_voice(voice)

    key = hashlib.sha1(f"{text}|{voice}|{speed:.3f}|{lang}|{fmt}".encode("utf-8")).hexdigest()
    if _cache_dir:
        path = os.path.join(_cache_dir, key + "." + fmt)
        if os.path.exists(path):
            with open(path, "rb") as f:
                return f.read()

    with _lock:  # onnx session is reused; serialize for safety
        samples, sr = _kokoro.create(text, voice=voice, speed=float(speed), lang=lang)

    data = to_ogg_bytes(samples, sr) if fmt == "ogg" else to_wav_bytes(samples, sr)
    if _cache_dir:
        with open(os.path.join(_cache_dir, key + "." + fmt), "wb") as f:
            f.write(data)
    return data


# ---------------------------------------------------------------------------

class Handler(BaseHTTPRequestHandler):
    server_version = "SentientTTS/1.0"

    def log_message(self, fmt, *args):  # quieter logs
        sys.stderr.write("[tts] " + (fmt % args) + "\n")

    def _send(self, code: int, ctype: str, body: bytes, extra: dict | None = None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, obj: dict):
        self._send(code, "application/json", json.dumps(obj).encode("utf-8"))

    def do_GET(self):
        if self.path in ("/health", "/"):
            self._json(200, {"ok": True, "engine": "kokoro",
                             "sampleRate": SAMPLE_RATE,
                             "voices": len(_voice_cache or [])})
        elif self.path == "/voices":
            self._json(200, {"voices": list(_voice_cache or [])})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path not in ("/tts", "/tts/speak"):
            self._json(404, {"error": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length) or b"{}")
        except Exception as e:
            self._json(400, {"error": f"bad json: {e}"})
            return

        text = (payload.get("text") or "").strip()
        if not text:
            self._json(400, {"error": "empty text"})
            return
        text = re.sub(r"\s+", " ", text)[:2000]
        voice = payload.get("voice") or DEFAULT_VOICE
        speed = float(payload.get("speed") or 1.0)
        lang = payload.get("lang")
        fmt = (payload.get("format") or "wav").lower()
        if fmt not in ("wav", "ogg"):
            fmt = "wav"

        try:
            t0 = time.time()
            data = synthesize(text, voice, speed, lang, fmt)
            dt = time.time() - t0
        except Exception as e:
            self._json(500, {"error": f"synthesis failed: {e}"})
            return

        self._send(200, "audio/ogg" if fmt == "ogg" else "audio/wav", data,
                   {"X-Synth-Seconds": f"{dt:.3f}", "Cache-Control": "no-store"})


# ---------------------------------------------------------------------------

def main():
    global _kokoro, _cache_dir, _voice_cache
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8770)
    ap.add_argument("--model", default=os.path.join(os.path.dirname(__file__), "models", "kokoro-v1.0.onnx"))
    ap.add_argument("--voices", default=os.path.join(os.path.dirname(__file__), "models", "voices-v1.0.bin"))
    ap.add_argument("--cache", default=os.path.join(os.path.dirname(__file__), "cache"))
    args = ap.parse_args()

    os.makedirs(args.cache, exist_ok=True)
    _cache_dir = args.cache

    from kokoro_onnx import Kokoro
    print(f"[tts] loading model {args.model}", flush=True)
    _kokoro = Kokoro(args.model, args.voices)
    try:
        _voice_cache = sorted(_kokoro.get_voices())
    except Exception:
        _voice_cache = []
    print(f"[tts] ready on http://{args.host}:{args.port} ({len(_voice_cache)} voices)", flush=True)

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
