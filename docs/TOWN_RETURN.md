# Town Return

Town Return adds a reusable player item for returning to a shared town point.

## Items

- `gisketchs_chowkingdom_mod:town_charm`: reusable return item with a 10 minute cooldown.

The Town Charm channels for 5 seconds before teleporting. While channeling, the player is locked in place with temporary flight-style motion, rises smoothly 1 block during the first second, continues drifting up to 1.5 blocks by completion, cannot interact, attack, break, place, or drop items, sees a snackbar progress meter, and gets a looping job-colored sparkle ring that expands, lifts, thins out, and recreates until teleport. The town portal destination also shows a cross-dimensional incoming ring above the target block while the player channels. The channel cancels if the player changes dimension, takes damage, logs out, or has a hostile monster within 16 blocks.

## Config

Town Charm particle colors are configured at `config/gisketchs_chowkingdom_mod/town_return/config.toml` with `jobColors` entries keyed by job id.

## Admin Commands

- `/ck town portal set`: set the shared town portal to the command executor's current dimension, block position, yaw, and pitch.
- `/ck town portal status`: show the current shared town portal.
- `/ck town portal clear`: remove the shared town portal.
- `/ck town tp clear [player]`: clear a player's Town Charm cooldown.
- `/ck town cooldown clear [player]`: clear a player's Town Charm cooldown.
- `/ck town give charm [player] [count]`: give Town Charms.

`/chowkingdom town ...` is also registered.

## Storage

Server state is stored at `world/data/gisketchs_chowkingdom_mod/town_return/state.json`.