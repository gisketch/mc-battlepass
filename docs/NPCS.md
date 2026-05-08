# NPCs

NPCs are config-driven characters backed by a small server foundation: definitions, world state, jobs, dialog, and housing contracts.

## Current Slice

- One default NPC: `finn`; additional NPC TOML files join the camping-block camper pool.
- Runtime config folder: `<game config>/gisketchs_chowkingdom_mod/npcs`; in local `runClient`, this is `runs/client/config/gisketchs_chowkingdom_mod/npcs`.
- Default file: `finn.toml`, written if missing.
- Global NPC settings: `settings.toml`, written if missing.
- Shared friendship message fallback: `friendship_messages.toml`, written if missing and used by all NPCs unless a definition overrides `friendship_messages`.
- Intro block: `gisketchs_chowkingdom_mod:camping_block`.
- Contract item: `gisketchs_chowkingdom_mod:rent_contract`.
- Entity id: `gisketchs_chowkingdom_mod:npc`.

Place a camping block to spawn a random eligible camper from the configured NPC pool. Right-click the block later to retry if no NPC is currently present and camp cooldown is ready. Right-click an unhoused camper to open dialog and receive a rent contract. Use the rent contract on a bed to assign that NPC's home.

## Dialog

NPC dialog opens as a local screen panel near the bottom of the screen. The panel uses `textures/gui/9slice_container_grey.png`, shows the NPC head avatar, renders the NPC name in the CKDM bold font, renders dialog body copy with the normal Minecraft font, and shows right-side action buttons for Talk, Buy, Gift, and Bye. The panel enters with a bottom-anchored scale/slide animation, the dialog body types in, and the vanilla hotbar is hidden while the dialog screen is open.

Buy opens the store template configured on the NPC job definition. NPC stores share the same store TOML pool by store id, but each NPC uses its own stock key, so two NPCs can roll different daily/weekly items and deplete stock independently. After a successful NPC store purchase, the owning NPC opens a close-only follow-up dialog chosen from `shop_messages` by friendship category and quantity bucket. Gift is disabled unless the player is holding an item, shows a hover hint, consumes one held item on success, and plays a reaction dialog with a single OKAY close button. Locked relic/player-locked items are rejected by the existing relic transfer guard. Bye closes the normal screen.

Talk opens a text input inside the existing dialog. Submitting sends the message to the server, validates NPC distance/state, shows a pending `...` in the dialog and nearby balloon while waiting, then replaces it with the validated LLM reply or a configured fallback. LLM provider settings are global in NPC `settings.toml`; `llm_message_usage` controls which message surfaces may use LLM text. V1 supports `openai_compatible` and `gemini` provider modes and expects JSON output with a single `message` field.

The dialog header shows the NPC avatar, name, and a 10-icon friendship row. Positive levels render filled hearts, negative levels render angry icons, and remaining slots render empty hearts. Dialog body text uses the CKDM small bold font at 75% white opacity.

In multiplayer, the interacting player sees the dialog screen only. Other players within 30 blocks of the NPC see the overheard NPC line as a world-space balloon above the NPC. Discord receives every NPC dialog through the webhook, with the NPC name/avatar as the webhook identity and a linked Discord user mention when the interacting player has an account link.

While talking, the NPC briefly stops navigating and looks at the interacting player. During the local dialog typewriter reveal, NPCs play proximity-faded animalese sounds using the configured voice pitch. Nearby NPC balloons and hurt balloons do not play animalese.

When a player comes within the global NPC greeting radius and has not spoken to that NPC yet that in-game day, the NPC pauses briefly, looks at the player, and shows a world-space greeting balloon for the configured duration. If the player does not interact, the same NPC/player pair can greet again after the configured cooldown. If the player leaves the radius, that cooldown resets. Right-clicking the NPC for the first chat of the in-game day grants +25 friendship, stops greeting balloons for that day, and uses the configured first-chat message pool when the NPC already has a home and is awake.

## Definition Fields

Each NPC is one TOML file:

- `id`: Stable NPC id. Used for state, commands, contracts, and future relationship data.
- `name`: Display name.
- `title`: Display subtitle.
- `skin`: Future skin resource id. Current renderer uses `textures/entity/npc/finn.png` for `finn` and a vanilla fallback for other NPC ids.
- `body_type`: Player-model shape. Supported values: `normal`, `slim`.
- `job`: Job behavior id. Supported ids: `adventurer`, `warrior`, `fashionista`. Unknown ids normalize to `adventurer`.
- `job_definition`: Static job behavior knobs loaded from TOML: `store`, `scan_interval_ticks`, `roam_radius`, and `work_scan_radius`. `store` is a store template id, not a shared stock instance.
- `schedule`: Static day schedule loaded from TOML. Each activity entry has `from_hour`, `to_hour`, and `activity`. Hours use a 24-hour clock where `00` is midnight.
- `store`: Legacy NPC-level store template id. New configs should use `job_definition.store` so multiple NPCs can share store pools without sharing rolled stock.
- `personality`: Future dialog/LLM seed fields: `llm_prompt`, `traits`, `speech_style`, `catchphrases`.
- `housing`: Move-in rules: `can_move_in`, `requires_bed`.
- `voice`: Animalese dialog voice settings: `animalese_pitch` (`high`, `med`, `low`, `lowest`), `pitch`, `volume`, and `radius`.
- `gifts`: Gift ids/tags grouped by `loved`, `liked`, `disliked`, plus `daily_limit`, `reset_hour`, and reaction message pools for `loved`, `liked`, `disliked`, and `neutral`.
- `friendship_messages`: Optional per-NPC category message additions for `interact`, `gift`, `hurt`, `wake`, `greeting`, and `first_daily_chat`. Categories are `hatred`, `enemy`, `dislike`, `neutral`, `okay`, `good_friends`, and `best_friends`. Message pools resolve in order: built-in defaults, shared `friendship_messages.toml`, then NPC-specific `friendship_messages`. Each configured layer joins the inherited pool and is inserted twice, so local additions have roughly 2:1 weight against inherited generic lines.
- `shop_messages`: Optional post-purchase message overrides with `single`, `normal`, and `bulk` buckets. Each bucket has the same friendship categories as `friendship_messages`. Supports `{player}`, `{npc}`, `{item}`, `{quantity}`, `{total}`, `{friendship_level}`, and `{friendship_points}`.
- `camper_messages`: Optional uncategorized camp message pools: `needs_house_balloon`, `needs_house_dialog`, `lost_house_balloon`, and `lost_house_dialog`. Supports `{player}` and `{npc}`.
- `hurt_messages`: NPC speech pool used every third hit from the same player. Supports `{player}`.
- `wake_messages`: NPC speech pool used when a sleeping NPC is right-clicked. Supports `{player}`.
- `work_target_blocks`: Block ids/tags the job can path toward while roaming.

Shared generic messages live in `friendship_messages.toml` and affect every NPC. NPC TOML files can add their own message lines under the same keys; those NPC-specific lines join the shared random pool with higher weight.

Global greeting behavior lives in `settings.toml`:

```toml
[greetings]
radius = 5.0
cooldown_seconds = 60
balloon_duration_seconds = 5
```

Clock compatibility is automatic when Better Days is installed and `betterdays-common.toml` exists. Chow Kingdom reads Better Days `dayStart` and `nightStart`, then maps that raw sky-time range to a 24-hour clock:

```toml
speedMethod = "MINUTES"
daySpeedMinutes = 0.5
nightSpeedMinutes = 0.5
dayStart = 23500.0
nightStart = 12500.0
```

If Better Days is not present, Chow Kingdom falls back to vanilla `0..24000` timing. NPC schedules, first daily chats, gift reset periods, respawn hour checks, camper cooldown hours, shipping bin payout, and LLM time context all use this clock. Store stock resets intentionally still use real-life time from store config.

Camper rotation settings also live in NPC `settings.toml`:

```toml
[campers]
cooldown_min_hours = 24
cooldown_max_hours = 48
needs_house_llm_prompt = "The player found you waiting at camp without a home. Ask for a bed or small house and mention the rent contract."
lost_house_llm_prompt = "Your assigned bed or home was removed. Tell the player you lost your bed and need a new one."

[llm_message_usage]
camper_needs_house = false
camper_lost_house = false
```

Example shared `friendship_messages.toml` addition:

```toml
[greeting]
neutral = ["Hi, {player}!"]
good_friends = ["Good to see you, {player}."]

[first_daily_chat]
neutral = ["First check-in of the day, {player}."]
```

Example NPC-specific addition inside an NPC TOML file:

```json
{
	"friendship_messages": {
		"greeting": {
			"neutral": ["Adventure time, {player}?"]
		},
		"first_daily_chat": {
			"neutral": ["First quest check-in today. What are we doing, {player}?"]
		}
	}
}
```

## State

Runtime state is stored in world data:

`<world>/data/gisketchs_chowkingdom_mod/npcs/state.json`

Resident state tracks each NPC's entity UUID, camp position, assigned home bed, whether a contract has been given, recent hurt records, per-player conversation history, per-player gift counters, last hurt player/streak, death state, scheduled respawn day, and camper return reason. World state also tracks the latest camping block position, the active unhoused camper id, and the next camper cooldown tick. Hurt history stores only every third same-player hit event and keeps the latest 10 records. This is world data, separate from static JSON definitions and runtime brain state.

Greeting state is tracked per NPC/player. It stores the last greeting day, the real-time greeting cooldown expiry, and the first-chat day used to stop repeat greetings and prevent duplicate daily friendship rewards.

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

When the same player hits an NPC three times in a row, the NPC speaks a random `hurt_messages` line in a nearby world-space balloon. Those third-hit events are persisted in `NpcResidentState` with timestamp and player identity.

The third same-player hit also starts a short brain override. The NPC equips a temporary random weapon, chases the attacker, then runs two scripted attack pulses with synced custom arm animation, attack sound, direct damage, and knockback when in range. The NPC renderer uses the vanilla player model plus an item-in-hand layer, and the custom animation copies transforms to the sleeve/jacket layer so the outer skin follows the swing. NPCs also run away for 3 seconds when they step on fire, soul fire, campfire, or soul campfire. Overrides pause normal schedule/job navigation and then fall back to the regular brain.

When Jade is installed, hovering an NPC shows the NPC display name and the hovering player's friendship category for that NPC with a small heart, empty heart, or angry icon.

Gift limits are per NPC/player and reset by in-game schedule period. Finn defaults to one gift per in-game day, resetting at 05:00. Gifts adjust friendship only: neutral `+5`, liked `+25`, loved `+50`, disliked `-50`. Hitting an NPC changes friendship by `-10`; killing one changes friendship by `-300`; first daily chat changes friendship by `+25`.

If a housed NPC dies, resident state marks it dead. Once the scheduled respawn day is due and the in-game hour is 05:00 or later, the server respawns it at its assigned bed if it has one. If an unhoused camper dies, the active camper remains reserved and respawns at the camping block instead. This is intentionally tolerant of debug time jumps that skip across the exact 05:00 scan window.

Home beds are validated against live bed blocks. If the assigned bed is broken or missing, the stored home is cleared, the NPC returns to the camping block when one is known, becomes the active camper again, and interaction can grant a new rent contract with lost-house dialog. Dead NPCs without a valid assigned bed camp-respawn instead of bed-respawning.

## Camper Rotation

- Only one active unhoused camper can exist at a camping block.
- Eligible campers are configured NPCs with `housing.can_move_in=true`, no valid home bed, and no live entity already present.
- The pool is unique by NPC id; once every configured NPC has a home, camp stops spawning new campers.
- Assigning a bed clears active camper state and schedules the next camper after a random `campers.cooldown_min_hours..cooldown_max_hours` Minecraft-hour delay.
- Breaking an assigned bed cancels that NPC's housed status and makes that NPC the active camper again, ahead of any new camper.
- If the camping block is removed, automatic cooldown spawning pauses until a camping block position is stored again by placing or right-clicking one.

## Commands

- `/npc reload`: reload NPC config files.
- `/npc spawn <id>`: spawn an NPC at the command source player's position.
- `/npc respawn status <id>`: show live/dead state, current day/hour, stored respawn day, home bed validity, and whether the NPC is ready to respawn.
- `/npc respawn <id>`: force-respawn a dead or missing NPC at its valid home bed.
- `/npc clear all confirm`: dangerous admin reset. Backs up NPC `state.json`, deletes all live NPC entities, clears all NPC world state, bed assignments, memories, camper state, and online rent contracts.
- `/npc clear <id> confirm`: dangerous admin reset for one NPC id. Backs up NPC `state.json`, deletes that live NPC entity, clears that NPC's state, bed assignment, memories, and online rent contracts for that NPC.
- `/npc friendship get <id> <player>`: show that player's points, level, and category for an NPC.
- `/npc friendship set <id> <player> <points>`: set points from `-1000` to `1000`.
- `/npc friendship add <id> <player> <delta>`: add or subtract points, clamped to `-1000..1000`.
- `/npc debug clock`: toggle live CK clock actionbar with `HH:MM AM/PM`, raw world time, tick-of-cycle, clock source, and daylight gamerule state.
- `/npc debug`: toggle realtime actionbar debug for the Chow Kingdom NPC under the player's crosshair. Run it on the same NPC again to stop. The actionbar includes current schedule time, tick-of-cycle, activity, task, navigation state, target, and debug time multiplier.
- `/npc debug time <multiplier>`: speed up day time for debugging schedules. Use `1` to reset. Allowed range is `1` to `240`.
- `/npc debug balloon <id> <message>`: show a timed test balloon above the spawned NPC for nearby players.
- `/ck npc ...` and `/chowkingdom npc ...`: aliases.

## Jade

When Jade is installed, beds assigned through rent contracts show the owner, such as `Finn's Bed`.

## Extension Points

Add future runtime decision logic in `NpcBrain`. Keep static behavior knobs in `NpcJobDefinition` and schedules in `NpcScheduleDefinition`.

Add future data fields to `NpcDefinition` instead of hard-coding per-NPC behavior in the entity.

Add future store, gift, relationship, and quest systems by reading the stable NPC id and definition fields. The entity should stay thin: identity, saved positions, interaction entrypoint, short-lived talking state, debug state, and per-tick delegation.
