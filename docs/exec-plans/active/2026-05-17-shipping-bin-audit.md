# Shipping Bin Audit

## Goal

Create a server-side audit report for shipping bin food prices so CKDM can curate sellable items from the real loaded modlist without opening a client.

## Acceptance Criteria

- Add an admin command that can run on a headless server.
- Report current sellable item prices.
- Report suspiciously high shipping prices.
- Report food-like or farm-like items that are not currently sellable.
- Use loaded registries and recipes when available.
- Do not change shipping prices in this pass.
- Validate with Gradle build.

## Commands

- `/shippingbin audit`
- `/ck shippingbin audit`
- `/chowkingdom shippingbin audit`

## Output

- `docs/generated/shipping-bin-audit.md`

## Progress

- 2026-05-17: Plan created after balance checkpoint commit.
- 2026-05-17: Added `/shippingbin audit` server command and offline `scripts/audit-shipping-bin.ps1`.
- 2026-05-17: Synced client config/mod jars into `runs/server`; full client mod copy does not boot as dedicated server because client-only jars load client classes.
- 2026-05-17: Generated `docs/generated/shipping-bin-offline-audit.md` from `runs/client/mods` and current shipping prices.
- 2026-05-17: Validation passed: `bash ./scripts/check-sonata.sh` and `./gradlew.bat build`.
