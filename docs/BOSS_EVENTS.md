# Boss Events

Boss Events V1 makes Finn the server-wide boss contract NPC.

## Flow

- Gates use lifetime total Chowcoin value sold through the shipping bin, not item count.
- Defaults live in `config/gisketchs_chowkingdom_mod/bosses/events/server_bosses.toml`.
- Settings live in `config/gisketchs_chowkingdom_mod/bosses/settings.toml`.
- World state persists in `<world>/data/gisketchs_chowkingdom_mod/bosses/state.json`.
- When a threshold is reached, the next contract unlocks silently. Finn teases it with a gold priority balloon when players are near him, but the boss name and gate details stay hidden until someone opens `CONTRACTS`.
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
- Active boss contracts are not pinned immediately on unlock. Right-clicking Finn with a newly active, unintroduced contract opens the contract screen directly; that first open introduces it server-wide. After introduction, right-clicking Finn returns to normal dialogue with `TALK`, `CONTRACTS`, `BUY`, `GIFT`, and other standard actions unless a reward is claimable.
- After a valid clear or debug credit grant, players with claimable credit see `Talk to Finn to claim your rewards` in the pinned mission HUD and can claim through the contract dialogue.
- Once a boss has enough credited players, it is treated as contract-complete for HUD/dialogue purposes even if credit was granted through debug commands.
- After the player claims, Finn no longer keeps that boss as the active contract; he moves to the next locked/active contract or says no contract is ready.
- Claiming happens through Finn dialogue using `boss_contract` / `boss_claim` actions.
- If a player has claimable boss credit, Finn shows a green quest balloon and right-click opens a claim-ready LLM prompt instead of the generic greeting.
- Finn boss balloons have priority over lower-priority greeting, quest-offer, and quest-claim reminder balloons so multiple NPC balloon systems do not overwrite an urgent boss cue.
- Important NPC balloons can use `@gold` or `@green` before icon markers. Gold is for new attention/discovery, camper housing needs, and boss discovery; green is for completion, boss defeated, and reward-ready cues. Both render with a tinted background and plain white text.
- Boss reward claims are per player. One credited player claiming their reward does not remove claim credit or reward availability for other credited players.
- Boss reward claim lines support `<xp>`, `<coin>`, and `<b>` dialogue highlights.
- LLM is optional. When enabled, Finn receives boss context variables and authored config lines are the fallback. Boss contract reward lines must keep `<xp>` and `<coin>` highlight tags.
- TALK from inside `CONTRACTS` receives boss-focused context with lore, location hints, access hints, fight tips, rewards, and next-boss data. Finn dialogue avoids shipping totals, thresholds, hidden ids, and UI-button wording.
- Locked/no-contract Finn dialogue is lore-only: Finn says he is scouting for strange trouble and does not reveal shipping totals, thresholds, hidden boss ids, or the next boss.
- Dialogue highlight tags and quest text auto-space common number/text joins, and highlighted rewards render with the smaller crisp CKDM claim font.

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
- `/ck bosses <boss_id> credit <player> <true|false>` legacy/debug shorthand
- `/ck bosses required <boss_id|all> <players>`
- `/ck bosses reset <boss_id> confirm`

Boss command tails are greedy strings so namespaced ids like `cataclysm:ignis` and `fdbosses:chesed` work cleanly. Debug credit updates sync the pinned boss mission progress immediately. `required` updates `required_players` in `server_bosses.toml`; use `/ck bosses required all 1` for solo testing.

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
