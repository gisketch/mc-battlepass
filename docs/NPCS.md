# NPCs

NPCs are config-driven characters backed by a small server foundation: definitions, world state, jobs, dialog, housing contracts, and a SmartBrainLib in-world brain.

In-world NPC AI uses [SmartBrainLib](references/smartbrainlib.md). CKDM still owns dialog, shops, gifts, quests, LLM routing, and UI contracts.

Conversation-specific runtime rules live in [NPC Conversations](NPC_CONVERSATIONS.md).

Custom Gecko animation AI planning lives in [NPC Custom Animation AI](NPC_CUSTOM_ANIMATION_AI.md).

## Current Slice

- One default NPC: `finn`; additional NPC TOML files join the camping-block camper pool.
- Runtime config folder: `<game config>/gisketchs_chowkingdom_mod/npcs`; in local `runClient`, this is `runs/client/config/gisketchs_chowkingdom_mod/npcs`.
- Default file: `finn.toml`, written if missing.
- Global NPC settings: `settings.toml`, written if missing.
- Rendering experiment: `settings.toml` `[rendering].playerlike_renderer` switches NPCs to an RCT-style vanilla `PlayerModel`/`HumanoidMobRenderer` path for EMF/ETF animation pack testing. Restart the client after changing it. FA/Fresh may apply automatically if it hooks custom player-shaped mobs; otherwise a compat resource pack or deeper renderer adapter is needed.
- Custom animation experiment: per-NPC `custom_animation = true` routes that entity to the GeckoLib playerlike model and bypasses the EMF-oriented playerlike renderer so CKDM owns the pose.
- Custom animation IDs are read from `assets/gisketchs_chowkingdom_mod/animations/npc/playerlike.animation.json`; `/npc animations reload` refreshes command suggestions and asks the caller's client to reload resources.
- Shared friendship message fallback: `friendship_messages.toml`, written if missing and used by all NPCs unless a definition overrides `friendship_messages`.
- Intro block: `gisketchs_chowkingdom_mod:camping_block`.
- Contract item: `gisketchs_chowkingdom_mod:rent_contract`.
- Job application item: `gisketchs_chowkingdom_mod:job_application`.
- Entity id: `gisketchs_chowkingdom_mod:npc`.

Place a camping block to spawn a random eligible camper from the configured NPC pool. Right-click the block later to retry if no NPC is currently present and camp cooldown is ready. Right-click an unhoused camper to open dialog and receive a rent contract. Use the rent contract on a bed to assign that NPC's home. Use the WORK dialog action to receive a job application when the NPC has no workplace; the NPC follows nearby players holding their job application. Right-click a block with it while the NPC is nearby to set that block as the workplace, after configured `work_blocks` are present nearby.

## Dialog

NPC dialog opens as a local screen panel near the bottom of the screen. The panel uses `textures/gui/9slice_container_grey.png`, shows the NPC head avatar, renders the NPC name in the CKDM bold font, renders dialog body copy with the normal Minecraft font, and shows right-side action buttons for Talk, Buy, Gift, Work, and Bye. The panel enters with a bottom-anchored scale/slide animation, the dialog body types in, and the vanilla hotbar is hidden while the dialog screen is open.

Buy opens the store template configured on the NPC job definition. NPC stores share the same store TOML pool by store id, but each NPC uses its own stock key, so two NPCs can roll different daily/weekly items and deplete stock independently. WORK grants a job application if the NPC has no workplace, or opens workplace management with MOVE and FIRE if one is assigned. MOVE gives a fresh job application for reassignment. FIRE clears the assigned workplace and leaves the NPC unemployed until WORK is used again. If configured `work_blocks` are missing around the workplace, assignment fails or the shop stays closed, and the NPC opens a close-only missing-block dialog. After a successful NPC store purchase, the owning NPC opens a close-only follow-up dialog chosen from `shop_messages` by friendship category and quantity bucket. Gift is disabled unless the player is holding an item, shows a hover hint, consumes one held item on success, and plays a reaction dialog with a single OKAY close button. Locked relic/player-locked items are rejected by the existing relic transfer guard. Bye closes the normal screen.

Talk opens a text input inside the existing dialog. Submitting sends the message to the server, validates NPC distance/state, shows a pending `...` in the dialog and nearby balloon while waiting, then replaces it with the validated LLM reply or a configured fallback. LLM provider settings are global in NPC `settings.toml`; `llm_message_usage` controls which message surfaces may use LLM text. V1 supports `openai_compatible` and `gemini` provider modes and expects JSON output with a single `message` field. When `llm_streaming = true`, OpenAI-compatible dialog replies stream partial direct-answer text into the dialog; Gemini keeps the non-streaming path.

Workplace dialogs can use LLM text when `llm_message_usage.work_application`, `work_manage`, `work_move`, `work_fire`, `assigned_workplace`, or `work_missing_blocks` are enabled. Prompt text for those surfaces lives under `[work]` in NPC `settings.toml`. Missing work-block prompts receive `{missing}`, `{requirements}`, `{workplace}`, and `{action}`.

The dialog header shows the NPC avatar, name, and a 10-icon friendship row. Positive levels render filled hearts, negative levels render angry icons, and remaining slots render empty hearts. Dialog body text uses the CKDM small bold font at 75% white opacity.

The inventory side menu has a Friends screen listing configured NPCs. Each row shows the NPC head, name, friendship hearts, friendship level, and mission summary. Hovering a row shows friendship points, gift availability for the current reset period, shop open/close timing, and active or available mission progress.

In multiplayer, the interacting player sees the dialog screen only. Other players within 30 blocks of the NPC see the overheard NPC line as a world-space balloon above the NPC. Discord receives every NPC dialog through the webhook, with the NPC name/avatar as the webhook identity and a linked Discord user mention when the interacting player has an account link.

While talking, the NPC briefly stops navigating and looks at the interacting player. During the local dialog typewriter reveal, NPCs play proximity-faded animalese sounds using the configured voice pitch. Nearby NPC balloons and hurt balloons do not play animalese.

When a player comes within the global NPC greeting radius and has not spoken to that NPC yet that in-game day, the NPC pauses briefly, looks at the player, and shows a world-space greeting balloon for the configured duration. If the player does not interact, the same NPC/player pair can greet again after the configured cooldown. If the player leaves the radius, that cooldown resets. Right-clicking the NPC for the first chat of the in-game day grants +25 friendship, stops greeting balloons for that day, and uses the configured first-chat message pool when the NPC already has a home and is awake.

Unhoused move-in NPCs use camper housing balloons instead of daily greeting balloons until they have a home or a rent contract has been issued. While a player is in greeting radius, the needs-house or lost-house balloon refreshes in world-space so it remains visible and is not replaced by normal daily greetings.

## Definition Fields

Each NPC is one TOML file:

- `id`: Stable NPC id. Used for state, commands, contracts, and future relationship data.
- `name`: Display name.
- `title`: Display subtitle.
- `skin`: Future skin resource id. Current renderer uses `textures/entity/npc/finn.png` for `finn` and a vanilla fallback for other NPC ids.
- `body_type`: Player-model shape. Supported values: `normal`, `slim`.
- `custom_animation`: Uses the GeckoLib playerlike NPC renderer instead of the EMF-compatible renderer path. Default `false`.
- `job`: Job behavior id. Supported ids: `adventurer`, `warrior`, `fashionista`. Unknown ids normalize to `adventurer`.
- `job_definition`: Static job behavior knobs loaded from TOML: `store`, `scan_interval_ticks`, `roam_radius`, and `work_scan_radius`. `store` is a store template id, not a shared stock instance.
- `schedule`: Static day schedule loaded from TOML. Each activity entry has `from_hour`, `to_hour`, and `activity`. Hours use a 24-hour clock where `00` is midnight.
- `store`: Legacy NPC-level store template id. New configs should use `job_definition.store` so multiple NPCs can share store pools without sharing rolled stock.
- `personality`: Future dialog/LLM seed fields: `llm_prompt`, `traits`, `speech_style`, `catchphrases`.
- `housing`: Move-in rules: `can_move_in`, `requires_bed`.
- `voice`: Animalese dialog voice settings: `animalese_pitch` (`high`, `med`, `low`, `lowest`), `pitch`, `volume`, and `radius`.
- `chat`: World chat settings. `call_names` are names/nicknames that can address the NPC from Minecraft or Discord chat; `minecraft_chat` and `discord_chat` toggle those channels.
- `gifts`: Gift ids/tags grouped by `loved`, `liked`, `disliked`, plus `daily_limit`, `reset_hour`, `llm_sentiment_prompt`, and reaction message pools for `loved`, `liked`, `disliked`, and `neutral`. Configured gift ids/tags decide friendship deterministically. If a player gives an unlisted item and gift LLM is enabled, the NPC asks the LLM for JSON `{ "message": "...", "gift_sentiment": "loved|liked|neutral|disliked" }`; invalid or failed sentiment falls back to `neutral`. `gifts.outgoing` configures NPC-to-player daily gifts with `enabled`, `radius`, `min_friendship_level`, `rare_friendship_level`, `follow_seconds`, `offer_messages`, `fallback_messages`, `llm_enabled`, `llm_prompt`, weighted `pool`, weighted `rare_pool`, and additive `extra_pool` / `extra_rare_pool` lists. Each pool entry has `item`, `qty`, and `weight`.
- `missions`: NPC quest settings. `enabled`, `offer_radius`, `offer_balloon_messages`, and weighted-like `pool` entries. Each mission has `id`, `category` (`task` or `fetch`), `event`, `event_desc`, `quest_text`, `pass_id`, `xp`, optional `chowcoins`, `goal`, `fetch_item`, `fetch_count`, and optional message pools.
- `friendship_messages`: Optional per-NPC category message additions for `interact`, `gift`, `hurt`, `wake`, `greeting`, and `first_daily_chat`. Categories are `hatred`, `enemy`, `dislike`, `neutral`, `okay`, `good_friends`, and `best_friends`. Message pools resolve in order: built-in defaults, shared `friendship_messages.toml`, then NPC-specific `friendship_messages`. Each configured layer joins the inherited pool and is inserted twice, so local additions have roughly 2:1 weight against inherited generic lines.
- `shop_messages`: Optional post-purchase message overrides with `single`, `normal`, and `bulk` buckets. Each bucket has the same friendship categories as `friendship_messages`. Supports `{player}`, `{npc}`, `{item}`, `{quantity}`, `{total}`, `{friendship_level}`, and `{friendship_points}`.
- `camper_messages`: Optional uncategorized camp message pools: `needs_house_balloon`, `needs_house_dialog`, `lost_house_balloon`, and `lost_house_dialog`. Supports `{player}` and `{npc}`.
- `hurt_messages`: NPC speech pool used every third hit from the same player. Supports `{player}`.
- `wake_messages`: NPC speech pool used when a sleeping NPC is right-clicked. Supports `{player}`.
- `npc_interaction_messages`: Optional NPC-specific world-space lines for NPC-to-NPC micro interactions. Supports `{npc}` and `{other}` and is added on top of shared generic interaction lines.
- `work_blocks`: Required nearby blocks/entities for workplace assignment and selling. Each entry has `id`, `count`, and optional `display_name`. Block ids, block tags like `#minecraft:beds`, entity ids like `minecraft:item_frame`, and entity tags are supported. Counts are scanned around the workplace using `job_definition.work_scan_radius`; bed halves count as one bed.

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
work_missing_blocks = true

[work]
missing_work_blocks_llm_prompt = "The player tried to use your workplace, but required work blocks are missing nearby. Missing: {missing}. Full requirement: {requirements}. Workplace: {workplace}. Reply in character and tell them what to add before you can work."
```

Example workplace requirements:

```toml
work_blocks = [
	{ id = "minecraft:barrel", count = 3, display_name = "barrels" },
	{ id = "minecraft:item_frame", count = 2, display_name = "item frames" },
	{ id = "#minecraft:beds", count = 1, display_name = "bed" },
]
```

NPC quest behavior:

- NPC quests live in each NPC TOML under `[missions]` / `missions.pool`.
- Quests are offered only during town-center meetup (`15:00-20:00`) and reset at in-game `15:00`.
- New quest offers require the NPC to have a valid home bed and an assigned workplace. Active quest rewards can still be claimed if the NPC later loses either assignment.
- A player can accept at most 4 active NPC quests per reset period.
- Declining an NPC quest suppresses that NPC's offer for 1 in-game hour; the same daily offer remains until the next 15:00 reset.
- Task quests progress from existing battlepass mission signals such as `minecraft:monster_killed` or `cobblemon:pokemon_caught`.
- Fetch quests complete when the player returns to the NPC with the configured item; the required item count is consumed on reward claim.
- Rewards grant configured battlepass XP to `pass_id` and optional `chowcoins` immediately. Unclaimed completed NPC quests expire at the next 15:00 reset.

Example NPC quest config:

```toml
[missions]
enabled = true
offer_radius = 7.0
offer_balloon_messages = ["@quest_log.png {quest_text}"]
pool = [
  { id = "hunt_mobs", category = "task", event = "minecraft:monster_killed", event_desc = "Defeat {goal} Monsters", quest_text = "Thin out monsters near town.", pass_id = "combat", xp = 100, chowcoins = 50, goal = 10 },
  { id = "fetch_beef", category = "fetch", event_desc = "Bring {goal} Cooked Beef", quest_text = "Bring food for patrol.", pass_id = "cozy", xp = 80, chowcoins = 25, fetch_item = "minecraft:cooked_beef", fetch_count = 4 },
]
```

NPC-to-NPC micro interaction settings live in NPC `settings.toml`:

```toml
[npc_interactions]
enabled = true
radius = 7.0
duration_seconds = 12
cooldown_min_hours = 2
cooldown_max_hours = 4
balloon_refresh_seconds = 6
messages = ["Talking with {other}...", "Catching up with {other}."]
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

Resident state tracks each NPC's entity UUID, camp position, assigned home bed, assigned workplace block, whether the NPC was fired from work, whether a contract has been given, recent hurt records, per-player conversation history, per-player gift counters, last hurt player/streak, death state, scheduled respawn day, and camper return reason. World state also tracks the latest camping block position, the active unhoused camper id, and the next camper cooldown tick. Hurt history stores only every third same-player hit event and keeps the latest 10 records. This is world data, separate from static JSON definitions and runtime brain state.

Greeting state is tracked per NPC/player. It stores the last greeting day, the real-time greeting cooldown expiry, and the first-chat day used to stop repeat greetings and prevent duplicate daily friendship rewards.

NPC quest state is tracked per player. It stores the active 15:00 reset period, accepted NPC quests with their next 15:00 expiry tick, completed NPC ids for that period, and per-NPC decline cooldown ticks. When the period changes, active unclaimed NPC quests expire only after their stored expiry tick.

Outgoing NPC gift state is tracked per NPC/player. It stores the current scheduled gift day/hour and the last offered day, so a ready gift attempt either delivers or times out, then cools down until the next in-game day.

Per NPC/player friendship is stored as points from `-1000` to `1000`, defaulting to `100` (`Lv. 1`). The derived friendship level is `-10` to `10`. Categories map as: `-10 hatred`, `-9..-6 enemy`, `-5..-3 dislike`, `-2..2 neutral`, `3..5 okay`, `6..9 good_friends`, `10 best_friends`.

Each NPC/player conversation keeps at most 30 records. The world also keeps at most 30 global NPC-relevant events. This is the foundation for future LLM prompt context.

## Runtime Flow

- `NpcDefinition`: static NPC identity loaded from TOML.
- `NpcJobDefinition`: static job behavior values loaded from TOML.
- `NpcScheduleDefinition`: static 24-hour schedule loaded from TOML.
- `NpcResidentState`: saved world data for home bed, camp, contract, and spawned entity UUID.
- `ChowNpcEntity`: the Minecraft entity that owns the SBL brain, interacts, renders, and exposes debug state.
- `NpcSmartBrain`: SBL sensors, core tasks, and idle behavior ordering for town NPCs.
- `NpcSmartBrainOverrides`: SBL-backed temporary interrupt behavior state for reactive events, such as retaliation or running away from danger.

## Memory Context

NPC memory records the current player-specific conversation and a small global event stream. Current captured events:

- Player interacts with NPC.
- NPC dialog message.
- Player hurts NPC.
- NPC hurt response.
- NPC death and killer when known.
- NPC respawn.
- Player death messages.
- Player gives an item to an NPC.
- NPC gives a pending gift item to a player.
- NPCs pause to chat with each other in-world.

Future LLM prompt building should read `NpcStore.llmContext(npcId, player)` and inject global events before player-specific conversation records. The context can also carry the current 24-hour schedule hour.

The LLM context includes the current friendship snapshot: points, level, and category. Gift history is two-way: player-to-NPC gifts and NPC-to-player gifts are both saved into conversation history, and a short player memory is recorded for each gift direction. Treat this as high-priority tone context for future generated NPC replies.

Current built-in activities are `work`, `home`, and `sleep`. If an older NPC config has no schedule, the loader supplies the default `06-20 work`, `20-22 home`, `22-06 sleep` routine. During `sleep`, Finn walks to his assigned bed and uses the sleeping pose when close enough.

Right-clicking a sleeping NPC wakes it, opens wake-specific dialog, pauses sleep while talking, then lets the brain return it to sleep when the dialog window ends and the schedule is still `sleep`.

When the same player hits an NPC three times in a row, the NPC speaks a random `hurt_messages` line in a nearby world-space balloon. Those third-hit events are persisted in `NpcResidentState` with timestamp and player identity.

The third same-player hit also starts a short SBL override. The NPC equips a temporary random weapon, chases the attacker, then runs two scripted attack pulses with synced custom arm animation, attack sound, direct damage, and knockback when in range. The NPC renderer uses the vanilla player model plus an item-in-hand layer, and the custom animation copies transforms to the sleeve/jacket layer so the outer skin follows the swing. NPCs also run away for 3 seconds when they step on fire, soul fire, campfire, or soul campfire. Overrides pause normal schedule/job navigation and then fall back to the regular SBL idle brain.

When Jade is installed, hovering an NPC shows the NPC display name and the hovering player's friendship category for that NPC with a small heart, empty heart, or angry icon.

Gift limits are per NPC/player and reset by in-game schedule period. Finn defaults to one gift per in-game day, resetting at 05:00. Gifts adjust friendship only: neutral `+5`, liked `+25`, loved `+50`, disliked `-50`. Hitting an NPC changes friendship by `-10`; killing one changes friendship by `-300`; first daily chat changes friendship by `+25`.

NPC-to-player gifts start at friendship level 5 by default. Each NPC/player pair rolls one scheduled hour per in-game day from that NPC's non-sleep schedule hours. If the player is nearby when the hour is ready, the NPC creates a pending weighted random gift, shows a `@gift.png` balloon, and follows the player for `follow_seconds`. The item is only granted when the player right-clicks the NPC while a pending gift exists. Ignoring the chase does not delete the gift; the pending gift can be claimed later, including on a later in-game day, and the NPC can remind the player again once per day. Claiming opens the normal NPC dialog with a `THANKS` close button and sends a snackbar using the received item as icon. Gift LLM thinking and replies stay in that dialog, not in world-space balloons. Friendship level 9+ uses the rare weighted pool when available. LLM gift messages receive `{player}`, `{npc}`, `{item}`, and `{quantity}` in the prompt; fallback messages use the same placeholders.

NPC-to-NPC micro interactions run after gifting and daily greeting checks, before normal scheduled movement. When two awake, non-talking NPCs in non-sleep schedule activity meet within `npc_interactions.radius`, they can pause, walk toward each other, face each other, and show short world-space balloons for `duration_seconds`. There is no daily cap; each NPC gets a random in-game cooldown between `cooldown_min_hours` and `cooldown_max_hours` before it can start another micro interaction. Each interaction records a global summary event for future LLM context.

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
- `/npc debug llm`: show current NPC LLM enabled/provider/model settings plus the latest in-memory LLM failures, including HTTP status such as `429` or `403` when the provider returns one.
- `/npc debug time <multiplier>`: speed up day time for debugging schedules. Use `1` to reset. Allowed range is `1` to `240`.
- `/npc debug balloon <id> <message>`: show a timed test balloon above the spawned NPC for nearby players.
- `/npc animation debug`: toggle a Steve-textured Chow Kingdom NPC debug entity for the command player.
- `/npc animation custom_animation true|false`: toggle GeckoLib custom animation mode on the active debug Steve, or the NPC under the crosshair when no debug Steve is active.
- `/npc animation idle`: enable custom animation mode and run the idle alias on the active debug Steve, or the NPC under the crosshair when no debug Steve is active.
- `/npc animation walk`: enable custom animation mode and run the walk alias on the active debug Steve, or the NPC under the crosshair when no debug Steve is active. The current weapon-socket reset file only defines `idle`, so this command is expected to fail until walk clips are re-authored.
- `/npc animation attack`: enable custom animation mode and run the attack alias on the active debug Steve, or the NPC under the crosshair when no debug Steve is active. The current weapon-socket reset file only defines `idle`, so this command is expected to fail until attack clips are re-authored.
- Replaying the same one-shot debug animation forces a GeckoLib controller reset, so repeated `/npc animation attack` calls restart the attack clip instead of holding the previous finished pose.
- `/npc animation reload` or `/npc animations reload`: reload animation IDs from `playerlike.animation.json` and request a client resource reload. The resource reload sees files from the active client resource pack/classpath; in a dev run, source edits may still require Gradle resource processing if the client is serving `build/resources/main`.
- `/npc animation wear <item>` or `/npc animations wear <item>`: equip the active debug Steve or looked-at NPC with `hat`, `chestplate`, `leggings`, `boots`, `sword`, `left_sword`, `left <item>`, any item id such as `minecraft:iron_sword`, or `clear`. Held items render directly on authored Gecko hand-item socket bones using raw item model context plus one fixed item-model-space adapter. Vanilla armor layers render on the normal/playerlike NPC renderers; the current Gecko custom renderer does not draw vanilla armor layers yet.
- `/npc animation itemrot <x> <y> <z>`: add temporary synced raw item rotation offsets after the fixed held-item adapter. `/npc animation itemrot reset` clears the offsets.
- `/npc animation itempos <x> <y> <z>`: add temporary synced item position offsets in socket space. `/npc animation itempos reset` clears the position offsets.
- `/npc animation itemscale <scale>`: add a temporary synced uniform item scale multiplier. `/npc animation itemscale reset` returns to `1`.
- `/npc animation itemrotorder <order>`: change the debug offset application order. Accepted values are `xyz`, `xzy`, `yxz`, `yzx`, `zxy`, and `zyx`.
- `/npc animation itemrotspace socket|item`: choose whether debug offsets apply in socket space before the item adapter or item-local space after the adapter. Use `socket` when calibrating visible roll/pitch/yaw against the debug weapon bones.
- Gecko hand-item socket default rotation is `[0, 0, 0]`, meaning a held sword points forward and its crossguard is vertical.
- `/npc animation <animation>` or `/npc animations <animation>`: play any animation key from `playerlike.animation.json`; suggestions are refreshed from the JSON file.
- `/ck npc ...` and `/chowkingdom npc ...`: aliases.

## Jade

When Jade is installed, beds assigned through rent contracts show the owner, such as `Finn's Bed`.

## Extension Points

Add future runtime decision logic as SmartBrainLib behaviors in `NpcSmartBrain`. Keep static behavior knobs in `NpcJobDefinition` and schedules in `NpcScheduleDefinition`.

Add future data fields to `NpcDefinition` instead of hard-coding per-NPC behavior in the entity.

Add future store, gift, relationship, and quest systems by reading the stable NPC id and definition fields. The entity should stay thin: identity, saved positions, interaction entrypoint, short-lived talking state, debug state, and per-tick delegation.
