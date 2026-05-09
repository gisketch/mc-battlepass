# SmartBrainLib Reference

## Scope

SmartBrainLib is required for future CKDM NPC AI work. Use it for in-world NPC behavior: movement, sensors, memories, task choice, schedules, reactions, guards, companions, and future bosses. Do not use it for CKDM product interaction contracts: right-click dialog, shops, gifts, quests, battlepass UI, snackbars, Discord, or LLM text routing.

## Dependency

Gradle:

```kotlin
repositories {
    exclusiveContent {
        forRepository {
            maven("https://dl.cloudsmith.io/public/tslat/sbl/maven/") {
                name = "SmartBrainLib"
            }
        }
        filter {
            includeGroup("net.tslat.smartbrainlib")
        }
    }
}

dependencies {
    implementation("net.tslat.smartbrainlib:SmartBrainLib-neoforge-$minecraftVersion:$smartBrainLibVersion")
}
```

Current CKDM values:

- Minecraft: `1.21.1`
- Loader: NeoForge
- Artifact: `net.tslat.smartbrainlib:SmartBrainLib-neoforge-1.21.1:1.16.11`
- Mod id: `smartbrainlib`
- Required side: `BOTH`

## Core APIs

Typical SBL entity shape:

```kotlin
class ExampleEntity(type: EntityType<out PathfinderMob>, level: Level) : PathfinderMob(type, level), SmartBrainOwner<ExampleEntity> {
    override fun brainProvider(): Brain.Provider<*> = SmartBrainProvider(this)

    override fun customServerAiStep() {
        tickBrain(this)
    }
}
```

Common owner methods to implement/use:

- `brainProvider()`: returns `SmartBrainProvider(this)`.
- `getSensors()`: declare SBL sensors used by the brain.
- `getCoreTasks()`: always-on tasks such as floating, looking, and moving to walk target.
- `getIdleTasks()`: non-combat town behavior, schedules, follow, greeting, workstation movement.
- `getFightTasks()`: combat tasks; keep empty for town NPC migration unless a current behavior really fights.
- `getTargetingTasks()`: target selection tasks; keep minimal for town NPCs.
- `tickBrain(this)`: call from server AI tick path.

Useful SBL concepts:

- Sensors feed memories.
- Memories are the state that behaviors read/write.
- Behaviors/tasks are composable and condition-gated.
- Core tasks handle low-level movement/look behavior.
- Idle/fight/target tasks hold actual high-level choices.

## CKDM Rules

Keep CKDM-owned systems outside SBL:

- `NpcFeature.handleDialogAction`
- `NpcNetwork` packets
- shop open/buy flow
- gift handling
- quest accept/claim UI
- LLM prompt/request lifecycle
- Discord relay
- renderer/model/EMF compatibility

Move only AI decision/movement behavior into SBL:

- schedule movement: work/home/sleep/meetup/roam
- follow rent contract holder
- follow job application holder
- quest claim approach
- outgoing gift approach
- greeting balloon trigger
- NPC-to-NPC micro interaction movement
- talking pause/look target
- fire/hazard run-away
- hurt retaliation behavior

## Migration Notes

Current CKDM town NPC AI entry is `ChowNpcEntity.customServerAiStep()` calling `tickBrain(this)`. `NpcSmartBrain` currently defines no sensors, uses SBL core tasks, and adds a town-brain idle behavior that re-evaluates ordered `NpcSmartBrainTask` tasks every tick. Add sensors only when a behavior reads the memory they populate. `NpcFeature.prepareSmartBrainTick(entity)` only syncs CKDM world state before SBL ticks. Preserve these contracts:

- `debugActivity`, `debugGoal`, and `debugTargetPos` still power `/npc debug`.
- sleeping still walks to bed, starts sleeping, and aligns to pillow.
- talking still pauses navigation and looks at the player.
- work schedule still goes to assigned workplace, but shop validity stays in CKDM interaction code.
- missing `work_blocks` still blocks shop/assignment through CKDM interaction checks.
- LLM/dialog behavior remains unchanged.

Current CKDM SBL behavior order:

1. critical hazard/hurt override
2. quest claim approach
3. rent contract follow
4. job application follow
5. NPC-to-NPC micro interaction
6. outgoing gift approach
7. quest offer balloon
8. greeting balloon
9. talking pause
10. routine schedule movement

Add future town AI as focused `NpcSmartBrainTask` entries unless it truly needs its own long-running SBL behavior. The town-brain root must re-evaluate priority every tick so stale dialog/talking/task states cannot freeze routine movement. Do not put dialog, shop, gift claiming, quest UI, packets, or LLM text generation into SBL behaviors.

## Sources

- SmartBrainLib wiki: Getting Started.
- SmartBrainLib wiki: Making an Entity With SmartBrainLib.
- SmartBrainLib Cloudsmith Maven repository.
