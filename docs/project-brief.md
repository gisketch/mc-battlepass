# Project Brief

## One-Line Intent

Minecraft 1.21.1 NeoForge mod for Chow Kingdom battlepass, economy, profiles, Discord bridge, and related server systems.

## Project Kind

existing project

## Stack

- Gradle Kotlin DSL.
- Kotlin JVM `2.3.0`.
- Java toolchain `21`.
- NeoForge `21.1.228` for Minecraft `1.21.1`.
- KotlinForForge and OWO library.

## Users

- Primary user: Chow Kingdom server owner/developer.
- Secondary users: Minecraft players using battlepass, wallets, shipping bin, nicknames, and Discord bridge.
- Non-goals: generic standalone app behavior outside the mod/runtime.

## Problem

Server needs cohesive in-game progression, currency, utility modules, and Discord relay without scattering behavior across undocumented one-off code.

## First Useful Version

Document current mod shape, preserve existing domain docs, and make future changes follow clear module and validation rules.

## Acceptance Criteria

- User can build the mod with Gradle and find domain docs from `docs/index.md`.
- System must keep gameplay state server-authoritative and persist world/config data in documented paths.
- Project is not done until source changes are validated with relevant Gradle and Sonata checks.

## Constraints

- Package manager/build runner: Gradle wrapper.
- Runtime: Minecraft `1.21.1`, NeoForge `21.1.228`, Java `21`.
- Data: world data for gameplay state; config files for definitions and local preferences.
- Security: Discord webhook URLs and bot tokens must stay out of git.
- Performance: do not block server chat or tick threads on network/webhook work.
- Token budget: default caveman terse chat; durable context belongs in docs.

## Open Questions

- Which Gradle tasks should be required before every handoff beyond `build`?
- Are shops intentionally stub-only or next feature target?
