# Sentient

**LLM-powered NPCs for Minecraft 1.20.1 (NeoForge / Forge 47.x).**
Talk to *any* creature. It answers in character, remembers what you said, and has
its own personality — driven by a local or remote OpenAI-compatible model.

All model traffic happens server-side. Clients never see your API key.

## Features

- **Talk to any living entity** — right-click it with a *Whisper Stone*, or aim at it and press **B**.
- **Persistent memory** — every creature keeps its conversation history, stored per-world (`sentient_memory`), and remembers you across sessions.
- **Personas** — 12 built-in personas (villager, iron golem, wolf, cat, zombie, creeper, enderman, allay, piglin, dragon, ...) plus your own for any entity type.
- **Context-aware** — the prompt includes the creature's name, the biome, day/night and dimension, and who is speaking.
- **In-world chat UI** — a scrollable chat screen with your history and the NPC's replies; nearby players hear the replies too.
- **Works with anything OpenAI-compatible** — Ollama, llama.cpp server, LM Studio, vLLM, OpenAI, OpenRouter, or your own gateway.
- **Admin commands** — reload config, switch models, test the endpoint, inspect/clear memory.
- **Safe by design** — calls are async (never blocks the server tick), rate-limited per player, range-checked, output sanitised and length-capped.

## Requirements

- Minecraft 1.20.1 with NeoForge/Forge 47.x
- An OpenAI-compatible `/chat/completions` endpoint (local or remote)

## Install

1. Drop `sentient-1.0.0.jar` into your `mods/` folder (client **and** server — the mod is needed on both sides for the chat UI).
2. Start the game/server once to generate `config/sentient.json`.
3. Point it at your model (see below) and run `/sentient reload`.

## Configuration — `config/sentient.json`

```json
{
  "providers": [
    { "name": "proxy", "type": "openai",  "baseUrl": "http://127.0.0.1:10531/v1", "model": "gpt-4o-mini", "supportsTools": true },
    { "name": "local", "type": "ollama",  "baseUrl": "http://127.0.0.1:11434",    "model": "qwen3:4b",   "supportsTools": true }
  ],
  "temperature": 0.8,
  "maxTokens": 256,
  "timeoutSeconds": 30,
  "contextMessages": 12,
  "maxMemoryMessages": 40,
  "cooldownTicks": 40,
  "interactionRange": 8.0,
  "broadcastToNearby": true,
  "broadcastRadius": 24.0,
  "logConversations": true,
  "ttsEnabled": true,
  "ttsEndpoint": "http://127.0.0.1:8770",
  "ttsVolume": 1.0,
  "ttsSpeed": 1.0,
  "ttsVoiceOverrides": { "minecraft:villager": "af_heart" },
  "systemPrompt": "You are {name}, ...",
  "personas": { "minecraft:villager": "a pragmatic village merchant ..." }
}
```

### LLM providers (provider-agnostic)

Providers are tried **in order with automatic fallback**. `type` is one of:

| type | API | baseUrl example |
|---|---|---|
| `openai` | OpenAI-compatible `/chat/completions` — OpenAI, OpenRouter, llama.cpp, LM Studio, vLLM, your proxy | `http://127.0.0.1:10531/v1` |
| `ollama` | native Ollama | `http://127.0.0.1:11434` |
| `anthropic` | Messages API | `https://api.anthropic.com` |
| `gemini` | generateContent | `https://generativelanguage.googleapis.com` |

If `providers` is empty, one OpenAI-compatible provider is derived from the legacy
`endpoint`/`model`/`apiKey` fields.

`systemPrompt` placeholders: `{name}` `{persona}` `{player}` `{biome}` `{time}` `{dimension}`.

### Voice (TTS)

Speech is synthesised by a local **Kokoro-82M** sidecar (Apache-2.0) and streamed to
nearby clients, played with distance attenuation + stereo panning.

```bash
cd tts_server
./setup.sh     # one-time: venv + model (~340 MB)
./run.sh       # serves http://127.0.0.1:8770
```

Per-NPC voices are chosen by config override (`ttsVoiceOverrides`) or stably by UUID.
`/sentient status` shows the active providers and TTS endpoint. (v1 playback uses the
JVM audio path; OpenAL positional playback is a later upgrade.)

## Commands

| Command | Perm | What |
|---|---|---|
| `/sentient status` | 0 | Show model, endpoint, NPC count |
| `/sentient ask <entity> <message>` | 0 | Talk to a creature from chat/console |
| `/sentient test` | 2 | Ping the LLM endpoint |
| `/sentient reload` | 2 | Reload `sentient.json` |
| `/sentient model <name>` | 2 | Switch model at runtime |
| `/sentient forget <entity>` | 2 | Wipe one creature's memory |
| `/sentient forgetall` | 2 | Wipe all memory |

## Build

```bash
./gradlew build        # -> build/libs/sentient-1.0.0.jar
```

Requires a JDK. See `gradle.properties` for the toolchain configuration used here
(JDK 21 targeting Java 17 bytecode, since no JDK 17 was installed).

## Roadmap (v2 ideas)

- Relationship / trust values that shift with how you treat each NPC
- NPC-initiated greetings and idle chatter
- Memory summarisation so long relationships stay cheap
- Optional TTS so NPCs speak aloud (set `ttsEndpoint`)
- Voice input

## Support

This mod is free to use. If it earns a place in your world, tips are welcome:

- **XMR:** `455Vxrh9bXR3L8KpSrfJDyT7E2KcAQkKRZPLtW1XWNfeh4FBf8R2eix6vYRdmDhJTcfnp8HQNaRAdAxUM2ecL2mwABKmjYf`
- **BTC:** `bc1qrg96a55wgjwl6tv0k9xr9flfq5zqwl6lywxh74`

## License

All Rights Reserved. © Infinite.
