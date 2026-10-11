#!/usr/bin/env bash
# One-time setup: venv + Kokoro ONNX model files.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p models

uv venv .venv --python 3.11
uv pip install --python ./.venv/bin/python kokoro-onnx soundfile

if [ ! -f models/kokoro-v1.0.onnx ]; then
  curl -L -o models/kokoro-v1.0.onnx \
    https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.onnx
fi
if [ ! -f models/voices-v1.0.bin ]; then
  curl -L -o models/voices-v1.0.bin \
    https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin
fi

echo "Done. Start with ./run.sh"
