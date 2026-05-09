# Jobs Test Guide

Use this to verify every job perk in multiplayer or singleplayer. Test one job at a time.

## Setup

1. Start the client/server with the latest build.
2. Give tester permissions or run commands from console.
3. Set a test job:

```mcfunction
/ck roles set job <player> <job_id>
```

4. If needed, force job rank by adding overall/job XP through the existing battlepass or role progression tools used on the server.
5. Use profile/onboarding UI to confirm the job icon, rank, and perk text appears.
6. For Cobblemon jobs, enable debug overlays:

```mcfunction
/ck roles debug catch-rate <player>
/ck roles debug mount-speed <player>
```

7. Record: job id, rank, expected result, actual result, screenshot/video if weird.

## Global Checks

- Catch rate: throw balls at matching type Pokemon and confirm debug overlay lists the job perk.
- Mount speed: ride matching type Pokemon and confirm debug overlay lists the job perk.
- Multi-job: add a second job with `/ck roles add job <player> <job_id>` and confirm both job status effects show.
- Config reload: edit a runtime job TOML, restart or run the reload command, then confirm UI values update.

## Botanist

- Set job: `botanist`.
- Catch/mount: test Grass Pokemon.
- Crop bonus: harvest mature crops many times; expect occasional extra drops.
- Quality harvest: harvest configured quality crops; expect occasional upgrade.
- Gentle Steps: jump on farmland; farmland should not trample.
- Seasonal Farmer: plant/grow crops; expect occasional accelerated growth.

## Diver

- Set job: `diver`.
- Catch/mount: test Water Pokemon.
- Swim Speed: swim and compare movement to no-job baseline.
- Underwater Mining: mine underwater; penalty should be reduced.
- Fishing Bonus: fish repeatedly; expect occasional bonus reward.
- Rain Catch Bonus: catch Water Pokemon during rain; debug should show rain bonus.

## Magma Scout

- Set job: `magma_scout`.
- Catch/mount: test Fire Pokemon.
- Fire Damage Reduction: take fire/lava damage; damage should be reduced.
- Lava Walker: stand near/on lava case used by server rules; confirm perk behavior.
- Nether Hunter: catch Fire Pokemon in Nether or lava-heavy area; debug should show bonus.
- Heat Burst: take fire damage; expect short speed/resistance burst and cooldown.

## Engineer

- Set job: `engineer`.
- Catch/mount: test Electric Pokemon.
- Tool Mining Speed: mine with pickaxe/axe/shovel; speed should improve.
- Magnet: drop normal items nearby; they should pull toward player.
- Technician Reach: interact/place blocks farther away; range should increase for all blocks.
- Charged Maintenance: mine redstone/copper/iron ore with damaged tool; sometimes repairs 1 durability.

## Field Researcher

- Set job: `field_researcher`.
- Catch/mount: test Normal Pokemon.
- Luck-lite: check luck-sensitive interactions/loot.
- Surveyor Chowcoins: scan Pokemon; expect capped chowcoin snackbar.
- First Encounter XP: scan a new species; expect BP XP snackbar once per species.
- Field Notes: scan milestone counts; expect reward pool item at milestone.

## Bug Scout

- Set job: `bug_scout`.
- Catch/mount: test Bug Pokemon.
- Arthropod Damage: hit arthropod mobs; damage should increase.
- Web Walker: walk through cobwebs; slowdown should be reduced.
- Tiny Forager: break leaves/grass-like blocks; expect small bonus drops.
- Swarm Sense: nearby qualifying mobs should trigger movement/sense behavior.

## Falconer

- Set job: `falconer`.
- Catch/mount: test Flying Pokemon.
- Fall Damage Reduction: fall from controlled height; damage should reduce.
- Slow Fall-lite: trigger fall case; expect Slow Falling.
- High Ground Speed: stand at high elevation; movement should improve.
- Scout's Leap: jump/mobility should improve by rank.

## Shade Runner

- Set job: `shade_runner`.
- Catch/mount: test Dark Pokemon.
- Swift Sneak-lite: crouch move; speed should improve.
- Nightstep: move in darkness/night; speed should improve.
- Backstab-lite: hit target from behind; damage should increase with cooldown.
- Shadow Escape: take damage while low enough; expect speed burst and particles.

## Esper

- Set job: `esper`.
- Catch/mount: test Psychic Pokemon.
- Projectile Damage Reduction: get hit by arrows; damage should reduce.
- Telekinesis-lite: check item/interaction behavior tied to perk.
- Focus Mind: trigger focus condition; expect Haste.
- Premonition: take qualifying damage; expect Speed/Resistance burst.

## Martial Artist

- Set job: `martial_artist`.
- Catch/mount: test Fighting Pokemon.
- Knockback-lite: hit mobs; knockback should increase.
- Agility-lite: movement should improve by condition/rank.
- Combo Flow: consecutive hits should improve damage/flow.
- Second Wind: kill mobs; expect recovery effect when applicable.

## Mountaineer

- Set job: `mountaineer`.
- Catch/mount: test Ice Pokemon.
- Freeze Damage Reduction: take freeze/cold damage; damage should reduce.
- Step Assist-lite: walk over one-block ledges; step height should improve.
- Coldproof: cold/freeze status should be reduced or removed.
- Climber: mine/climb around mountain blocks; movement/mining should improve.

## Shinobi

- Set job: `shinobi`.
- Catch/mount: test Poison Pokemon.
- Poison Aspect-lite: melee mobs; expect occasional poison.
- Sneak Speed: crouch move; speed should improve.
- Toxic Resistance: get poisoned; duration should shorten.
- Smoke Step: take damage while sneaking; expect Speed II and cooldown.

## Mason

- Set job: `mason`.
- Catch/mount: test Rock Pokemon.
- Blast Protection-lite: take controlled explosion damage; damage should reduce.
- Builder's Reach: place/interact with blocks from farther away; attack reach should not change.
- Steady Hands: place stone/brick/wood/glass/decor blocks many times; expect occasional refund.
- Mason's Eye: sneak-right-click a block with empty hand; matching nearby blocks should show particles. Repeat to disable.

## Excavator

- Set job: `excavator`.
- Catch/mount: test Ground Pokemon.
- Terrain Mining Speed: mine dirt/sand/gravel/clay/mud/stone; speed should improve, ores should not count.
- Excavation-lite: mine soft blocks at ranks 2-5; shape should grow from 2x1 to 3x3. Sneak mining stays 1x1.
- Archaeologist: break sand/gravel/clay/suspicious blocks many times; expect occasional treasure.
- Tunnel Sense: go underground below Y=40 with no sky; expect Night Vision.

## Blacksmith

- Set job: `blacksmith`.
- Catch/mount: test Steel Pokemon.
- Unbreaking-lite: mine or kill with damaged held item; expect occasional 1 durability restoration.
- Repairing-lite: mine ores or kill mobs with damaged held item; expect occasional 1 durability repair, 30s cooldown.
- Forge Discount: use anvil repair/combine; XP cost should be about 20% lower.
- Ore Tempering: smelt iron/copper/gold and take output; expect occasional bonus ingot snackbar.

## Spirit Medium

- Set job: `spirit_medium`.
- Catch/mount: test Ghost Pokemon.
- Soul Speed-lite: walk on soul sand/soul soil/spooky blocks; speed should improve by rank.
- Ethereal Step-lite: drop below 25% HP; expect Resistance with 120s cooldown.
- Spirit Sight: crouch near undead; undead should glow for 5 seconds, 30s cooldown.
- Grave Whisper: kill undead many times; expect occasional 10-50 chowcoin snackbar until weekly cap.

## Drake Tamer

- Set job: `drake_tamer`.
- Catch/mount: test Dragon Pokemon.
- Protection-lite: take controlled damage; damage should reduce by rank.
- Mount Velocity-lite: ride Dragon Pokemon; mount debug should show stronger movement than normal Dragon mount speed alone.
- Draconic Presence: mount Dragon Pokemon; expect Resistance I for 8 seconds, 60s cooldown.
- Treasure Sense: open unopened chests/barrels/shulker boxes; expect rare chowcoin or amethyst shard reward with weekly caps.

## Performer

- Set job: `performer`.
- Catch/mount: test Fairy Pokemon.
- Charisma-lite: gain NPC friendship from chat/gifts; positive friendship gain should be higher by rank.
- Happy Boost-lite: stand near one or more NPCs; expect Speed I/II/III by rank. Multiple NPCs should not stack.
- Charming Gift: give loved/liked NPC gifts; expect +10 extra friendship before charisma scaling.
- Encore: complete NPC quests; expect 10% chance for +10 bonus BP XP, daily cap 50.

## Report Format

```text
Tester:
Build/date:
Job:
Rank:
Steps:
Expected:
Actual:
Pass/Fail:
Notes/screenshot:
```
