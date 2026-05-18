# Boss Events

Boss Events V1 makes Finn the server-wide boss contract NPC.

## Flow

- Gates use lifetime total Chowcoin value sold through the shipping bin, not item count.
- Defaults live in `config/gisketchs_chowkingdom_mod/bosses/events/server_bosses.toml`.
- Settings live in `config/gisketchs_chowkingdom_mod/bosses/settings.toml`.
- World state persists in `<world>/data/gisketchs_chowkingdom_mod/bosses/state.json`.
- When a threshold is reached, Finn announces the new contract in world chat, a snackbar is sent, and `events_webhook_url` is posted if configured.
- Bosses stay fightable through their native mods. CKDM controls contract unlocks, contributor credit, claim rewards, and locked drop suppression.

## Locked Bosses

- Locked configured bosses warn nearby players on spawn/first interaction.
- Locked configured bosses warn attackers on damage.
- Locked configured boss item drops are suppressed.
- V1 does not cancel non-drop side effects such as End portal creation.

## Credit And Claim

- Contributor credit uses the killer, recent damaging players, and nearby players in the same dimension.
- Defaults: 96 block radius, 10 minute participation window, 8 required credited players.
- Finn always shows a `CONTRACTS` button in normal dialogue.
- `CONTRACTS` opens a nested boss contract dialogue for locked, active, claimable, or completed states.
- After a valid clear, players with credit see `Talk to Finn to claim <Boss>` in the mission HUD and can claim through the contract dialogue.
- Claiming happens through Finn dialogue using `boss_contract` / `boss_claim` actions.
- LLM is optional. When enabled, Finn receives boss context variables and authored config lines are the fallback.
- TALK from inside `CONTRACTS` receives boss-focused context with lore, location hints, access hints, fight tips, reward, threshold, and next-boss data.

## NPC Memory

- Valid boss clears record `boss_cleared` global event and memory entries.
- Other NPCs may naturally reference clears through the existing LLM memory system.
- V1 does not force non-Finn NPC world-chat announcements.

## Commands

- `/ck bosses status`
- `/ck bosses reload`
- `/ck bosses unlock <boss_id>`
- `/ck bosses credit get <boss_id> <player>`
- `/ck bosses credit set <boss_id> <player> <true|false>`
- `/ck bosses required <boss_id|all> <players>`
- `/ck bosses reset <boss_id> confirm`

Boss command tails are greedy strings so namespaced ids like `cataclysm:ignis` and `fdbosses:chesed` work cleanly. `required` updates `required_players` in `server_bosses.toml`; use `/ck bosses required all 1` for solo testing.

## Default Boss Order

1. `minecells:coniunctivius`
2. `block_factorys_bosses:sandworm`
3. `block_factorys_bosses:yeti`
4. `cataclysm:netherite_monstrosity`
5. `fdbosses:chesed`
6. `block_factorys_bosses:underworld_knight`
7. `minecraft:wither`
8. `cataclysm:the_harbinger`
9. `block_factorys_bosses:infernal_dragon`
10. `minecraft:ender_dragon`
11. `cataclysm:ender_guardian`
12. `fdbosses:malkuth`
13. `block_factorys_bosses:kraken`
14. `cataclysm:ancient_remnant`
15. `cataclysm:the_leviathan`
16. `cataclysm:scylla`
17. `cataclysm:maledictus`
18. `fdbosses:geburah`
19. `cataclysm:ignis`
