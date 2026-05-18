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
- Priority balloons: gold for first greetings, quest offers, and Finn unintroduced boss teases; green for quest-ready, boss defeated, and boss reward-ready alerts.
- Finn boss balloons suppress lower-priority greeting/quest reminder balloons when an urgent boss cue is available.
- Active boss HUD rows appear only after the first player opens Finn `CONTRACTS`; claimable reward rows still pin immediately and render gold.
- Boss reward claiming is per-player; one player's claim does not consume rewards for other credited players.
- Active Finn boss contracts now auto-open on right-click, use boss details immediately, and avoid shipping-gate or UI-button wording in immersive dialogue.
- NPC dialogue, HUD mission rows, and gold balloons use crisper CKDM font sizing plus display spacing cleanup for joined number/text phrases.

## Validation

- `./gradlew.bat build` passes.

## Follow-Ups

- `boss_first_clear` mission signal is implemented in the mission hook batch.
- Defer `boss_participated`; participation semantics need more conversation and should not ship in the current hook set.
- Add helper/repeat reward distinction if repeat clears become common.
- Tune required player counts, thresholds, and rewards after in-game testing.
