# Snackbar Antispam

## Goal

Prevent duplicate consecutive snackbar notifications from filling the stack, and remove battlepass reward claim spam from the Claim All UI path.

## Acceptance Criteria

- Identical consecutive snackbar payloads create one client snackbar and extend its visible duration.
- Dedupe checks the newest queued snackbar first, then the newest active snackbar.
- Non-identical snackbar order and max-active queue behavior remain unchanged.
- Battlepass Claim All keeps local reward flyout visuals but sends one server `claimAll` request.
- Battlepass Claim All sends one `Claimed X rewards.` snackbar and suppresses per-reward claim snackbars from reward side effects.
- Snackbar chrome is smaller, title uses the existing small CKDM font, and text is drawn at native font scale.
- Build passes after Kotlin changes.

## Context Links

- [Snackbar client](../../src/main/kotlin/dev/gisketch/chowkingdom/snackbar/SnackbarClient.kt)
- [Battlepass screen](../../src/main/kotlin/dev/gisketch/chowkingdom/battlepass/BattlepassScreen.kt)
- [Quality](../quality.md)

## Steps

- [x] Audit snackbar queue/active ownership and battlepass claim paths.
- [x] Add client-side consecutive duplicate coalescing.
- [x] Route battlepass custom Claim All click through server batch claim.
- [x] Validate with Gradle build.

## Validation

- `./gradlew.bat build`

## Decision Log

- 2026-05-19: Dedupe belongs client-side because all server and offline snackbar sources converge into one client queue.
- 2026-05-19: Battlepass Claim All spam source is `BattlepassScreen.mouseClicked`, which loops per reward slot; server already has `claimAll`.
- 2026-05-19: Claim All reward grants run with reward-side snackbar notifications suppressed, then emit one summary snackbar.
- 2026-05-19: Removed full snackbar pose scaling so fonts stay crisp; reduced layout constants and switched title to `ckdm_bold_small`.
- 2026-05-19: Tech License client denial copy matches server denial copy so client/server duplicate denials coalesce into one required snackbar.

## Progress Log

- 2026-05-19: Audit complete; implementation next.
- 2026-05-19: Added client duplicate coalescing, fixed battlepass Claim All packet spam, and passed Gradle build.
- 2026-05-19: Tightened Claim All summary behavior, silenced license reward snackbar during batch claims, reduced snackbar dimensions, and passed Gradle build again.
- 2026-05-19: Added asymmetric snackbar padding, smaller icon/frame dimensions, small title font, tighter content line height, and passed Gradle build.
- 2026-05-19: Replaced client-side `Must unlock Create License.` denial with `Create License required.`.
