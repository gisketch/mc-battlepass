# NPC Micro Interaction Dialogue

## Goal

Fix [GIS-62](https://linear.app/gisketch/issue/GIS-62/p2-fix-per-npc-chat-balloons-for-micro-interactions-more-lore-accurate) by replacing generic NPC-to-NPC micro interaction balloons with immersive, lore-accurate, low-repeat exchanges.

## Acceptance Criteria

- Two NPCs in a micro interaction show paired lines from one shared exchange, not two unrelated random one-liners.
- Each configured town/mentor NPC has at least 10 short interaction lines or exchange entries in the Prism runtime config.
- Exchange text fits world-space balloons: short, readable, no long exposition, no hidden reasoning text.
- Pair selection can use generic topics, per-NPC fallback lines, and optional pair-specific overrides.
- Recent exchange/topic ids are suppressed so nearby players do not see the same beat repeatedly.
- Existing movement, cooldown, close-player-only balloon visibility, and global event recording behavior remains intact.
- Docs describe the config shape and runtime rules.

## Context Links

- Linear: `GIS-62`.
- Runtime config source of truth: `C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod\npcs`.
- Current code: `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt`.
- Current config fields: `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcDefinitions.kt`.
- Behavior docs: `docs/NPCS.md`, `docs/NPC_CONVERSATIONS.md`.

## Plan

1. Inventory NPCs and split them into content groups:
   - Town/CKDM residents: high priority.
   - Class mentor / crossover-inspired NPCs: high priority.
   - Pokemon league trainers and Elite Four: medium priority, can use league/battle topic packs.
   - Debug or generated NPCs: low priority.

2. Add a data shape for real exchanges:
   - Global settings support `exchange_topics`.
   - Per-NPC config supports `npc_interaction_exchanges`.
   - Optional pair-specific overrides support lines for a known target NPC id.
   - Each exchange has `id`, `line`, `response`, optional `topic`, optional `targets`, and optional `weight`.

3. Change runtime selection:
   - At `startNpcMicroInteraction`, select one exchange for the pair.
   - Assign `line` to the initiating NPC and `response` to the partner.
   - Replace `{npc}` and `{other}` placeholders.
   - Fall back to existing `npc_interaction_messages` if no exchange is eligible.
   - Record the chosen exchange id/topic in the global event summary.

4. Add anti-repeat memory:
   - Keep a small in-memory recent exchange bag per unordered NPC pair.
   - Avoid last 3-5 exchange ids for that pair when alternatives exist.
   - Keep existing in-game cooldowns unchanged.

5. Author immersive content:
   - Start with 10-15 entries for each non-debug NPC in the Prism instance.
   - Prefer pairs/topics that reveal personality, jobs, quests, main Pokemon, class identity, workplace, town worries, and playful rivalries.
   - Keep lines direct. No narrator phrasing like "Talking with..." unless used only as fallback.

6. Validation:
   - Run Gradle checks from `docs/quality.md`.
   - Run `/npc reload` in the Prism instance.
   - Spawn or gather several NPC pairs and watch repeated micro interactions with debug time speedup.
   - Confirm balloons are paired, short, readable, and not spammy.

## Decision Log

- Use authored data first. LLM-generated ambient exchanges are not the V1 path because these run passively near players and must not block tick/chat threads or produce long unstable text.
- Best immersion target is paired exchanges, not only bigger one-line pools. The current one-line model still feels generic even with more text because the two NPCs do not answer each other.
- Runtime config under the Prism instance is the content source of truth for this issue, not repo `runs/`.

## Progress Log

- 2026-05-18: Linear issue read. Current implementation inspected. Plan created.
