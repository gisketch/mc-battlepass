---
name: npc-creator
description: Create or update CKDM NPC TOML configs with correct NPC type, body/rendering shape, schedule, store/workplace, housing, personality/LLM prompt, gifts, friendship messages, camper/default balloons, missions/unique quests, boss/class links, and validation. Use when adding a new NPC, converting a character into an NPC, auditing NPC config completeness, creating NPC dialogue defaults, or preparing an NPC for later micro interactions.
---

# NPC Creator

Use for full NPC config work. This skill owns the NPC TOML shape; use `npc-micro-interaction-creator` after the NPC exists to add pair dialogue.

## Workflow

1. Read `docs/NPCS.md` definition fields and any relevant domain docs:
   - `docs/NPC_CLASS_QUESTS.md` for class mentors.
   - `docs/POKEMON_LEAGUES.md` for trainer/league NPCs.
   - `docs/MICRO_INTERACTIONS_GUIDE_FOR_FUTURE_NPCS.md` only to add `interaction_tags`.
2. Work in the Prism runtime config, not repo `runs/`:
   `C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod\npcs`
3. Classify NPC type before writing:
   - `resident`: town NPC with housing, work blocks, gifts, greetings, possible store.
   - `class_mentor`: resident plus `class`, Training flow, boss/mentor flavor.
   - `trainer`: Pokemon league/gym/rival NPC; battle-first, no normal housing/work unless requested.
   - `shopkeeper`: store-heavy NPC; clear `store`, `work_blocks`, and shop messages.
   - `professor`: league/support NPC; full custom content if user says key NPC.
   - `debug`: transient/test only; keep out of normal content pools.
4. Create or edit one `<id>.toml`.
5. Delegate lore/dialogue drafting to `pi`, then integrate the returned fragments into the NPC TOML.
6. Run validator:
   ```powershell
   python .codex/skills/npc-creator/scripts/validate_npc_config.py --npc-file "<runtime-npcs-dir>\<id>.toml"
   ```
7. If behavior changed, update docs or note why no doc update is needed.
8. Run `.\gradlew.bat build`; in game run `/npc reload`.

## Required NPC Shape

Every normal NPC should define:

- Identity: `id`, `name`, `title`, `skin`, `body_type`, `body_model`, scale fields.
- Behavior: `schedule`, `job_definition`, `housing`.
- Personality: `[personality] llm_prompt`, `traits`, `speech_style`, `catchphrases`.
- Voice/chat: `[voice]`, `[chat] call_names`.
- Gifts: loved/liked/disliked pools and reaction lines.
- Player-facing defaults:
  - `hurt_messages`
  - `wake_messages`
  - `friendship_messages.interact`
  - `friendship_messages.greeting`
  - `friendship_messages.first_daily_chat`
  - `camper_messages.needs_house_balloon/dialog` for move-in NPCs.
- Work/store:
  - `store` and `shop_messages` if the NPC sells.
  - `work_blocks` if the NPC can work or train.
- Missions:
  - Use `[missions]` and `[unique_quests]` only when this NPC should offer quests.
  - Prefer `unique_quests` templates for new NPCs; avoid huge legacy `missions.pool` unless needed.

## Type Rules

- `class_mentor`: set `class`, meaningful `work_blocks`, `boss.template` matching class/moveset when available, mentor-flavored personality, Training-compatible workplace.
- `trainer`: do not claim physical Kanto/Johto/Hoenn locations unless docs say so; Skylands hosting is the rule. Prefer battle/league dialogue and trainer tags.
- `resident`: must be coherent as a camper: can ask for a home, can sleep, has daily greeting lines.
- `shopkeeper`: store id must exist under `config/gisketchs_chowkingdom_mod/stores`.
- `professor`: should explain league/support role and avoid acting like a normal gym trainer unless configured.

## LLM Prompt Rules

Write `personality.llm_prompt` as durable behavior instructions:

- Who they are in Chow Kingdom.
- Their job/class/store role.
- What they should and should not claim.
- Speech style and emotional tone.
- Relevant constraints, such as store stock truth, Skylands league framing, or mentor training limits.

Keep prompt concrete. Do not include secrets, API keys, or generated chain-of-thought instructions.

## Dialogue Defaults

Use short, reusable lines. Include placeholders where supported:

- `{player}` for player name.
- `{npc}` for NPC name.
- `{item}`, `{quantity}`, `{total}` for shop/gift messages where documented.

For micro interactions, add only `interaction_tags` here. Then invoke `npc-micro-interaction-creator` to generate pair exchanges.

## Pi Use

Use `pi --model deepseek/deepseek-v4-flash` as the lore/dialogue subagent for:

- `personality.llm_prompt`
- traits, speech style, and catchphrases
- friendship/default dialogue pools
- camper lines
- shop message flavor
- mission flavor text

Codex owns schema, file placement, final TOML integration, and validation. Do not let `pi` rewrite code or runtime configs directly unless the user explicitly requests it.

Build a prompt:

```powershell
python .codex/skills/npc-creator/scripts/build_pi_lore_prompt.py --npc-id "<id>" --npc-name "<name>" --npc-type "<resident|class_mentor|trainer|shopkeeper|professor|debug>" --concept "<short concept>" --class-id "<optional>" --store-id "<optional>" --output ".codex-analysis/pi_<id>_lore_prompt.md"
```

Run `pi`:

```powershell
pi --model deepseek/deepseek-v4-flash --no-tools --no-session -p "@.codex-analysis/pi_<id>_lore_prompt.md"
```

Paste or merge the returned TOML fragments into the NPC file, then run the validator. If `pi` returns Markdown fences, comments, non-ASCII, bad claims, or invalid TOML, sanitize or rerun with stricter instructions.

## References

- Read `references/npc-config-shape.md` for a compact field checklist and TOML examples.
- Use `scripts/validate_npc_config.py` before handoff.
- Use `scripts/build_pi_lore_prompt.py` to create repeatable pi prompts for NPC lore/dialogue drafting.
