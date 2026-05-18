# Micro Interactions Guide For Future NPCs

NPC-to-NPC micro interactions use paired authored exchanges. One interaction picks one exchange, then shows `line` over the first NPC and `response` over the second NPC.

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
- `weight`: optional selection weight. Default `1.0`.
- `source_ids`: optional NPC ids that can start this exchange.
- `target_ids`: optional NPC ids that can answer this exchange.
- `source_tags`: optional tags the first NPC must have.
- `target_tags`: optional tags the second NPC must have.

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
