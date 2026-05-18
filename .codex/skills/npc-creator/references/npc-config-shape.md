# NPC Config Shape

Compact checklist for CKDM NPC TOML.

## Minimal Resident

```toml
id = "example"
name = "Example"
title = "Town Resident"
skin = "gisketchs_chowkingdom_mod:npc/example"
body_type = "normal"
body_model = "boy"
height = 1.0
weight = 1.0
store = ""
interaction_tags = ["town"]
hurt_messages = ["Careful, {player}."]
wake_messages = ["Mmph. I am awake."]

[job_definition]
scan_interval_ticks = 60
roam_radius = 7
work_scan_radius = 9

[schedule]
activities = [
  { from_hour = 7, to_hour = 15, activity = "work" },
  { from_hour = 15, to_hour = 20, activity = "meetup" },
  { from_hour = 22, to_hour = 7, activity = "sleep" },
]

[personality]
llm_prompt = "Who this NPC is, what they do, how they speak, and what they must not claim."
traits = ["warm", "practical"]
speech_style = "short, grounded, cozy"
catchphrases = ["Short phrase."]

[housing]
can_move_in = true
requires_bed = true

[voice]
animalese_pitch = "med"
pitch = 1.0
volume = 0.35
radius = 12.0

[chat]
enabled = true
call_names = ["example"]
minecraft_chat = true
discord_chat = true
```

## Common Blocks

Work blocks:

```toml
work_blocks = [
  { id = "minecraft:barrel", count = 2, display_name = "barrels" },
  { id = "#minecraft:beds", count = 1, display_name = "bed" },
]
```

Gifts:

```toml
[gifts]
loved = ["minecraft:diamond"]
liked = ["minecraft:bread"]
disliked = ["minecraft:rotten_flesh"]
daily_limit = 1
reset_hour = 5
```

Missions:

```toml
[missions]
enabled = true
offer_radius = 7.0
offer_balloon_messages = ["@quest_log.png I have a task, {player}."]

[unique_quests.fetch]
entries = [
  { id = "example_fetch", event_desc = "Bring {goal} Bread", quest_text = "Bring bread for the pantry.", pass_id = "cozy", xp = 80, chowcoins = 25, fetch_item = "minecraft:bread", fetch_count = 4, weight = 1.0 },
]
```

Boss/class mentor:

```toml
class = "warrior"

[boss]
enabled = true
health = 80.0
damage = 4.0
template = "warrior"
```

## Review Rules

- `id` must match file stem unless there is a deliberate migration.
- Store id must exist if nonblank.
- Class id should exist under roles/classes if nonblank.
- Housing NPCs need sleep schedule and camper lines.
- Store/training NPCs need work blocks.
- LLM prompt must say what the NPC must not claim.
- Trainer dialogue must not claim physical old-region travel when Skylands hosting applies.

## Pi Lore Delegation Contract

Use `pi --model deepseek/deepseek-v4-flash` to draft lore/dialogue fragments only.

Codex still decides:

- NPC type.
- final TOML shape.
- store/class/work block validity.
- mission mechanics.
- validation and docs.

Reject or rerun pi output if it includes Markdown fences, non-ASCII text, chain-of-thought, secrets, romance, or claims about hidden runtime state.
