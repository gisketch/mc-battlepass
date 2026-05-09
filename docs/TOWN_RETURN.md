# Town Return

Town Return adds a reusable player item for returning to a shared town point.

## Items

- `gisketchs_chowkingdom_mod:town_charm`: reusable return item with a 10 minute cooldown.

The Town Charm channels for 5 seconds before teleporting. While channeling, the player is locked in place with temporary flight-style motion, rises smoothly 1 block during the first second, continues drifting up to 1.5 blocks by completion, cannot interact, attack, break, place, or drop items, sees a snackbar progress meter, and gets a looping particle ring that expands, lifts, thins out, and recreates until teleport. The channel cancels if the player changes dimension, takes damage, logs out, or has a hostile monster within 16 blocks.

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