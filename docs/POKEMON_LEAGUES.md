# Pokemon League Gyms

Pokemon league gyms are CKDM-owned story progression backed by Radical Cobblemon Trainers API battles.

Trainer NPCs are different from town-life NPCs. They live around a Pokemon stadium area, do not need houses, do not use workplaces, and do not sell shops by default. They can still talk through the normal NPC dialogue/LLM path, but their main action is battle.

## V1 Rules

- Professor Chowfan (`prof_chowfan`) starts league records.
- A player can have one active generation league at a time.
- League progress is driven by an ordered `sequence`.
- Gyms, rivals, Elite Four, and Champion are all encounters.
- Rival battles are modeled as repeated encounters for one trainer identity.
- Trainer teams live in external RCT JSON files.
- Encounter unlocks are global, but badges and clears are per-player.
- Level caps are exact per battle. If the cap is 8, every Pokemon in the active party must be level 8 or lower.
- Each trainer NPC can be challenged 3 times per player, then that trainer enters a configurable real-time cooldown. Default cooldown is 15 minutes.
- Gym trainer NPCs are killable, but use gym-owned recovery instead of town NPC death/camper logic. Default respawn is 5 real minutes.
- `CHALLENGE` is always the official league-record encounter for that trainer. If the next story encounter is the same trainer but the unlock day has not arrived, `CHALLENGE` stays visible but disabled.
- `FRIENDLY BATTLE` is separate practice. It can be offered while the official encounter is delayed or when that trainer is not the player's next record match. Friendly wins do not grant badges, league clears, BP XP, or Chowcoins.
- Active league records add a pinned Pokemon mission only when the next official challenge is currently available. Delayed/cooldown encounters stay unpinned until ready.
- Kanto is the first league.
- Blue uses one default branch in V1.

## Config Layout

League TOML:

```text
config/gisketchs_chowkingdom_mod/gyms/leagues/kanto.toml
```

External RCT trainer JSON:

```text
config/gisketchs_chowkingdom_mod/gyms/rct_trainers/kanto/brock.json
```

League files use one ordered `[[sequence]]` list as the source of truth.

## League TOML Shape

```toml
id = "kanto"
display_name = "Kanto League"
stadium_area = "main_stadium"
active_only = true
starter_mode = "story_only"
chowfan_npc_id = "prof_chowfan"

[defaults]
daily_attempts_per_npc = 3
attempt_cooldown_minutes = 15
trainer_respawn_minutes = 5
battle_format = "GEN_9_SINGLES"
hard_level_cap = true
level_cap_scope = "whole_party"
heal_players = true
max_item_uses = 2
rival_branch = "default"
pass_id = "combat"
badge_xp = 250
badge_chowcoins = 500

[[trainers]]
id = "blue"
name = "Blue"
role = "rival"
npc_id = "trainer_blue"
spawn_group = "kanto_rivals"
main_pokemon = "cobblemon:blastoise"

[[trainers]]
id = "brock"
name = "Brock"
role = "gym_leader"
npc_id = "gym_brock"
badge_id = "boulder_badge"
spawn_group = "kanto_gym_leaders"
main_pokemon = "cobblemon:onix"

[[sequence]]
id = "blue_oaks_lab"
kind = "rival"
trainer = "blue"
display_name = "Blue - Oak's Lab"
level_cap = 5
team_ref = "rct_trainers/kanto/blue_oaks_lab.json"
required = true
global_unlock_next = true
spawn_delay_days = 1

[[sequence]]
id = "brock"
kind = "gym"
trainer = "brock"
display_name = "Brock"
badge_id = "boulder_badge"
level_cap = 14
team_ref = "rct_trainers/kanto/brock.json"
required = true
global_unlock_next = true
spawn_delay_days = 1
```

## Kanto V1 Sequence

1. Blue - Oak's Lab, cap 5
2. Blue - Route 22 pre-Brock, cap 9
3. Brock, cap 14
4. Blue - Nugget Bridge, cap 19
5. Misty, cap 21
6. Blue - S.S. Anne, cap 24
7. Lt. Surge, cap 24
8. Erika, cap 29
9. Blue - Pokemon Tower, cap 34
10. Blue - Silph Co., cap 40
11. Koga, cap 43
12. Sabrina, cap 43
13. Blaine, cap 47
14. Blue - Route 22 final rival battle, cap 48
15. Giovanni, cap 50
16. Elite Four #1
17. Elite Four #2
18. Elite Four #3
19. Elite Four #4
20. Champion Blue

## State Model

Player state:

- active league id
- cleared encounter ids per league
- badge ids per league
- trainer attempt records keyed by trainer id with real-time cooldown expiry
- last announced available encounter, so delayed challenge snackbars fire once per player

Global state:

- stadium areas
- optional stadium battle spots: `playerSpot` and `trainerSpot`
- globally unlocked encounter ids per league
- next-day availability for newly unlocked encounters
- active RCT battle context while battle is running

## Commands

- `/ck gyms reload`
- `/ck gyms area set <radius>`
- `/ck gyms area set_named <area_id> <radius>`
- `/ck gyms area set_player`
- `/ck gyms area set_trainer`
- `/ck gyms area tp [player]`
- `/ck gyms area teleport [player]`
- `/ck gyms area status`
- `/ck gyms status [player]`
- `/ck gyms league start <league_id> [player]`
- `/ck gyms league reset <league_id> <player>`
- `/ck gyms unlock <league_id> <encounter_id>`
- `/ck gyms grant <league_id> <encounter_id> <player>`
- `/ck gyms attempts get <trainer_id> <player>`
- `/ck gyms attempts reset <trainer_id> <player>`

## Mission Events

- `gisketchs_chowkingdom_mod:gym_battle_attempted`
- `gisketchs_chowkingdom_mod:gym_battle_won`
- `gisketchs_chowkingdom_mod:gym_badge_earned`

Attributes should include `league`, `encounter`, `trainer`, `kind`, and `badge` when available.

## V1 Implementation Status

Implemented in code:

- `gyms/` module with league config, world/player state, commands, trainer dialogue, Professor Chowfan record flow, stadium area storage, next-day global unlocks, and RCT battle start/end wiring.
- Default Kanto league TOML and external RCT JSON stubs are written when missing.
- Default Kanto trainer NPC TOML files are written when missing. League start reuses the existing Prism `prof_chowfan` NPC instead of writing a separate clerk NPC.
- First-clear rewards grant combat BP XP and Chowcoins.
- First badge clear sends snackbar and Discord webhook relay.
- Friendly battles are available from trainer NPCs when the player is not on that trainer's active league record.
- NPC main Pokemon companions are recalled with a short particle/sound cue before RCT battles, hidden during the battle, then restored after the battle ends.
- Battle start sends a black client fade, waits briefly, teleports the player and trainer NPC to the configured stadium battle spots, faces them toward each other, then starts the RCT battle while the screen fades back in.
- Strict story delay keeps delayed official encounters visible as disabled `CHALLENGE`, with separate `FRIENDLY BATTLE`.
- Active league records sync into the pinned NPC quest HUD when the next challenge is available, and delayed challenges send a one-time `POKEMON CHALLENGE READY` snackbar when their unlock day arrives.
- Gym trainers reconcile to one loaded NPC per trainer id, remove stale duplicates, and respawn through the gym system after the configured real-time recovery.

Still needs in-game smoke testing:

- RCT battle launch from a live trainer NPC.
- RCT `BATTLE_ENDED` callback winner detection.
- Cobblemon party level inspection against the exact party cap.
- Stadium spawn placement around the configured area in the Prism instance.
