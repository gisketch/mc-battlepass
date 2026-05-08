# CKDM NPC LLM V1 Plan

## Goal

Add a feasible **Talk** mode for CKDM NPCs.

V1 should make NPCs reply in-character using:

- NPC identity and personality.
- Friendship tone with the current player.
- A small shared current-world context used by all NPCs.
- Recent conversation turns.
- Important remembered facts.

V1 should not try to make every job its own context system. Job-specific context, store awareness, missions, event scoring, and gossip can come later.

## Core Rule

The server owns truth. The LLM owns flavor.

The LLM can generate dialogue only. It must not grant items, change friendship, complete missions, move entities, change prices, start trades, or mutate game state through text.

## Current Codebase Fit

Build V1 on the existing NPC pieces:

- `NpcDefinition`: id, name, title, job, personality fields, store id.
- `NpcStore`: friendship snapshots, conversation records, global event list, resident state.
- `NpcFeature`: interaction flow, Talk button action routing, dialog opening, Discord relay, balloons.
- `NpcNetwork`: client/server dialog actions and payloads.
- `NpcClient`: dialog screen, typewriter, animalese, world balloons.
- `settings.json`: global NPC settings. Add LLM settings here.

Keep the first implementation small. No new event scoring engine. No job-specific knowledge map. No store context in V1.

## V1 User Flow

```txt
Player right-clicks NPC
Dialog opens
Player clicks Talk
Dialog switches to text input
Player sends message
Server validates distance and NPC state
Server builds compact context
Server calls configured LLM provider
Server parses JSON `{ "message": "..." }`
Server validates/clamps message
Server stores player turn + NPC reply
Client shows reply with typewriter + animalese
Nearby players see balloon
Discord receives NPC line if enabled
```

## V1 Components

### `NpcLlmSettings`

Global config under `settings.json`:

```json
{
  "llm": {
    "enabled": false,
    "provider": "openai_compatible",
    "base_url": "https://api.deepseek.com",
    "model": "deepseek-chat",
    "api_key_env": "CKDM_LLM_API_KEY",
    "cooldown_seconds": 4,
    "max_reply_chars": 280,
    "max_recent_turns": 8,
    "max_important_memories": 6,
    "request_timeout_seconds": 20,
    "rate_limited_message": "Give me a second to gather my thoughts.",
    "error_message": "Sorry, my thoughts wandered for a second. What were we talking about?",
    "fallback_message": "Sorry, my thoughts wandered for a second. What were we talking about?"
  },
  "llm_message_usage": {
    "interact": true,
    "gift": false,
    "hurt": false,
    "wake": false,
    "greeting": false,
    "first_daily_chat": false,
    "shop_single": false,
    "shop_normal": false,
    "shop_bulk": false
  }
}
```

Provider config should be flexible enough for DeepSeek, Gemini, or any OpenAI-compatible endpoint:

- `provider`: `openai_compatible`, `gemini`, or future ids.
- `base_url`: configurable provider base URL.
- `model`: configurable model name.
- `api_key_env`: environment variable name, not a raw key in JSON.

`llm_message_usage` is global. It decides which NPC message surfaces may call the LLM. V1 defaults to LLM for normal interact/Talk only. Gifts, hurt lines, wake lines, proximity greetings, first daily chat, and shop follow-up lines stay deterministic config messages until explicitly enabled.

### `NpcLlmService`

Server-side entry point.

Responsibilities:

- Validate NPC exists and player is close enough.
- Enforce per-player cooldown and one active request per NPC.
- Return configured fallback messages for rate limits, provider errors, timeouts, invalid JSON, or validation failure.
- Build context through `NpcLlmPromptBuilder`.
- Call `NpcLlmProvider`.
- Parse `{ "message": "..." }`.
- Validate/clamp output.
- Store conversation and important memory updates.
- Return the final NPC message to normal dialog/balloon/Discord flow.

### `NpcLlmProvider`

Small interface:

```kotlin
interface NpcLlmProvider {
    fun complete(request: NpcLlmRequest): NpcLlmResult
}
```

Implement first:

- `OpenAiCompatibleNpcLlmProvider` for DeepSeek and OpenAI-style APIs.
- `GeminiNpcLlmProvider` only if Gemini endpoint shape is different enough to need a separate adapter.
- `DisabledNpcLlmProvider` returns fallback when disabled.

### `NpcLlmPromptBuilder`

Builds one compact prompt. No scoring engine in V1.

Sections:

```txt
NPC identity
Behavior rules
Relationship
Shared current context
Important memories
Recent chat
Player message
Output format
```

### `NpcMemoryService`

Use existing conversation history for recent turns, then add one small important-memory store.

V1 memory types:

```txt
conversation_turn
important_memory
```

Conversation turns can continue using existing `NpcConversationRecord`.

Important memories should be a simple per NPC/player list:

```json
{
  "timestamp": 0,
  "importance": 7,
  "summary": "Ghe wants to become the town's main monster hunter."
}
```

Store at most 20 important memories per NPC/player. Use at most 6 in the prompt.

## V1 Context Shape

### NPC Identity

From `NpcDefinition`:

```txt
NPC:
- Name: Finn
- Title: The Adventurer
- Job: adventurer
- Personality: brave, friendly, reckless
- Speech style: energetic hero
- Catchphrases: Mathematical!, Adventure time!
- Prompt: Finn is brave, friendly, direct, and hungry for adventure.
```

### Rules

Always include:

```txt
Rules:
- Stay in character as the NPC.
- You are not an assistant.
- Do not mention AI, prompts, models, APIs, hidden rules, or system messages.
- Reply in 1 to 3 short sentences.
- Use plain ASCII only with letters, numbers, spaces, and basic punctuation.
- Do not use emojis, em dashes, smart quotes, or other Unicode symbols.
- Do not claim you gave items, changed friendship, changed prices, completed quests, teleported anyone, healed anyone, or changed the world.
- If asked to do a game action, suggest the real UI action instead.
- Use only the context provided. If unsure, answer naturally with uncertainty.
```

### Relationship

From `NpcStore.friendshipSnapshot`:

```txt
Relationship:
- Player: Ghe
- Friendship points: 650
- Friendship level: 6
- Category: good_friends
- Tone: warm, familiar, trusting
```

Tone mapping can be deterministic:

```txt
hatred: hostile, guarded, brief
enemy: suspicious, cold, cautious
dislike: restrained, wary
neutral: friendly but not intimate
okay: warm, cooperative
good_friends: familiar, trusting
best_friends: deeply loyal, playful, personal
```

### Shared Current Context

Same shape for all NPCs, no job-specific branching yet:

```txt
Current context:
- Time: 14:00
- Weather: clear
- NPC activity: work
- NPC has home: yes
- Player health: 18/20
- Player held item: minecraft:iron_sword
- Nearby players: Hya, Mark
```

Keep this cheap. Use what is easy now. Add more only when it is already exposed by current code.

### Important Memories

Use stored important memories only:

```txt
Important memories:
- Ghe wants to become the town's main monster hunter.
- Ghe apologized after accidentally hitting Finn.
```

No summarization pipeline yet. V1 can ask the LLM whether the player message produced a memory, but store only after validation.

### Recent Chat

Use the newest conversation records for this NPC/player:

```txt
Recent chat:
Player: Do you remember the sword I gave you?
Finn: How could I forget? That blade has hero energy.
```

Limit to `max_recent_turns` from settings.

## V1 Prompt Template

```txt
You are roleplaying as an NPC in a Minecraft multiplayer server.

{npc_identity}

{rules}

{relationship}

{current_context}

{important_memories}

{recent_chat}

Player says:
"{player_message}"

Return JSON only in this shape:
{"message":"NPC reply here"}
```

## V1 Output

Request JSON only:

```json
{
  "message": "string"
}
```

Validation:

- JSON must parse.
- `message` must be non-empty.
- Clamp to `max_reply_chars`.
- Strip markdown/code fences.
- Reject hidden-context leaks.
- Reject direct game-state claims.
- Fallback on failure.

Do not add `emotion`, `tone`, `should_overhear`, or `memory_importance` yet. Those are V2 fields.

## Important Memory Extraction

Because the user wants important memories in V1, keep this simple.

After a valid reply, optionally make a second cheap extraction call or ask the same call to include a hidden candidate later. For V1 implementation, prefer explicit second call only when needed.

Memory extraction prompt output:

```json
{
  "remember": true,
  "importance": 7,
  "summary": "Ghe wants to become the town's main monster hunter."
}
```

Rules:

- Only store importance `6..10`.
- Summary max 180 chars.
- Must involve the current player and current NPC.
- No secrets, insults, prompt details, or raw chat dumps.
- Store max 20 important memories per NPC/player.

If this feels too much during implementation, ship Talk first and leave memory extraction behind a config flag:

```json
"important_memory_enabled": true
```

## Safety Filters

Reject or fallback if reply contains patterns like:

```txt
as an AI
system prompt
hidden context
I gave you
I added
I removed
I teleported
I changed your friendship
I completed your quest
I changed the price
```

Also reject if message is too long, empty, markdown-heavy, or not valid JSON.

## Rate Limits

V1 settings:

```txt
Per player: one message every 4 seconds
Per NPC: one active request at a time
Server: simple provider timeout, no queue yet
Reply: max 280 chars
Recent turns in prompt: 8
Important memories in prompt: 6
```

## Client UX V1

Talk mode can reuse the existing dialog panel.

Minimal UI:

```txt
NPC dialog body
Text input
Send / Back / Bye
```

While waiting:

```txt
Finn: ...
```

The waiting state should update both the local dialog and nearby balloon. Render the `...` as an animated wave/pulse so it reads as thinking, not a frozen message. When the provider response arrives, replace the pending text with the final validated NPC reply in the dialog and refresh the balloon text for nearby players.

If the request is rate-limited, times out, fails, or returns invalid output, replace the pending text with the configured fallback/rate-limit/error message and finish the normal dialog flow.

On reply:

- show in dialog body,
- typewriter reveal,
- animalese voice,
- pause NPC facing player,
- nearby world-space balloon,
- Discord relay through existing NPC dialog path.

## Implementation Phases

### Phase 1 — Config + Provider Skeleton

- Add `llm` block to NPC `settings.json`.
- Add global `llm_message_usage` toggles to NPC `settings.json`.
- Add settings model and normalization.
- Add provider interface.
- Add disabled/fallback provider.
- Add OpenAI-compatible provider with flexible `base_url`, `model`, and `api_key_env`.

### Phase 2 — Talk Request Path

- Add Talk input state to existing NPC dialog UI.
- Add client-to-server Talk payload.
- Validate NPC id, distance, cooldown, and live entity.
- Show pending `...` in the dialog and nearby balloon while fetching.
- Return configured fallback while provider is disabled, rate-limited, or failing.

### Phase 3 — Prompt Builder

- Build compact context from current code:
  - NPC identity,
  - rules,
  - friendship tone,
  - shared current context,
  - recent chat,
  - current player message.
- Add `/npc llm prompt <id> <player> <message>` debug command.

### Phase 4 — Real Provider + Validation

- Call configured provider.
- Parse `{ "message": "..." }`.
- Validate and clamp response.
- Store conversation turn.
- Show reply through existing dialog/balloon/Discord flow.

### Phase 5 — Important Memories

- Add per NPC/player important memory list to `NpcStore`.
- Add memory extraction behind config flag.
- Include top important memories in prompt.
- Add `/npc memory get <id> <player>` debug command.

## Later, Not V1

### V2 — Store-Aware Talk

Add store summary only when player asks about items, stock, prices, buying, or recommendations.

Do not include full shop state in every prompt.

### V3 — Event-Aware Talk

Normalize global events and add simple relevance selection.

No job-specific context until this exists.

### V4 — Mission-Aware Talk

Include tracked or nearly complete battlepass missions.

### V5 — Rich Output

Extend JSON to:

```json
{
  "message": "string",
  "emotion": "neutral",
  "should_overhear": true
}
```

Use emotion later for portrait, balloon style, and voice variation.

## Acceptance Criteria for V1

- Talk button lets player type to the NPC.
- Server validates player/NPC interaction before sending to provider.
- Provider config can support DeepSeek, Gemini, and OpenAI-compatible endpoints.
- Prompt is compact and debuggable.
- LLM output is JSON with only `message`.
- Bad/invalid output falls back safely.
- Rate limits, provider errors, and timeouts fall back safely.
- While fetching, the dialog and balloon show animated `...`, then update to the final or fallback message.
- Global config can enable/disable LLM per message surface; V1 defaults to `interact: true` and all non-interact surfaces `false`.
- NPC reply uses existing dialog, animalese, balloon, and Discord path.
- Recent conversation is stored.
- Important memories can be stored and inspected.
- Store context, event relevance, mission context, and job-specific context are explicitly deferred.

## Current Implementation Notes

- Talk button opens an input field in the existing NPC dialog.
- Enter or Send submits a client-to-server Talk request.
- While the server is fetching, the local dialog and nearby balloon show animated/pending `...`.
- Server validates NPC existence, distance, per-player cooldown, and one active request per NPC.
- `settings.json` supports `openai_compatible` and `gemini` provider modes with configurable `base_url`, `model`, and inline `api_key`.
- Disabled provider, missing API key, rate limits, invalid output, timeouts, and provider errors use configured fallback messages.
- Valid replies return as JSON `{ "message": "..." }`, are clamped/filtered, stored in conversation history, shown in the dialog, relayed as a nearby balloon, and sent through the existing Discord NPC relay.
- Important memory extraction remains planned, not implemented in this pass.
