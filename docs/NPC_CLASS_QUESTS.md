# NPC Class Quests

Class mentor quests are separate from daily NPC quests. They are one-time, non-expiring questlines stored per player and class. Daily NPC quests stay generic and repeatable.

## Runtime Decisions

- `Training` no longer grants an unknown class directly. It starts or advances the mentor questline for the NPC's configured `class`.
- Each class TOML owns its mentor quest data under `[mentor_quest]`.
- Quest state lives in `RoleStore` as per-player class mentor progress, not in daily NPC quest state.
- Vow steps are dialogue gates and advance after the mentor introduces the oath.
- Offering steps can be fetch, craft/task, or food-chain preparation.
- Discipline and Signature Trial steps reuse existing battlepass/NPC event hooks where possible.
- Payment is a new class unlock license fee: starter unlocks cost `25,000` chowcoins and upgrade unlocks cost `50,000` chowcoins.
- Existing class changes keep their old higher replacement costs: starter changes cost `50,000`, upgrade changes cost `100,000`.
- Mentor Duel uses the existing non-lethal NPC bossfight. Winning the duel unlocks the class, grants starter items, sends a title line, broadcasts the unlock, and opens an LLM-backed mentor ceremony.
- LLM prompts receive the whole questline, current step, current progress, class classification, unlock cost, mentor NPC, and step flavor prompt.

## Class Questlines

| Class | Mentor | Offering | Discipline | Signature Trial | Unlock Title |
|---|---|---|---|---|---|
| Warrior | Finn | Bring 12 iron ingots for practice blade repairs. | Defeat 12 monsters on the road. | Defeat 8 Overworld skeletons. | Oathsworn Blade |
| Rogue | Ezio | Bring 2 ender pearls for escape routes. | Travel 750 blocks on foot. | Defeat 6 Overworld skeleton sentries. | Hidden Creedblade |
| Archer | Huntress Wizard | Craft 32 arrows after the rite begins. | Travel 1000 blocks on foot to read terrain. | Defeat 8 Overworld skeleton archers. | Greenwood Stringkeeper |
| Wizard | Gandalf | Bring 6 amethyst shards for focus work. | Craft 3 fire charges. | Defeat 4 Nether blazes. | Grey Sparkbearer |
| Priest | Katara | Cook 1 fresh Farmer's Delight vegetable soup after the step begins. | Breed 3 animals. | Catch 8 fish. | Tidebound Carekeeper |
| Berserker | Zagreus | Bring 6 blaze powder for the red line. | Defeat 18 monsters while keeping count. | Defeat 5 Nether blazes. | Red Oath Breaker |
| Paladin | Tarnished | Bring 8 gold ingots for oath weight. | Defeat 10 Overworld zombies. | Defeat 3 Nether wither skeletons. | Gracebound Sentinel |
| Bounty Hunter | Aloy | Craft 32 arrows as contract stock. | Catch 3 Pokemon as living trail work. | Defeat 8 spiders for a target contract. | Redgrass Seeker |
| Forcemaster | Vi | Bring 16 redstone dust for gauntlet power. | Travel 1500 blocks for pressure footwork. | Defeat 12 monsters with clean impact. | Pressureline Breaker |
| Bard | Venti | Prepare 1 fresh Farmer's Delight mixed salad after the step begins. | Trade with villagers 4 times to read the room. | Travel 600 blocks on foot with the wind. | Stormsong Namebearer |
| Witcher | Geralt | Cook 1 fresh Farmer's Delight beef stew after the step begins. | Defeat 15 monsters as bestiary work. | Defeat 4 endermen as a contract. | Silver-Signed Hunter |
| War Archer | Legolas | Bring 48 arrows for the company. | March 1200 blocks on foot. | Defeat 12 Overworld skeleton archers. | White-Fletched Captain |
| Tundra Archer | Traxex | Bring 16 snowballs for breath control. | Travel 1200 blocks on foot in silence. | Defeat 4 strays. | Frost-Quiet Arrow |
| Elemental Wizard | Invoker | Bring 2 blaze rods for Exort focus. | Smelt 16 glass for heat and clarity. | Catch 3 Fire, Water, or Electric Pokemon. | Quas-Wex-Exort Adept |

## Config Surface

Each class file under `runs/client/config/gisketchs_chowkingdom_mod/roles/classes` has:

```toml
[mentor_quest]
mentor_npc_id = "mentor_id"
title = "Questline Title"
intro_message = "Short class premise."
unlock_title = "Earned Title"
announcement = "{player} completed {npc}'s questline and unlocked {class} as {title}."
steps = [
  { id = "class_vow", skeleton = "vow", kind = "dialogue", title = "Vow", objective = "..." },
  { id = "class_offering", skeleton = "offering", kind = "fetch", item = "minecraft:item", qty = 1 },
  { id = "class_discipline", skeleton = "discipline", kind = "task", event = "minecraft:event", goal = 1, filters = { key = "value" } },
  { id = "class_signature_trial", skeleton = "signature_trial", kind = "task", event = "minecraft:event", goal = 1 },
  { id = "class_license", skeleton = "payment", kind = "payment" },
  { id = "class_duel", skeleton = "mentor_duel", kind = "duel" },
]
```

Supported step kinds now used by configs:

- `dialogue`: LLM-backed vow or mentor intro; advances after opening.
- `fetch`: player brings and consumes a configured item count.
- `food_chain`: player must create the configured Farmer's Delight food after the step begins, then bring the marked stack back.
- `task`: tracks an existing battlepass signal with optional filters.
- `payment`: checks license capacity and charges the new unlock fee.
- `duel`: starts the existing non-lethal NPC bossfight; victory unlocks the class.
