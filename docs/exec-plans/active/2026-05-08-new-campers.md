# New Campers

## Goal

Rotate configured NPCs through the camping block. Only one unhoused camper can wait at camp. After that NPC gets a bed, camp waits a random 24-48 Minecraft hours, then introduces another unhomed NPC.

## Acceptance

- Camp picks a random eligible NPC from config, not always Finn.
- Existing housed/live/unhoused active NPCs block duplicate camper spawns.
- Unhoused dead camper respawns at camp, not a bed.
- Breaking a housed NPC bed clears home and returns that NPC to camp with lost-bed dialog.
- New camper and lost-bed dialog use config message pools without friendship categories.
- LLM can be enabled separately for camper need-house and lost-house dialog.
- New random camper arrivals send a server-wide snackbar with the NPC head icon and relay to Discord.
- Docs include test steps.

## Steps

- [x] Read NPC docs and camp/dialog/state code.
- [x] Patch config/state/model.
- [x] Patch spawn, home assignment, death, bed-break, cooldown tick.
- [x] Update docs.
- [x] Run Gradle build.

## Edge Cases Covered

- Active unhoused camper blocks all other camper spawns.
- Dead unhoused camper respawns at camp and keeps camp reserved.
- Broken or missing home bed clears home and returns that NPC to camp before new campers.
- Removed camping block pauses automatic spawns until camp is placed or used again.
- Fully housed pool produces no new camper.
- Existing live entity prevents duplicate spawn for same NPC id.
- NPC time and shipping bin payout now route through shared `ChowClock`, which reads Better Days `betterdays-common.toml` when present. Store reset periods intentionally remain real-life time.

## Manual Test Steps

1. Add at least two NPC JSON files under `config/gisketchs_chowkingdom_mod/npcs`.
2. Set `settings.toml` campers cooldown to `1..1` for fast testing.
3. Start client/server, place `gisketchs_chowkingdom_mod:camping_block`.
4. Verify exactly one random NPC appears near camp and shows a needs-house balloon.
5. Verify all players receive a snackbar: `NEW CAMPER AT THE CAMPING BLOCK`, NPC head icon, and welcome/rent-contract content.
6. If Discord webhook is enabled, verify Discord receives the camper arrival embed using the NPC avatar.
7. Right-click that NPC, verify rent contract is granted and needs-house dialog appears.
8. Right-click a bed with the contract, verify home assigned snackbar and contract consumed outside creative.
9. Wait one Minecraft hour or run `/npc debug time 240`; verify another unhomed NPC appears and announces once.
10. Try right-clicking camp while an unhoused camper exists; verify no second camper spawns.
11. Kill an unhoused camper, wait until respawn time, verify same NPC returns to camp without a new-arrival announcement.
12. Break a housed NPC's assigned bed; verify the NPC returns to camp, says lost-house balloon, and gives a new contract on interaction.
13. Assign every configured NPC a bed; verify camp no longer spawns new campers.

## Better Days Test Steps

1. In the active instance `config/betterdays-common.toml`, set `speedMethod="MINUTES"`, `daySpeedMinutes=0.5`, and `nightSpeedMinutes=0.5`.
2. Keep Better Days `dayStart=23500.0` and `nightStart=12500.0`.
3. Start client and enable `/npc debug` on a housed NPC.
4. Advance time slowly; verify debug hour changes from `06` at day start toward `18` at configured night start.
5. Verify NPC activity uses schedule labels: `work` during configured work hours, `home` near evening, `sleep` at configured sleep hours.
6. Set Finn gift reset to `5`; gift once before configured 05:00, advance past configured 05:00, verify gift reset.
7. Kill a housed NPC before configured 05:00; advance to configured 05:00, verify respawn only after that clock hour.
8. Set camper cooldown `1..1`; assign a camper bed, advance one configured clock hour, verify next camper spawns.
9. Put items in shipping bin before configured 05:00; advance past configured 05:00, verify payout happens once.
10. Open a daily store before and after configured in-game 05:00; verify daily stock does not reset from in-game time and still follows real-life store reset.
11. Sleep through night with Better Days sleep acceleration; verify NPC clock jumps consistently and does not duplicate first daily chat rewards.
12. Repeat with a different Better Days day/night split, then verify sleep/home schedules still key off displayed hour.

## Decision Log

- 2026-05-08: Added `shoumai.toml` under the playtest `runs/client` config, added `fashionista` as a supported NPC job id, and moved NPC store routing to `job_definition.store`.
- 2026-05-08: Store ids on jobs are now store templates/pools; NPC shops use per-NPC stock keys so two NPCs can share `cosmetics.toml` while rolling different stock.

## Progress Log

- 2026-05-08: Added Shou Mai NPC config with custom lore, camper dialog, shop lines, and fashionista-to-cosmetics store setup.
