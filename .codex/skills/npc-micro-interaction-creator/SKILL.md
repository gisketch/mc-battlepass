---
name: npc-micro-interaction-creator
description: Create CKDM NPC-to-NPC micro interaction content for a new or newly promoted NPC. Use when adding a new NPC, removing stale NPC dialogue such as deleted NPCs, checking which current NPC pair combinations lack authored exchanges, building pi/deepseek-v4-flash prompts for new exchange TOML, validating generated micro_interactions*.toml files, or updating future NPC micro interaction coverage.
---

# NPC Micro Interaction Creator

Use for new NPC content. Keep runtime code shape exact and let `pi` generate authored dialogue.

## Workflow

1. Read `docs/MICRO_INTERACTIONS_GUIDE_FOR_FUTURE_NPCS.md`.
2. Inspect runtime NPC config, not repo `runs/`:
   `C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod\npcs`
3. Run coverage:
   ```powershell
   python .codex/skills/npc-micro-interaction-creator/scripts/analyze_micro_interactions.py --npc-dir "<runtime-npcs-dir>" --focus <npc_id>
   ```
4. Build a small targeted prompt for `pi`, using missing pairs and NPC lore from the NPC TOML.
5. Generate a new file named `micro_interactions_<npc_id>.toml` or `micro_interactions_<theme>.toml`.
6. Sanitize and validate the generated TOML with the same script.
7. Run `.\gradlew.bat build`; run `/npc reload` in game for runtime check.

## Pi Command

Use DeepSeek V4 Flash exactly:

```powershell
pi --model deepseek/deepseek-v4-flash --no-tools --no-session -p "@prompt.md" | Set-Content -LiteralPath "<runtime-npcs-dir>\micro_interactions_<slug>.toml" -Encoding ASCII
```

If `pi` needs file reads/writes itself, use:

```powershell
pi --model deepseek/deepseek-v4-flash --tools read,write,ls --no-session -p "@prompt.md"
```

## Required TOML Shape

```toml
[[exchanges]]
id = "unique_id"
topic = "topic_id"
source_ids = ["npc_id"]
target_ids = ["other_npc_id"]
source_tags = ["optional_tag"]
target_tags = ["optional_tag"]
line = "short first NPC balloon"
response = "short second NPC balloon"
weight = 1.0
```

Omit empty id/tag arrays. Keep `id`, `topic`, `line`, `response`, and `weight`.

For trainer reusable content, use `[[trainer_exchanges]]` with `source_tags = ["pokemon_trainer"]`.

## Prompt Rules

- Tell `pi`: valid TOML only, no Markdown fences, no comments, ASCII only.
- Tell `pi`: no romance, dating, grief, modern slang, or deleted NPC references.
- Keep each `line` and `response` <= 95 chars.
- Make the two balloons one actual exchange.
- Prefer tags for broad coverage; use `source_ids` + `target_ids` for important relationships.
- Never mention Marin unless the user explicitly re-adds that NPC.

## Coverage Rules

- Background NPC: 10-20 exchanges.
- Normal resident: 40-60 exchanges.
- Key mentor/main NPC: 80-120 exchanges.
- Iconic pair: 20-30 pair-specific exchanges.
- Reusable pack: 50+ exchanges per broad theme.

Do not author every permutation. Use the coverage script to find gaps, then fill high-value missing pairs.
