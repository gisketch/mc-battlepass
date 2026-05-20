# NPC Emotecraft Animations

## Goal

Let CKDM playerlike NPCs play Emotecraft `.emotecraft` clips through the existing PlayerAnimator/Mob Player Animator path.

## Acceptance Criteria

- Emotecraft 1.21.1 NeoForge jar is present in the Prism runtime instance.
- `refs/emotes/*.emotecraft` are copied to the runtime `.minecraft/emotes/server` folder.
- `/npc animation list` shows Emotecraft ids when Emotecraft is installed and files parse.
- `/npc animations emotecraft:<id>` queues the clip on playerlike NPCs.
- `emotes.toml` curates stable NPC emote ids for LLM, microinteraction, ambient, and posture use.
- LLM replies can return `{"message":"...","emote":"none"}`; invalid or wrong-surface emotes safely fall back to `none`.
- `/npc emote list|reload|test <id>` supports debug and smoke testing.
- CKDM still boots and Better Combat playerlike animations still work without Emotecraft.

## Context Links

- `docs/NPC_PLAYERLIKE_ANIMATIONS.md`
- `docs/COMPATIBILITY.md`
- `refs/emotes`

## Steps

- Add optional Emotecraft dependency metadata.
- Add reflection bridge for Emotecraft serializer/loaded emote maps.
- Add Emotecraft ids to `NpcPlayerlikeAnimationRegistry`.
- Resolve Emotecraft ids in `NpcPlayerlikeAnimationLayer`.
- Improve animation debug command status.
- Add NPC emote catalog/controller on top of playerlike animation playback.
- Add LLM emote output parsing and surface-filtered prompt choices.
- Add emote hooks for talk, world chat, gift sentiment, micro interactions, ambient life, Pokemon observation, and solo moments.
- Add `/npc emote` debug commands.
- Install runtime jar and copy runtime emote files.
- Run Gradle build.

## Validation

- `.\gradlew.bat build`
- In game:
  - `/npc animation debug`
  - `/npc animation playerlike true`
  - `/npc animation reload`
  - `/npc animation list`
  - `/npc animations emotecraft:wave`
  - `/npc emote list`
  - `/npc emote test wave`
  - LLM talk with `emote:"wave"` and neutral talk with `emote:"none"`
  - Ambient posture such as `sit_cool` cancels on interaction/movement/damage
  - `/npc animations bettercombat:one_handed_slash_horizontal_right`

## Decision Log

- Use `.minecraft/emotes/server` as the runtime source for `.emotecraft` files.
- Keep Emotecraft optional and use reflection, not hard API calls.
- Treat Emotecraft clips as visual-only PlayerAnimator `KeyframeAnimation`; CKDM keeps hit timing and combat rules.
- Keep raw `.emotecraft` files in the Prism runtime `.minecraft/emotes/server`; CKDM's mod resources only keep refs/source material.
- Keep the LLM/network dialog payload message-only for v1; the server triggers NPC entity animation through synced playerlike animation state.

## Progress Log

- 2026-05-20: Started implementation.
- 2026-05-20: Installed Emotecraft jar in Prism runtime and copied 15 `.emotecraft` files into `.minecraft/emotes/server`.
- 2026-05-20: Added optional Emotecraft bridge, playerlike registry ids, NPC layer playback, and debug command status.
- 2026-05-20: `.\gradlew.bat build` passed.
- 2026-05-20: Installed built CKDM jar into Prism runtime with `.\scripts\run-client.ps1 -SkipBuild -NoLaunch`.
- 2026-05-20: Added NPC emote catalog/controller, LLM `emote` parsing, micro/ambient hooks, and `/npc emote` debug commands.
- 2026-05-20: `.\gradlew.bat build` passed after emote life integration.
- 2026-05-20: Installed built CKDM jar into Prism runtime after emote life integration with `.\scripts\run-client.ps1 -SkipBuild -NoLaunch`.
- 2026-05-20: `bash ./scripts/check-sonata.sh` and `git diff --check` passed after emote life integration.
