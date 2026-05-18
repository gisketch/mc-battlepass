# Boss Events V1

## Goal

Implement Finn-driven server boss contracts gated by total shipping-bin Chowcoin value.

## Implemented

- `bosses/` module with default 19-boss config, settings, world state, event hooks, and commands.
- Shipping payout unlock checks based on `ShippingBinStore.totalChowcoinsSold()`.
- Finn world-chat/snackbar/Discord announcements for unlock and clear events.
- Locked boss spawn/damage warnings and locked boss drop suppression.
- Contributor tracking and Finn dialogue claim flow with optional LLM prompt context.
- Mission HUD injection for active boss contracts and claim-ready contracts.

## Validation

- `./gradlew.bat build` passes.

## Follow-Ups

- Add mission signals for `boss_first_clear` and `boss_participated`.
- Add helper/repeat reward distinction if repeat clears become common.
- Tune required player counts, thresholds, and rewards after in-game testing.
