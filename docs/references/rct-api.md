# Radical Cobblemon Trainers API Reference

Purpose: repo-local notes for CKDM Pokemon NPC gyms.

Installed jar inspected:

- Prism path: `C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\mods\rctapi-neoforge-1.21.1-0.15.2-beta.jar`
- Mod id: `rctapi`
- Version: `0.15.2-beta`
- Loader: NeoForge 1.21.1
- Required deps from jar metadata: NeoForge `21.1+`, Minecraft `1.21.1+`, Architectury `13.0.2+`, Cobblemon `1.7+`

Sources checked:

- Installed jar public signatures with `javap`.
- Upstream API README: <https://gitlab.com/srcmc/rct/api/-/raw/1.21.1/README.md>
- RCT trainer data docs: <https://srcmc.gitlab.io/rct/docs/latest/configuration/data_pack/trainers/>
- RCT server config docs: <https://srcmc.gitlab.io/rct/docs/latest/configuration/server_config/>
- RCT commands docs: <https://srcmc.gitlab.io/rct/docs/latest/gameplay/commands/>

## What The API Gives Us

The API is enough for CKDM-owned gym progression:

- Register CKDM players and NPC trainers as RCT `Trainer` objects.
- Attach an RCT `TrainerNPC` to a CKDM NPC `LivingEntity`.
- Start Cobblemon battles from our dialogue flow.
- Listen for RCT battle start/end events.
- Read battle winners/losers after the fight.
- Configure trainer teams, AI, bag items, battle format, and battle rules from our own config.

The API does not appear to be a complete gym system by itself. CKDM should own badges, unlocks, mission hooks, dialogue, rewards, rematches, and persistence.

## Important Classes

Main entry point:

```java
com.gitlab.srcmc.rctapi.api.RCTApi
```

Useful methods:

```java
RCTApi.initInstance(String modId)
RCTApi.getInstance(String modId)
RCTApi.getTrainerRegistry()
RCTApi.getBattleManager()
RCTApi.getEventContext()
RCTApi.gsonBuilder()
RCTApi.configureGsonBuilder(GsonBuilder)
```

Trainer registry:

```java
TrainerRegistry.init(MinecraftServer)
TrainerRegistry.registerPlayer(String id, ServerPlayer player)
TrainerRegistry.registerNPC(String id, TrainerModel model)
TrainerRegistry.registerNPC(String id, TrainerNPC trainer)
TrainerRegistry.unregisterById(String id)
TrainerRegistry.getById(String id)
TrainerRegistry.getById(String id, Class<T>)
TrainerRegistry.clear()
TrainerRegistry.clearNPCs()
TrainerRegistry.clearPlayers()
```

Battle manager:

```java
BattleManager.startBattle(List<Trainer> side1, List<Trainer> side2, BattleFormatProvider format, BattleRules rules): UUID
BattleManager.startBattle(List<Trainer> side1, List<Trainer> side2, BattleFormat format, BattleRules rules): UUID
BattleManager.startSingle(Trainer, Trainer, BattleRules): boolean
BattleManager.end(UUID)
BattleManager.getState(UUID): BattleState
BattleManager.getStates()
```

Prefer `startBattle(...)` because it returns a battle UUID.

Trainers:

```java
TrainerPlayer(ServerPlayer)
TrainerNPC(Text name, Pokemon[] team, TrainerBag bag, BattleAI ai, LivingEntity entity)
TrainerNPC.setEntity(LivingEntity)
TrainerNPC.getBattleInstances()
TrainerNPC.getTeam()
TrainerNPC.getBag()
```

Battle result state:

```java
BattleState.getBattle()
BattleState.getParticipants1()
BattleState.getParticipants2()
BattleState.isParticipating(Trainer)
BattleState.getWinners()
BattleState.getLosers()
BattleState.getWinnerSide()
BattleState.getLoserSide()
BattleState.isEndForced()
```

Events:

```java
Events.TRAINER_REGISTRED
Events.TRAINER_UNREGISTRED
Events.BATTLE_STARTED
Events.BATTLE_ENDED
EventContext.register(EventType<T>, EventListener<T>)
EventContext.unregister(EventListener<T>)
```

Note: `TRAINER_REGISTRED` / `TRAINER_UNREGISTRED` are spelled that way in the API.

## Trainer Models

RCT has serializable models that match its JSON trainer docs:

```java
TrainerModel
PokemonModel
BagItemModel
Gimmicks
```

Trainer fields from docs/API:

- `name`
- `identity`
- `ai`
- `battleFormat`
- `battleRules`
- `battleTheme`
- `bag`
- `team`

Pokemon fields:

- `species`
- `nickname`
- `gender`
- `level`
- `nature`
- `ability`
- `moveset`
- `ivs`
- `evs`
- `shiny`
- `heldItem`
- `aspects`
- `gimmicks`

Battle rule fields:

- `maxItemUses`
- `healPlayers`
- `adjustPlayerLevels`
- `adjustNPCLevels`

Builder:

```java
BattleRules.Builder()
    .withMaxItemUses(2)
    .withHealPlayers(true)
    .withAdjustPlayerLevels(false)
    .withAdjustNPCLevels(false)
    .build()
```

Formats exposed by API:

- `GEN_9_SINGLES`
- `GEN_9_DOUBLES`
- `GEN_9_TRIPLES`
- `GEN_9_MULTI`

Docs mention `GEN_9_ROYAL`, but the installed `0.15.2-beta` jar public enum only shows the four above. Treat `GEN_9_ROYAL` as unavailable unless a later jar exposes it.

## AI

Recommended RCT AI type in docs: `rct`.

Public classes:

```java
RCTBattleAI
RCTBattleAIConfig
StrongBattleAIConfig
SelfdotGen5AIConfig
```

RCT AI config fields:

- `moveBias`
- `statusMoveBias`
- `switchBias`
- `itemBias`
- `maxSelectMargin`

Docs call the JSON field `statMoveBias`, but the installed jar exposes `statusMoveBias()`. Verify JSON parsing before finalizing trainer config. Use the upstream docs field first, but test with a sample gym leader.

## Proposed CKDM Gym Flow

1. On server starting:
   - `RCTApi.initInstance("gisketchs_chowkingdom_mod")`
   - `api.getTrainerRegistry().init(server)`
   - register RCT event listeners once.

2. On player login/logout:
   - register player as `TrainerPlayer`, probably by UUID string, not name.
   - unregister on logout.

3. On config reload:
   - parse CKDM `gyms/*.toml`.
   - build RCT `TrainerModel` or direct `TrainerNPC` per gym.
   - register each gym trainer as `gym/<gym_id>/<stage_id>`.

4. On NPC interaction:
   - CKDM dialogue checks badge/gate state.
   - if challenge accepted, fetch `TrainerPlayer` and `TrainerNPC`.
   - call `trainerNpc.setEntity(ckdmNpcEntity)` immediately before battle.
   - start battle with explicit format/rules.
   - store `battleUuid -> gymChallengeContext(playerUuid, gymId, npcId, startedAt)`.

5. On `Events.BATTLE_ENDED`:
   - find stored context by battle UUID or matching participants.
   - ignore forced-ended battles unless we explicitly decide otherwise.
   - if player is in winners, grant badge/reward/mission hook.
   - if player loses, record attempt and give Finn-style/NPC-style loss dialogue.

## Important Safety Notes

- Always attach the trainer to a loaded CKDM NPC entity immediately before `startBattle`. Upstream README warns unloaded entities may softlock battles.
- Prefer UUID-based player trainer ids. Names are convenient but unsafe for long-term server state.
- Keep CKDM persistence separate from RCT progression. RCT commands/series are useful for debugging, but CKDM badges should live in CKDM world/player data.
- Do not rely on natural RCT trainer spawning for CKDM gyms. Our gym leaders should be CKDM NPCs with explicit dialogue.
- Level caps should be CKDM-owned unless we intentionally adopt RCT's series progression. RCT config has level cap systems, but using them wholesale may conflict with battlepass/classes/story pacing.
- Start with singles and no gimmicks. Add doubles, tera, dynamax, or held-item gimmicks only after one stable gym works.
- Multiplayer: one gym battle per player/NPC pairing at a time. Add challenge locks to avoid two players attaching the same gym NPC simultaneously.

## Gradle Integration Note

The jar is currently only installed in the Prism instance. To compile CKDM against RCT API, add a compile/runtime dependency or local flat-file dependency later.

CurseForge file page for installed NeoForge jar:

```gradle
modImplementation("curse.maven:radical-cobblemon-trainers-api-1152792:7952419")
```

The repo currently does not declare CurseMaven. Add a repository/dependency only when implementation starts, not during planning.

## CKDM V1 Scope

Recommended first implementation:

- CKDM `gyms/` module.
- One sample gym config.
- One CKDM NPC marked as `gym_leader`.
- Dialogue button: `CHALLENGE`.
- Singles battle only.
- Heal player team at start.
- `maxItemUses = 2`.
- First win grants badge, Chowcoins, pass XP, and mission event.
- Repeat wins grant small reward or no reward.
- Commands:
  - `/ck gyms status`
  - `/ck gyms reload`
  - `/ck gyms grant <gym_id> <player>`
  - `/ck gyms reset <gym_id> <player>`

## Open Questions

- Should CKDM gym leaders use fixed levels, player-relative levels, or badge-relative levels?
- Should losing have a cost, cooldown, or only dialogue?
- Should gym badges unlock legendary eligibility, shops, regions, or mostly prestige?
- Should gym missions be battlepass weekly goals, permanent CKDM missions, or both?
- Should gym NPCs support rematch teams after first clear?
