# CKDM Micro Interaction Schema

Runtime loader accepts any TOML file in the NPC config directory whose file name starts with `micro_interactions`.

```toml
[[exchanges]]
id = "unique_id"
topic = "topic_id"
source_ids = ["npc_id"]
target_ids = ["other_npc_id"]
required_spawned_ids = ["third_party_npc_id"]
source_tags = ["optional_tag"]
target_tags = ["optional_tag"]
line = "short first NPC balloon"
response = "short second NPC balloon"
weight = 1.0

[[trainer_exchanges]]
id = "trainer_unique_id"
topic = "pokemon_training"
source_tags = ["pokemon_trainer"]
line = "short first trainer balloon"
response = "short second trainer balloon"
weight = 1.0
```

`source_ids`, `target_ids`, `source_tags`, `target_tags`, and `required_spawned_ids` are optional. Empty arrays should be omitted. Use `required_spawned_ids` for concrete NPC names mentioned in text that are not already the source or target.

Core derived tags include `town`, `mentor`, `pokemon_trainer`, `gym_leader`, `elite_four`, `champion`, `rival`, and `professor`.
