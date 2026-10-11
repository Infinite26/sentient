#!/usr/bin/env bash
# Start the Kokoro TTS sidecar for the Sentient mod.
# First run: create the venv and fetch the model (see setup.sh).
set -euo pipefail
cd "$(dirname "$0")"
exec ./.venv/bin/python server.py "$@"
