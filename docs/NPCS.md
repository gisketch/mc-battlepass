# NPCs

NPCs are config-driven characters backed by a small server foundation: definitions, world state, jobs, dialog, and housing contracts.

## Current Slice

- One default NPC: `finn`.
- Config folder: `config/gisketchs_chowkingdom_mod/npcs`.
- Default file: `finn.json`, written if missing.
- Shared friendship message fallback: `friendship_messages.json`, written if missing and used by all NPCs unless a definition overrides `friendship_messages`.
- Intro block: `gisketchs_chowkingdom_mod:camping_block`.
- Contract item: `gisketchs_chowkingdom_mod:rent_contract`.
- Entity id: `gisketchs_chowkingdom_mod:npc`.

Place a camping block to spawn the first available NPC if none exists. Right-click the block later to retry if no NPC is currently present. Right-click Finn to open dialog and receive a rent contract. Use the rent contract on a bed to assign Finn's home.

## Dialog

NPC dialog opens as a local screen panel near the bottom of the screen. The panel uses `textures/gui/9slice_container_grey.png`, shows the NPC head avatar, renders the NPC name in the CKDM bold font, renders dialog body copy with the normal Minecraft font, and shows right-side action buttons for Talk, Buy, Gift, and Bye. The panel enters with a bottom-anchored scale/slide animation, the dialog body types in, and the vanilla hotbar is hidden while the dialog screen is open.

Buy opens the store configured on the NPC definition. After a successful NPC store purchase, the owning NPC opens a close-only follow-up dialog chosen from `shop_messages` by friendship category and quantity bucket. Gift is disabled unless the player is holding an item, shows a hover hint, consumes one held item on success, and plays a reaction dialog with a single OKAY close button. Locked relic/player-locked items are rejected by the existing relic transfer guard. Bye closes the normal screen. Talk is reserved for a future conversation input flow.

The dialog header shows the NPC avatar, name, and a 10-icon friendship row. Positive levels render filled hearts, negative levels render angry icons, and remaining slots render empty hearts. Dialog body text uses the CKDM small bold font at 75% white opacity.

In multiplayer, the interacting player sees the dialog screen only. Other players within 30 blocks of the NPC receive a chat line in the form `NPC > Player : message`. Discord receives every NPC dialog through the webhook, with the NPC name/avatar as the webhook identity and a linked Discord user mention when the interacting player has an account link.

While talking, the NPC briefly stops navigating and looks at the interacting player. During the local dialog typewriter reveal, NPCs play proximity-faded animalese sounds using the configured voice pitch. Nearby world chat relay and hurt messages do not play animalese.

## Definition Fields

Each NPC is one JSON file:

- `id`: Stable NPC id. Used for state, commands, contracts, and future relationship data.
- `name`: Display name.
- `title`: Display subtitle.
- `skin`: Future skin resource id. Current renderer uses `textures/entity/npc/finn.png` for `finn` and a vanilla fallback for other NPC ids.
- `body_type`: Player-model shape. Supported values: `normal`, `slim`.
- `job`: Job behavior id. Current supported ids: `adventurer`, `warrior`.
- `job_definition`: Static job behavior knobs loaded from JSON: `scan_interval_ticks`, `roam_radius`, and `work_scan_radius`.
- `schedule`: Static day schedule loaded from JSON. Each activity entry has `from_hour`, `to_hour`, and `activity`. Hours use a 24-hour clock where `00` is midnight.
- `store`: Fixed custom store id opened by the dialog Buy button.
- `personality`: Future dialog/LLM seed fields: `llm_prompt`, `traits`, `speech_style`, `catchphrases`.
- `housing`: Move-in rules: `can_move_in`, `requires_bed`.
- `voice`: Animalese dialog voice settings: `animalese_pitch` (`high`, `med`, `low`, `lowest`), `pitch`, `volume`, and `radius`.
- `gifts`: Gift ids/tags grouped by `loved`, `liked`, `disliked`, plus `daily_limit`, `reset_hour`, and reaction message pools for `loved`, `liked`, `disliked`, and `neutral`.
- `friendship_messages`: Optional per-NPC category message overrides for `interact`, `gift`, `hurt`, and `wake`. Categories are `hatred`, `enemy`, `dislike`, `neutral`, `okay`, `good_friends`, and `best_friends`. If omitted, the shared `friendship_messages.json` fallback is used.
- `shop_messages`: Optional post-purchase message overrides with `single`, `normal`, and `bulk` buckets. Each bucket has the same friendship categories as `friendship_messages`. Supports `{player}`, `{npc}`, `{item}`, `{quantity}`, `{total}`, `{friendship_level}`, and `{friendship_points}`.
- `hurt_messages`: NPC speech pool used every third hit from the same player. Supports `{player}`.
- `wake_messages`: NPC speech pool used when a sleeping NPC is right-clicked. Supports `{player}`.
- `work_target_blocks`: Block ids/tags the job can path toward while roaming.

## State

Runtime state is stored in world data:

`<world>/data/gisketchs_chowkingdom_mod/npcs/state.json`

Resident state tracks each NPC's entity UUID, camp position, assigned home bed, whether a contract has been given, recent hurt records, per-player conversation history, per-player gift counters, last hurt player/streak, death state, and scheduled respawn day. Hurt history stores only every third same-player hit event and keeps the latest 10 records. This is world data, separate from static JSON definitions and runtime brain state.

Per NPC/player friendship is stored as points from `-1000` to `1000`, defaulting to `100` (`Lv. 1`). The derived friendship level is `-10` to `10`. Categories map as: `-10 hatred`, `-9..-6 enemy`, `-5..-3 dislike`, `-2..2 neutral`, `3..5 okay`, `6..9 good_friends`, `10 best_friends`.

Each NPC/player conversation keeps at most 30 records. The world also keeps at most 30 global NPC-relevant events. This is the foundation for future LLM prompt context.

## Runtime Flow

- `NpcDefinition`: static NPC identity loaded from JSON.
- `NpcJobDefinition`: static job behavior values loaded from JSON.
- `NpcScheduleDefinition`: static 24-hour schedule loaded from JSON.
- `NpcResidentState`: saved world data for home bed, camp, contract, and spawned entity UUID.
- `NpcBrain`: runtime logic that chooses the current schedule activity and movement target.
- `NpcBrainOverrides`: temporary interrupt behaviors that can hijack schedule movement for reactive events, such as retaliation or running away from danger.
- `NpcEntity`: the Minecraft entity that moves, interacts, renders, and exposes debug state.

## Memory Context

NPC memory records the current player-specific conversation and a small global event stream. Current captured events:

- Player interacts with NPC.
- NPC dialog message.
- Player hurts NPC.
- NPC hurt response.
- NPC death and killer when known.
- NPC respawn.
- Player death messages.

Future LLM prompt building should read `NpcStore.llmContext(npcId, player)` and inject global events before player-specific conversation records. The context can also carry the current 24-hour schedule hour.

The LLM context includes the current friendship snapshot: points, level, and category. Treat this as high-priority tone context for future generated NPC replies.

Current built-in activities are `work`, `home`, and `sleep`. If an older NPC config has no schedule, the loader supplies the default `06-20 work`, `20-22 home`, `22-06 sleep` routine. During `sleep`, Finn walks to his assigned bed and uses the sleeping pose when close enough.

Right-clicking a sleeping NPC wakes it, opens wake-specific dialog, pauses sleep while talking, then lets the brain return it to sleep when the dialog window ends and the schedule is still `sleep`.

When the same player hits an NPC three times in a row, the NPC speaks a random `hurt_messages` line in nearby world chat. Those third-hit events are persisted in `NpcResidentState` with timestamp and player identity.

The third same-player hit also starts a short brain override. The NPC equips a temporary random weapon, chases the attacker, then runs two scripted attack pulses with synced custom arm animation, attack sound, direct damage, and knockback when in range. The NPC renderer uses the vanilla player model plus an item-in-hand layer, and the custom animation copies transforms to the sleeve/jacket layer so the outer skin follows the swing. NPCs also run away for 3 seconds when they step on fire, soul fire, campfire, or soul campfire. Overrides pause normal schedule/job navigation and then fall back to the regular brain.

When Jade is installed, hovering an NPC shows the NPC display name and the hovering player's friendship category for that NPC with a small heart, empty heart, or angry icon.

Gift limits are per NPC/player and reset by in-game schedule period. Finn defaults to one gift per in-game day, resetting at 05:00. Gifts adjust friendship only: neutral `+5`, liked `+25`, loved `+50`, disliked `-50`. Hitting an NPC changes friendship by `-10`; killing one changes friendship by `-300`. No extra friendship mechanics or rewards exist yet.

If an NPC dies, resident state marks it dead. Once the scheduled respawn day is due and the in-game hour is 05:00 or later, the server respawns it at its assigned bed if it has one. This is intentionally tolerant of debug time jumps that skip across the exact 05:00 scan window.

Home beds are validated against live bed blocks. If the assigned bed is broken or missing, the stored home is cleared, the NPC stops treating itself as housed, and interaction can grant a new rent contract. Dead NPCs without a valid assigned bed do not bed-respawn; placing a camping block can introduce them again and clears stale death state.

## Commands

- `/npc reload`: reload NPC config files.
- `/npc spawn <id>`: spawn an NPC at the command source player's position.
- `/npc respawn status <id>`: show live/dead state, current day/hour, stored respawn day, home bed validity, and whether the NPC is ready to respawn.
- `/npc respawn <id>`: force-respawn a dead or missing NPC at its valid home bed.
- `/npc friendship get <id> <player>`: show that player's points, level, and category for an NPC.
- `/npc friendship set <id> <player> <points>`: set points from `-1000` to `1000`.
- `/npc friendship add <id> <player> <delta>`: add or subtract points, clamped to `-1000..1000`.
- `/npc debug`: toggle realtime actionbar debug for the Chow Kingdom NPC under the player's crosshair. Run it on the same NPC again to stop. The actionbar includes current schedule hour, activity, task, navigation state, target, and debug time multiplier.
- `/npc debug time <multiplier>`: speed up day time for debugging schedules. Use `1` to reset. Allowed range is `1` to `240`.
- `/ck npc ...` and `/chowkingdom npc ...`: aliases.

## Jade

When Jade is installed, beds assigned through rent contracts show the owner, such as `Finn's Bed`.

## Extension Points

Add future runtime decision logic in `NpcBrain`. Keep static behavior knobs in `NpcJobDefinition` and schedules in `NpcScheduleDefinition`.

Add future data fields to `NpcDefinition` instead of hard-coding per-NPC behavior in the entity.

Add future store, gift, relationship, and quest systems by reading the stable NPC id and definition fields. The entity should stay thin: identity, saved positions, interaction entrypoint, short-lived talking state, debug state, and per-tick delegation.
