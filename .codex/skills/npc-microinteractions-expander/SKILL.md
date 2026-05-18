---
name: npc-microinteractions-expander
description: Expand existing CKDM NPC micro interaction packs when more variety is needed. Use when the user says to add more NPC microinteractions, multiply dialogue, reduce repeats, add more reusable packs, expand trainer dialogue, create themed micro_interactions*.toml chunks with pi/deepseek-v4-flash, or validate and merge generated NPC-to-NPC exchange content.
---

# NPC Microinteractions Expander

Use to add volume to existing micro interaction content without changing runtime logic.

## Workflow

1. Read `docs/MICRO_INTERACTIONS_GUIDE_FOR_FUTURE_NPCS.md`.
2. Inspect existing `micro_interactions*.toml` files in the Prism runtime NPC config.
3. Run coverage summary:
   ```powershell
   python .codex/skills/npc-microinteractions-expander/scripts/analyze_micro_interactions.py --npc-dir "<runtime-npcs-dir>" --summary
   ```
4. Choose expansion target:
   - `--topic <topic>` for weak themes.
   - `--focus <npc_id>` for thin NPC coverage.
   - trainer pack when `trainer_exchanges` count is low.
   - tag pack when pair permutations would be too large.
5. Ask `pi` to create a new chunk file, not overwrite existing reviewed chunks.
6. Sanitize generated output, then validate all chunks.
7. Run `.\gradlew.bat build`; run `/npc reload` for runtime smoke.

## Pi Command

Use DeepSeek V4 Flash exactly:

```powershell
pi --model deepseek/deepseek-v4-flash --no-tools --no-session -p "@prompt.md" | Set-Content -LiteralPath "<runtime-npcs-dir>\micro_interactions_<theme>_<nn>.toml" -Encoding ASCII
```

Do not hand-author bulk dialogue unless the user explicitly asks. Let `pi` generate the exchange text, then integrate and validate.

## Expansion Prompt Minimum

Tell `pi`:

- Generate valid TOML only.
- No Markdown fences, no comments, ASCII only.
- Output exactly N `[[exchanges]]` or `[[trainer_exchanges]]` entries.
- No romance, dating, grief, modern slang, or deleted NPCs.
- Each `line` and `response` <= 95 chars.
- IDs must start with the chunk slug and be unique.
- Keep exact shape:
  `id`, `topic`, optional ids/tags, `line`, `response`, `weight`.

## Validation

Run:

```powershell
python .codex/skills/npc-microinteractions-expander/scripts/analyze_micro_interactions.py --npc-dir "<runtime-npcs-dir>" --validate
```

Fix or delete invalid generated blocks. Validation must pass before final handoff.

## Expansion Strategy

- Add new chunk files instead of rewriting old packs.
- Use tag packs for scale: `town`, `mentor`, `pokemon_trainer`, `gym_leader`, `elite_four`, `rival`, `professor`, class tags.
- Use pair-specific packs only for relationships players notice.
- Keep trainers reusable unless the user asks for trainer-specific dialogue.
- Keep deleted NPCs out. Marin is currently deleted.
