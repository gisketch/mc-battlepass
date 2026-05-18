# NPC Pokemon Battle And Sparring

Date: 2026-05-19

## Goal

Ship resident NPC Pokemon battle quests, no-reward Friendly Battle, and class-only sparring quests. All access is gated by a valid resident workplace and required work blocks. Trainer/gym NPC configs remain untouched.

## Acceptance

- `pokemon_battle` and `sparring` load as first-class NPC quest categories.
- Pokemon battle quest wins complete the active NPC quest and award combat BP XP/Chowcoins.
- Friendly Battle starts from a resident dialog button, costs no daily slot, and gives no reward.
- Sparring starts a nerfed NPC boss fight, completes only on win, and never advances class unlock/mentor flow.
- New mission hooks exist: `npc_pokemon_battle_won`, `npc_sparring_won`.
- Prism resident NPC configs contain battle quest entries and roster files for all non-trainer residents.
- Build passes.

## Work Plan

1. Add quest-category and mission-hook plumbing.
2. Add shared workplace/work-block gate for battle actions and quest offers.
3. Add resident RCT battle service using `main_stadium` and random 6-of-15 roster sampling.
4. Add Friendly Battle dialog route.
5. Add sparring boss mode and completion.
6. Generate Prism roster JSON and append resident quest config entries.
7. Update docs and run validation/build.

## Notes

- Use RCT battle rules for temporary level scaling where possible; do not grant Pokemon XP or item rewards.
- Roster JSON lives under `config/gisketchs_chowkingdom_mod/npc_battles/rosters`.
- Work-blocked means assigned workplace plus configured `work_blocks` present, not current work hour.

## Validation

- `compileKotlin` passed with constrained local heap.
- `build` passed with constrained local heap.
- Validated 26 edited Prism resident NPC TOMLs with the NPC config validator.
- Validated 26 Prism roster JSON files for 15 Pokemon, level 50, IV shape, and legal EV totals.
