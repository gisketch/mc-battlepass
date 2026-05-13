# NPC Class Quests

Class mentor quests are separate from daily NPC quests. They are one-time, non-expiring questlines stored per player and class. Daily NPC quests stay generic and repeatable.

## Runtime Decisions

- `Training` no longer grants an unknown class directly. It starts or advances the mentor questline for the NPC's configured `class`.
- Each class TOML owns its mentor quest data under `[mentor_quest]`.
- Quest state lives in `RoleStore` as per-player class mentor progress, not in daily NPC quest state.
- Vow steps are dialogue gates and advance after the mentor introduces the oath.
- Offering steps can be fetch, craft/task, or food-chain preparation.
- Discipline and Signature Trial steps reuse existing battlepass/NPC event hooks where possible.
- Battle trials can use `kind = "timed"` with `time_window_seconds`. Timed mentor steps use the same sliding window logic as timed NPC quests: only matching events inside the latest window count until the goal locks complete.
- Some battle trials intentionally stay untimed endurance checks. Those use larger goals, usually 100 monster kills, when the fantasy is patrol, bestiary work, or oath service instead of burst damage.
- Payment is a new class unlock license fee: starter unlocks cost `25,000` chowcoins and upgrade unlocks cost `50,000` chowcoins.
- Existing class changes keep their old higher replacement costs: starter changes cost `50,000`, upgrade changes cost `100,000`.
- Mentor Duel uses the existing non-lethal NPC bossfight. Winning the duel unlocks the class, grants starter items, sends a title line, broadcasts the unlock, and opens an LLM-backed mentor ceremony.
- LLM prompts receive the whole questline, current step, current progress, timed window when present, class classification, unlock cost, mentor NPC, and step flavor prompt.

## Class Questlines

| Class | Mentor | Offering | Discipline | Signature Trial | Unlock Title |
|---|---|---|---|---|---|
| Warrior | Finn | Bring 12 iron ingots for practice blade repairs. | Defeat 100 monsters on the roads. | Timed: defeat 4 Overworld skeletons in 35s. | Oathsworn Blade |
| Rogue | Ezio | Bring 2 ender pearls for escape routes. | Travel 750 blocks on foot. | Timed: defeat 3 Overworld skeleton sentries in 20s. | Hidden Creedblade |
| Archer | Huntress Wizard | Craft 32 arrows after the rite begins. | Travel 1000 blocks on foot to read terrain. | Timed: defeat 4 Overworld skeleton archers in 30s. | Greenwood Stringkeeper |
| Wizard | Gandalf | Bring 6 amethyst shards for focus work. | Craft 3 fire charges. | Timed: defeat 3 Nether blazes in 45s. | Grey Sparkbearer |
| Priest | Katara | Cook 1 fresh Farmer's Delight vegetable soup after the step begins. | Breed 3 animals. | Catch 8 fish. | Tidebound Carekeeper |
| Berserker | Zagreus | Bring 6 blaze powder for the red line. | Timed: defeat 6 monsters in 20s while keeping count. | Timed: defeat 3 Nether blazes in 35s. | Red Oath Breaker |
| Paladin | Tarnished | Bring 8 gold ingots for oath weight. | Defeat 100 monsters as oath service. | Timed: defeat 2 Nether wither skeletons in 90s. | Gracebound Sentinel |
| Bounty Hunter | Aloy | Craft 32 arrows as contract stock. | Catch 3 Pokemon as living trail work. | Timed: defeat 5 spiders in 25s for a target contract. | Redgrass Seeker |
| Forcemaster | Vi | Bring 16 redstone dust for gauntlet power. | Travel 1500 blocks for pressure footwork. | Timed: defeat 6 monsters in 20s with clean impact. | Pressureline Breaker |
| Bard | Venti | Prepare 1 fresh Farmer's Delight mixed salad after the step begins. | Trade with villagers 4 times to read the room. | Travel 600 blocks on foot with the wind. | Stormsong Namebearer |
| Witcher | Geralt | Cook 1 fresh Farmer's Delight beef stew after the step begins. | Defeat 100 monsters as bestiary work. | Defeat 4 endermen as a sparse contract, untimed. | Silver-Signed Hunter |
| War Archer | Legolas | Bring 48 arrows for the company. | March 1200 blocks on foot. | Timed: defeat 5 Overworld skeleton archers in 35s. | White-Fletched Captain |
| Tundra Archer | Traxex | Bring 16 snowballs for breath control. | Travel 1200 blocks on foot in silence. | Timed: defeat 3 strays in 45s. | Frost-Quiet Arrow |
| Arcane Wizard | Invoker | Bring 12 amethyst shards for resonance focus. | Travel 800 blocks on foot for vector work. | Timed: defeat 3 endermen in 60s. | Arcane Sequencer |
| Fire Wizard | Invoker | Bring 4 blaze rods for Exort focus. | Smelt 24 glass for heat and clarity. | Timed: defeat 5 Nether blazes in 60s. | Exort Adept |
| Frost Wizard | Invoker | Bring 8 blue ice for Quas focus. | Craft 8 snow blocks for control drills. | Defeat 6 strays. | Quas Adept |
| Water Wizard | Katara | Bring 8 kelp for river study. | Catch 8 fish for current patience. | Catch 3 Water Pokemon. | Aqua Adept |
| Earth Wizard | Invoker | Bring 32 deepslate for Terra focus. | Craft 16 stone bricks for structure work. | Smelt 12 iron ingots. | Terra Adept |
| Wind Wizard | Venti | Bring 16 feathers for Wex focus. | Travel 1200 blocks on foot. | Catch 3 Flying Pokemon. | Wex Adept |

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
  { id = "class_signature_trial", skeleton = "signature_trial", kind = "timed", event = "minecraft:event", goal = 3, time_window_seconds = 30, filters = { key = "value" } },
  { id = "class_license", skeleton = "payment", kind = "payment" },
  { id = "class_duel", skeleton = "mentor_duel", kind = "duel" },
]
```

Supported step kinds now used by configs:

- `dialogue`: LLM-backed vow or mentor intro; advances after opening.
- `fetch`: player brings and consumes a configured item count.
- `food_chain`: player must create the configured Farmer's Delight food after the step begins, then bring the marked stack back.
- `task`: tracks an existing battlepass signal with optional filters.
- `timed`: tracks an existing battlepass signal with optional filters inside `time_window_seconds`.
- `payment`: checks license capacity and charges the new unlock fee.
- `duel`: starts the existing non-lethal NPC bossfight; victory unlocks the class.
