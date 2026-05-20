# Shipping Bin Full Audit

## Goal

Build a server-free audit for CKDM shipping prices using mod jar item, tag, and recipe data.

## Acceptance Criteria

- No headless server boot required.
- Parse current shipping prices.
- Parse item names, item tags, and recipe/process JSON from `runs/client/mods`.
- Include farming, food, fish, drink, Vinery wine, and other cozy-natural candidates.
- Produce a full CSV and readable Markdown report.
- Do not rewrite shipping prices in this pass.

## Context Links

- `docs/SHIPPING_BIN.md`
- `scripts/audit-shipping-bin.ps1`
- `<game config>/gisketchs_chowkingdom_mod/shipping_bin/prices.toml`

## Steps

- Upgrade offline audit script with better recipe extraction.
- Add process-aware cost handling for shaped/shapeless/cooking/cutting/Vinery.
- Add conservative sellability categories and block rules.
- Generate report data.
- Apply conservative nerfs to currently priced entries from report.
- Validate script and docs.

## Validation

- `.\scripts\audit-shipping-bin.ps1`
- `bash ./scripts/check-sonata.sh`

## Decision Log

- Use offline jar scraping, not server command, because full server boot is fragile.
- Report suggested prices only; do not mutate live prices yet.
- Use conservative shipping assumptions: NPC commissions should beat unlimited shipping.

## Progress Log

- 2026-05-17: Plan created.
- 2026-05-17: Upgraded offline audit to parse jar item names, tags, normal recipes, broad process recipes, and Vinery fermentation.
- 2026-05-17: Generated Markdown, CSV, and review-only TOML suggestion outputs.
- 2026-05-17: Applied current-entry-only nerfs where suggested price was lower than configured price.
- 2026-05-17: Validation passed: `.\scripts\audit-shipping-bin.ps1`, `bash ./scripts/check-sonata.sh`, `.\gradlew.bat build`.
