# LLM Token Usage Debug

## Goal

Find whether the 2026-05-19 DeepSeek spike came from runtime NPC LLM calls, Codex/pi skill generation, or both. Add an in-game `/llm token` summary so future sessions show request/token/cost totals without checking the provider dashboard first.

## Acceptance Criteria

- Runtime LLM code exposes total requests, token counts, and estimated USD cost.
- `/llm token` prints current in-memory totals and active provider/model pricing assumptions.
- Investigation records local evidence for skill-generated content vs runtime NPC calls.
- Gradle build passes.

## Findings

- DeepSeek dashboard screenshot for 2026-05-19 shows 27,394,006 Flash tokens: 2,785,664 cache-hit input, 19,947,837 cache-miss input, and 4,660,505 output.
- At DeepSeek official 2026-05-19 Flash pricing, that is about $4.10: cache-hit input $0.008, cache-miss input $2.79, output $1.30.
- Runtime mod debug log from 2026-05-19 23:57:45.861 to 2026-05-20 00:10:53.175 shows 36 NPC LLM request log lines, about 280,275 prompt chars, roughly 70,069 input tokens before provider-reported usage.
- Runtime Prism NPC config currently has 59 `micro_interactions*.toml` files with 3,273 exchanges, 308 trainer exchanges, and 250 solo moments.
- `2026-05-19-multiple-class-mentors.md` records 110 pair-specific exchanges generated with `deepseek-v4-pro` and 41 expansion chunks with 2,026 generated tag-pack exchanges.

## Plan

1. Add in-memory LLM usage tracking in `NpcLlmService`.
2. Parse OpenAI-compatible non-streaming `usage`; request and parse streaming usage when available.
3. Estimate usage from prompt/reply chars when provider usage is missing.
4. Add `/llm token` command output.
5. Build.
