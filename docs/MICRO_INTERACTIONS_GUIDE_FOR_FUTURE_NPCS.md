# Micro Interactions Guide For Future NPCs

NPC-to-NPC micro interactions use paired authored exchanges. One interaction picks one exchange, then shows `line` over the first NPC and `response` over the second NPC.

Runtime pacing is witness-first: authored exchanges normally start only when a player is close enough to receive balloons. The interaction window can stay open longer than the balloon duration, so late passersby can still catch the line without refreshing the same balloon repeatedly for one player.

## Content File

Put shared content in:

```text
<game config>/gisketchs_chowkingdom_mod/npcs/micro_interactions.toml
```

Use NPC-local content only when an NPC needs lines that should live next to its normal config:

```toml
npc_interaction_exchanges = [
  { id = "unique_id", topic = "topic_id", line = "...", response = "...", weight = 1.0 },
]
```

## Exchange Fields

- `id`: stable unique id. Use lowercase words joined by underscores.
- `topic`: small category such as `town_patrol`, `training`, `weather`, `pokemon_match`, or `shop_stock`.
- `line`: first NPC balloon.
- `response`: second NPC balloon.
- `source_emote`: optional emote catalog id for the first NPC, such as `wave`, `clap`, `shrug`, or `proud`. Do not use sitting/lying emotes here.
- `target_emote`: optional emote catalog id for the second NPC. Blank lets runtime auto-pick a safe gesture sometimes.
- `weight`: optional selection weight. Default `1.0`.
- `source_ids`: optional NPC ids that can start this exchange.
- `target_ids`: optional NPC ids that can answer this exchange.
- `source_tags`: optional tags the first NPC must have.
- `target_tags`: optional tags the second NPC must have.
- `required_spawned_ids`: optional NPC ids that must be alive in the world before this exchange can be selected. Use this for any concrete third-party NPC named in `line` or `response` who is not already covered by `source_ids` or `target_ids`.

## Solo Moments

`micro_interactions*.toml` can also define rare one-NPC ambient balloons. These are authored text only; they do not call LLMs.

```toml
[[solo_moments]]
id = "town_checks_paths"
topic = "roam"
source_tags = ["town"]
activities = ["work", "meetup"]
line = "These paths tell you where people really walk."
emote = "lookout"
weight = 2.0
```

- `source_ids` and `source_tags` gate which NPC can say the line.
- `required_spawned_ids` gates named third-party NPC references the same way it does for paired exchanges.
- `activities` is optional. When present, it limits the line to schedule activities such as `work`, `home`, `meetup`, or `pokemon_roam`.
- `emote` is optional. Ambient solo moments may use normal ambient gestures or ambient posture ids such as `sit_cool`; posture ids cancel when the NPC talks, moves, takes damage, or enters combat/battle locks.
- Keep `line` short and observational. It should sound like a thought or small daily action, not a quest prompt.
- Solo moment balloons trigger for a player who enters close range during the active ambient event. Do not write lines that require the player to have seen the NPC from the first tick of the action.

## Tags

NPCs can define manual tags:

```toml
interaction_tags = ["town", "strict", "fire", "mentor"]
```

The runtime also derives useful tags from config:

- `mentor` from NPCs with a class.
- `store_<id>` from shop/store id.
- `pokemon_trainer`, `gym_leader`, `elite_four`, `champion`, `rival`, and `professor` from trainer-style ids/titles.
- `town` for non-trainer NPCs.

## Authoring Targets

- Background NPC: 10-20 exchanges.
- Normal resident: 40-60 exchanges.
- Key resident or mentor: 80-120 exchanges.
- Iconic pair: 20-30 pair-specific exchanges.
- Shared tag pack: 50+ exchanges per broad theme.

Do not author every permutation. Use tag packs for scale, then add pair-specific exchanges only for relationships players will notice.

## Style Rules

- Keep each balloon short enough to read above a moving NPC.
- Make the two lines sound like a real exchange.
- Assume a player may catch either line mid-interaction; each line should make sense alone.
- Avoid narrator text like `Talking with {other}` except as emergency fallback.
- Avoid long lore dumps; one idea per balloon.
- Do not depend on hidden state unless the exchange is gated by a tag or topic that makes it safe.
- Prefer personality and daily-life texture over exposition.

## Review Checklist

- No exact duplicate ids.
- No exact duplicate lines within the same NPC or topic.
- No claims that contradict NPC config, class, store, or role.
- Pair-specific content uses `source_ids` and `target_ids`.
- Trainer-only content uses trainer tags instead of listing every trainer id.
- New NPCs get useful content with tags alone, before custom pair lines are added.
- Any line naming a concrete NPC outside its `source_ids`/`target_ids` has `required_spawned_ids`.
