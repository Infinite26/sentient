# Sentient — Roadmap / Plan

**LLM-powered autonomous creatures for Minecraft 1.20.1 (NeoForge / Forge 47.x).**

This document is the agreed design and build plan. It starts from the working
v1 chat mod (LLM dialogue + memory + personas + GUI) and grows it into a
creature that **perceives, remembers, navigates, and learns to play — including
with modded content.**

---

## 1. Locked decisions

| Area | Decision |
|---|---|
| **Body** | In-mod Java `SentientBeing` entity (server-side). Reads the pack's live `RecipeManager`. Ships as one mod, works with any modpack. |
| **Voice (TTS)** | **Kokoro-82M** local (Apache-2.0), per-NPC voices, positional audio. Runs as a **local sidecar server** (Python) the mod calls over HTTP. |
| **Learning (v1)** | Voyager-style **skill library + LLM self-repair**. No gradient training in v1. |
| **Brain** | **Provider-agnostic** LLM layer. Remote proxy default, local fallback, adapters for **all** provider types. |
| **Perception** | **Block / ray tracing** — no camera or screenshot vision. The creature "sees" real geometry and real modded blocks. |
| **Surroundings memory** | A **basic spatial memory** of what it has observed, persisted per NPC. |

---

## 2. Architecture

```
                 ┌─────────────── BRAIN (provider-agnostic LLM) ──────────────┐
                 │ planner · tool-calling · recipe/mod RAG · dialogue memory  │
                 └───────────────▲────────────────────────┬───────────────────┘
        structured sight /      │                        │ skill calls
        memory answers          │                        │
                 ┌───────────────┴────────────────────────▼───────────────────┐
                 │                BODY  (Sentient mod, server-side)            │
                 │  PERCEPTION (raycast + voxel scan) ──► SURROUNDINGS MEMORY   │
                 │  skill executor · async A* navigation · inventory            │
                 │  custom entity: SentientBeing (PathfinderMob)               │
                 └────────────────────────┬────────────────────────────────────┘
                                          │ audio packets
                                  ┌───────▼────────┐   ┌──────────────────────┐
                                  │ TTS sidecar    │   │ LEARNED LIMBS (later)│
                                  │ Kokoro-82M     │   │ small BC policy      │
                                  └────────────────┘   └──────────────────────┘
```

**Mental model:** the LLM is the *frontal lobe* (planning, language, tool use);
the skill library + (later) a small policy are the *limbs / muscle memory*;
perception + spatial memory are the *senses and short-term spatial recall*.

---

## 3. Reality check (what "trained from the ground up" can mean)

- **VPT** (the reference) is 250M params, ~70,000 h of video + ~230,000 h of RL,
  on a cluster. **Not runnable on a Ryzen Z1 Extreme iGPU with 9 GB RAM.**
- Achievable learning, honestly:
  1. **In-context learning + skill library** (Voyager-style): agent writes/repairs
     skills, keeps what works, retrieves later. No training compute. Generalises
     to any modpack. **This is v1.**
  2. **Small behavior-cloned motor policy** (a few M params, not VPT): trained
     offline from the agent's own trajectories on CPU/ROCm. Narrow low-level
     motion/aim. **This is the deferred Phase 6.**
- Full autonomy across a modpack comes from perception + memory + skill library,
  **not** from the trained net.

---

## 4. Phases

### Phase 1 — Provider-agnostic brain + Kokoro voice
**Brain (`llm/`)**
- `LlmProvider` interface + adapters:
  - `OpenAiProvider` — OpenAI-compatible (`/chat/completions`): OpenAI, OpenRouter,
    llama.cpp server, LM Studio, vLLM, the local `openai-oauth` proxy.
  - `OllamaProvider` — native Ollama API.
  - `AnthropicProvider` — Messages API.
  - `GeminiProvider` — generateContent API.
- Canonical tool-call format = **OpenAI functions**; adapters translate. For
  tool-less models, fall back to **prompt-based JSON / ReAct**.
- `LlmRouter`: named providers, priority + fallback chain, timeouts, retries.
- Config: `providers[]` (name, type, baseUrl, apiKey, model, supportsTools),
  default provider, fallback order.

**Voice (`tts/`)**
- `tts_server/` sidecar: `kokoro-onnx` + `misaki` phonemiser; HTTP contract
  `POST /tts {text, voice, speed} → audio/ogg` (+ `GET /voices`, `GET /health`).
- Mod-side `TtsClient` (async, cached by `(text,voice)` hash) → `TtsCache`.
- Per-NPC voice assigned from entity type / UUID hash.
- Client playback: custom **positional OpenAL** of server-sent OGG, streamed so
  speech starts before the line ends. Reference: Simple Voice Chat.

**Files:** `llm/{LlmProvider,OpenAiProvider,OllamaProvider,AnthropicProvider,GeminiProvider,LlmRouter,LlmToolCall}.java`,
`tts/{TtsClient,TtsCache,TtsVoice}.java`, `net/PlayVoicePacket.java`,
`client/audio/{PositionalAudio,VoiceManager}.java`, `tts_server/`.

### Phase 2 — The body
- `entity/SentientBeing` extends `PathfinderMob`: inventory, `GroundPathNavigation`
  with a custom node evaluator, async path computation (never blocks the tick).
- `skill/` primitives: `goto`, `look_at`, `mine`, `craft`, `smelt`, `place`,
  `use_item`, `equip`, `attack`, `follow`, `drop`.
- Task executor runs primitives off the server thread; reports success/failure.
- Spawn/convert command + persistence + entity renderer/model.

**Files:** `entity/{SentientBeing,SentientBeingRenderer}.java`, `entity/ai/*`,
`skill/{Skill,SkillContext,SkillResult,primitives/*}.java`.

### Phase 3 — Perception by block/ray tracing
- `perception/RayCaster`: grid of rays across the FOV (e.g. 8×6) from eye height
  → block hit, position, face, distance, entity hits; honours modded shapes.
- `perception/VoxelScanner`: sample blocks in radius/cone → occupancy + material
  map; flag salient things (ores, machines, chests, water/lava, doors, tagged
  modded blocks).
- `perception/Sight`: compact **symbolic view** for the LLM — depth-quantised
  ASCII map (near/far) + ranked visible blocks/entities with distances +
  line-of-sight (`can_see`), so only unoccluded surfaces are "seen".
- LLM tools: `look()`, `raycast(direction)`, `scan_blocks(radius, filter)`,
  `can_see(target)`, `find_nearest(block|entity)`, `describe_view()`.

**Files:** `perception/{RayCaster,VoxelScanner,Sight,ViewEncoder}.java`.

### Phase 3b — Surroundings memory (spatial)
- `memory/SpatialMemory` per `SentientBeing`, persisted in world SavedData:
  - **Landmarks** — chests, crafting tables, furnaces, beds, doors, modded
    machines, water/lava, ore veins, cave mouths, spawners: position, kind,
    first/last seen, confidence.
  - **Terrain** — coarse sparse voxel map (occupancy + dominant material) for
    explored regions.
  - **Trail** — recent path history / known vantage points.
  - **Decay & refresh** — entries age and lose confidence; re-observation
    refreshes; LRU caps keep it bounded per NPC.
- Perception **writes**; planner/skills **read**.
- Tools: `where_is(block|item)`, `known_landmarks(kind)`, `nearest_known(kind)`,
  `have_i_seen(pos)`, `map_summary()`.
- Navigation can target remembered things without a fresh full scan.

**Files:** `memory/{SpatialMemory,Landmark}.java`, `memory/SpatialMemoryData.java`.

### Phase 4 — Recipe / mod knowledge
- `knowledge/`: on load, index every item/block/tag (incl. modded) and every
  recipe from the live `RecipeManager` (shaped/shapeless/smelting/custom modded
  types) into **SQLite FTS5**.
- Tools: `search_items`, `get_recipe`, `plan_craft(item, qty)` (recursive
  prerequisite tree), `find_block`, `list_mods`, `get_inventory`.

**Files:** `knowledge/{RecipeIndex,ItemIndex,ToolRegistry}.java`.

### Phase 5 — Learning loop (LLM supervises itself)
- `skill/Library`: skills = parameterised action programs, indexed + retrieved by
  similarity. Success → stored; failure → LLM reads the error and repairs it
  (Voyager iterative prompting).
- Trajectory log `(state → action → result)` written to disk for future training.
- Automatic curriculum: propose next goal from current state.

**Files:** `skill/Library.java`, `learn/{TrajectoryLogger,Critic,Curriculum}.java`.

### Phase 6 — Learned limbs (deferred)
- Offline PyTorch behavior-cloning pipeline from Phase-5 trajectories (optionally
  + MineRL data) → ONNX → localhost inference; the mod uses it for low-level
  motion/aim when present, falls back to the deterministic executor otherwise.
- Optional later: LoRA fine-tune of a small local brain for planning.

---

## 5. Configuration sketch

```jsonc
{
  "llm": {
    "default": "proxy",
    "fallback": ["local"],
    "providers": [
      { "name": "proxy", "type": "openai", "baseUrl": "http://127.0.0.1:10531/v1", "model": "...", "supportsTools": true },
      { "name": "local", "type": "ollama", "baseUrl": "http://127.0.0.1:11434", "model": "qwen3:4b", "supportsTools": true }
    ]
  },
  "tts": {
    "endpoint": "http://127.0.0.1:8770",
    "enabled": true,
    "volume": 1.0,
    "voiceOverrides": { "minecraft:villager": "af_heart" }
  },
  "perception": { "fovRaysX": 8, "fovRaysY": 6, "scanRadius": 24 },
  "memory": { "maxLandmarks": 512, "landmarkDecayTicks": 24000, "terrainCellSize": 4 }
}
```

---

## 6. New / changed files (overview)

```
src/main/java/dev/sentient/
  llm/        LlmProvider, OpenAiProvider, OllamaProvider, AnthropicProvider,
              GeminiProvider, LlmRouter, LlmToolCall
  tts/        TtsClient, TtsCache, TtsVoice
  net/        PlayVoicePacket (+ existing chat packets)
  client/audio/  PositionalAudio, VoiceManager
  entity/     SentientBeing, SentientBeingRenderer, ai/*
  skill/      Skill, SkillContext, SkillResult, primitives/*, Library
  perception/ RayCaster, VoxelScanner, Sight, ViewEncoder
  memory/     SpatialMemory, Landmark, SpatialMemoryData (+ existing NpcMemory)
  knowledge/  RecipeIndex, ItemIndex, ToolRegistry
  learn/      TrajectoryLogger, Critic, Curriculum
tts_server/   Kokoro sidecar (Python)
```

---

## 7. Risks / mitigations

| Risk | Mitigation |
|---|---|
| Java positional OGG playback is fiddly | Follow Simple Voice Chat's OpenAL approach |
| Local 4B planner is weak at tool-calling | Fallback chain to a stronger provider; ReAct fallback |
| Async pathfinding could stall the server | Compute off-thread; apply results on the server thread |
| Spatial memory unbounded growth | LRU caps + time decay + refresh on re-observation |
| Modded recipe types vary wildly | Read `RecipeManager` generically; per-type adapters |
| TTS latency | Streaming; cache by text+voice; short replies |

---

## 8. Monetisation (unchanged)

Selling mods violates Mojang's Commercial Guidelines. Legit routes: **crypto
donations/tips** (XMR/BTC addresses already wired), Patreon-style early access,
commissions, or the official Minecraft Marketplace.

- **XMR:** `455Vxrh9bXR3L8KpSrfJDyT7E2KcAQkKRZPLtW1XWNfeh4FBf8R2eix6vYRdmDhJTcfnp8HQNaRAdAxUM2ecL2mwABKmjYf`
- **BTC:** `bc1qrg96a55wgjwl6tv0k9xr9flfq5zqwl6lywxh74`

---

## 9. Status

- **v1 (done):** chat mod — LLM dialogue, persistent per-NPC memory, personas,
  chat GUI, Whisper Stone item, keybind, commands, server-side-only API key.
- **Phase 1 (done, verified 2026-09-23):**
  - Provider-agnostic brain: `LlmProvider` + OpenAI / Ollama / Anthropic / Gemini
    adapters + `LlmRouter` (ordered fallback). Verified end-to-end against a mock
    OpenAI endpoint (`POST /v1/chat/completions` with the correct body).
  - Kokoro-82M TTS sidecar (`tts_server/`, Apache-2.0, 54 voices, RTF ~0.28 on CPU)
    with a `POST /tts → audio/wav` contract; synthesis verified.
  - Mod TTS client + `PlayVoicePacket` + client playback with distance attenuation
    and stereo panning; wired into NPC replies. Builds clean.
- **Next:** Phase 2 (the body: `SentientBeing` + async pathfinding + skills).
