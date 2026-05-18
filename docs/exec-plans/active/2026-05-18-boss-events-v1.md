# Boss Events V1

## Goal

Implement Finn-driven server boss contracts gated by total shipping-bin Chowcoin value.

## Implemented

- `bosses/` module with default 19-boss config, settings, world state, event hooks, and commands.
- Shipping payout unlock checks based on `ShippingBinStore.totalChowcoinsSold()`.
- Finn world-chat/snackbar/Discord announcements for clear events. Unlocks stay hidden until Finn introduces the contract through `CONTRACTS`.
- Locked boss spawn/damage warnings and locked boss drop suppression.
- Contributor tracking and Finn dialogue claim flow with optional LLM prompt context.
- Mission HUD injection for active boss contracts and claim-ready contracts.
- Finn `CONTRACTS` nested dialogue with locked, active, claimable, and complete contract states.
- Boss-focused Finn TALK context from the nested contract screen.
- Subtle `boss_cleared` global NPC memory for later LLM references.
- Greedy boss command tails for colon-safe ids, plus `/ck bosses required <boss_id|all> <players>` to tune crew size in config.
- Debug credit updates now sync pinned mission progress, including `/ck bosses <boss_id> credit <player> <true|false>` shorthand.
- Claim-ready Finn UX: quest balloon, automatic claim prompt on right-click, LLM-backed reward paid line, highlighted XP/Chowcoin text, and green claim buttons.
- Gold priority balloons for first greetings, quest offers, quest-ready cues, Finn unintroduced boss teases, and boss reward-ready alerts.
- Active boss HUD rows appear only after the first player opens Finn `CONTRACTS`; claimable reward rows still pin immediately and render gold.

## Validation

- `./gradlew.bat build` passes.

## Follow-Ups

- Add mission signals for `boss_first_clear` and `boss_participated`.
- Add helper/repeat reward distinction if repeat clears become common.
- Tune required player counts, thresholds, and rewards after in-game testing.
