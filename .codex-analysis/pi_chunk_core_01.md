Generate valid TOML only. No markdown, no comments, no explanation.
Output exactly 140 [[exchanges]] entries.
No [[trainer_exchanges]] in this file.
All text ASCII only. No romance, no dating, no grief, no modern slang. Each line/response <= 95 chars.
Use this TOML shape for every entry:
[[exchanges]]
id = "core01_unique_id"
topic = "topic_id"
source_ids = ["npc_id"]
target_ids = ["npc_id"]
line = "short first NPC balloon"
response = "short second NPC balloon"
weight = 1.0

NPCs for this pack: finn, shoumai, prof_chowfan, aang, zuko, katara, toph.
Tone hints:
Finn brave friendly reckless adventure. Shou Mai practical warm cozy town leader teasing. Prof Chowfan smart busy warm league organizer paperwork chaos. Aang playful gentle pacifist wind. Zuko disciplined intense fire honor. Katara caring firm water. Toph blunt funny earth senses ground.
Make many pair-specific exchanges between these NPCs plus some source_tags = ["town"] or ["mentor"] entries. Cozy witty town life, patrols, training, roads, plaza, chores, weather, league paperwork.
Ids must start with core01_ and be unique.
