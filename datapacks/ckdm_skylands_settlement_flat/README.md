# CKDM Sky Lands Settlement Flat Datapack

Deprecated fallback. The mod already bundles `data/ckdm/dimension/sky_lands.json`, so normal servers should not need this world datapack. With Sky Archipelago `1.3.0`, tune Sky Lands through:

```text
config/sky_archipelago-common.toml
```

Use this datapack only if server testing proves the common TOML does not affect `ckdm:sky_lands` and a world-level dimension override is needed.

If needed, copy this folder into your world datapacks folder:

```text
<server>/<level-name>/datapacks/ckdm_skylands_settlement_flat
```

For a default server, that is usually:

```text
world/datapacks/ckdm_skylands_settlement_flat
```

Then restart the server. New Sky Lands chunks use the changed terrain. Existing chunks will not reshape unless regenerated.

This overrides:

```text
data/ckdm/dimension/sky_lands.json
```

Terrain intent:

- Flatter buildable islands: `terrain_relief_scale = 0.35`.
- Floating island spread: `min_island_y = 96`, `max_island_y = 230`.
- Visible low/mid/high bands with extra high-island presence.
- More settlement space than default without continent-scale islands: large islands reach radius `116-175` blocks with `190-320` dynamic cluster spacing.
- Peaceful structure policy: hostile/dungeon-style structures are denied; villages are not denied.
- No ocean remains enabled, matching CKDM default.
