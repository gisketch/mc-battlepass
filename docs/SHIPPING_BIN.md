# Shipping Bin

Shipping bin is a Stardew-style sale container for Chow Kingdom.

## Behavior

- Block id: `gisketchs_chowkingdom_mod:shipping_bin`.
- Right-click opens vanilla double chest UI.
- The chest title area renders a coin icon, current chowcoin balance, and a live green `+<preview>` value for sellable contents.
- The preview updates every render and animates changed values by fading/sliding the old value out and the new value in.
- Each player has a private 27-slot bin inventory. Any placed shipping bin opens that player's own bin.
- Shipping access is based on `(cozy BP XP + combat BP XP) / 100`, minimum level 1.
- Slots unlock by shipping level: level 1 has 1 slot, level 5 has 3, level 10 has 9, level 20 has 18, and level 30 has the max 27 sell slots.
- Per-slot stack caps unlock by shipping level: 16 before level 50, 32 at level 50, 48 at level 75, and 64 at level 100.
- The bin only allows one slot per item id. Item data does not create a separate slot, so gold quality wheat and diamond quality wheat still count as the same wheat item.
- Each item id has a weekly quota of 128 sold items. Items sold after quota still sell, but pay 10% value until the weekly battlepass period resets.
- At configured in-game time, server sells priced items from every saved player's bin.
- Payout is checked after the payout hour for each in-game day, so time skips past 5 AM still pay once for that day.
- Sold items grant chowcoins. Unpriced items remain in bin.
- Player gets an animated top-center `Sold <items> items for <amount> chowcoins` HUD notification with sale sounds.
- Offline players get their chowcoins immediately in world data and receive the reward notification on next login.
- Server broadcasts `<name> shipped items for <amount> chowcoins.`
- After each shipping payout batch, the top seller is announced to players by snackbar and to Discord when Discord webhooks are enabled.
- Server ops can run `/shippingbin sell` to sell their own bin immediately for testing.
- Server ops can run `/shippingbin sellabletag` to regenerate the `#gisketchs_chowkingdom_mod:sellable` item tag from current shipping prices. Run `/reload` after changing shipping prices so EMI sees the refreshed `#sellable` search results.
- Items priced by this config append a `coins.png <amount>` price row under the item name in hover tooltips.
- Sellable items are also written to a generated world datapack tag so EMI can search them with `#sellable`.

## Config

File created on first load:

```text
config/gisketchs_chowkingdom_mod/shipping_bin/prices.toml
```

## Audit

Offline audit, no client/server launch:

```powershell
.\scripts\audit-shipping-bin.ps1
```

Outputs:

```text
docs/generated/shipping-bin-offline-audit.md
docs/generated/shipping-bin-full-audit.csv
docs/generated/shipping-bin-price-suggestions.toml
```

The offline report reads item names, item tags, and recipe/process JSON from `runs/client/mods`. It is the preferred balance audit path because it does not require a dedicated server to boot. The suggested TOML is review-only and should not be copied blindly.

Server audit command, when a server-safe modlist can boot:

```text
/shippingbin audit
/ck shippingbin audit
/chowkingdom shippingbin audit
```

Output:

```text
docs/generated/shipping-bin-audit.md
```

The offline report is broad and useful for planning. The server command is more accurate because it uses the live registry and recipe manager, but it requires client-only mods to be removed from `runs/server/mods`.

Versioned samples:

- [docs/samples/shipping_bin_prices.json](samples/shipping_bin_prices.json)
- [docs/samples/shipping_bin_quality_food_multipliers.json](samples/shipping_bin_quality_food_multipliers.json)

Default shape:

```toml
payout_hour = 5
payout_minute = 0
entries = [
  { item = "minecraft:wheat", price_amount = 8 },
  { item = "minecraft:carrot", price_amount = 10 },
  { tag = "minecraft:crops", price_amount = 8 },
]

[quality_food]
enabled = true
iron_quality = 1.25
gold_quality = 1.6
diamond_quality = 2.25
```

## Price Resolution

1. Exact `item` match.
2. First matching `tag`.
3. No match means item is not sold.

Specific items always beat tags, even if tag entry appears earlier.

If Quality Food is installed, `quality_food:quality` data component levels multiply the resolved base price:

- Iron quality: `iron_quality`
- Gold quality: `gold_quality`
- Diamond quality: `diamond_quality`

The dependency is optional. Without the component registry entry, multiplier stays `1.0`.

Packaged datapack compatibility also contributes optional Cobblemon berry/apricorn item ids and common Cobblemon crop tags to Quality Food tags:

- `data/quality_food/tags/item/material_whitelist.json`
- `data/quality_food/tags/block/quality_blocks.json`

These entries are `required: false`, so the pack is safe when Cobblemon or Quality Food is absent.

## Battlepass Events

- `quality_food:quality_crop_harvested`: increments by quality item count dropped from crop/Quality Food blocks.
- `quality_food:quality_food_cooked`: increments by quality result count from furnace smelting and Farmer's Delight-style cooking/crafting outputs.
- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold`: increments by quality item count sold through the shipping bin.
- `gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold`: increments by iron quality item count sold through the shipping bin.
- `gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold`: increments by gold quality item count sold through the shipping bin.
- `gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold`: increments by diamond quality item count sold through the shipping bin.
- `gisketchs_chowkingdom_mod:shipping_bin_value_sold`: increments by total chowcoin value sold through the shipping bin.
- `gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold`: increments by chowcoin value from quality items only.

Testing command:

```text
/battlepass daily replace gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold 8
```

This command is admin-only because it is registered behind permission level `2`.

## Quality Food Test Items

Use these commands with Quality Food installed to get wheat with each tier:

```mcfunction
/give @s minecraft:wheat[quality_food:quality={level:1,type:"quality_food:iron"}] 64
/give @s minecraft:wheat[quality_food:quality={level:2,type:"quality_food:gold"}] 64
/give @s minecraft:wheat[quality_food:quality={level:3,type:"quality_food:diamond"}] 64
```

With the default shipping config, one wheat sells for `8`, iron wheat sells for `10`, gold wheat sells for `13`, and diamond wheat sells for `18`.

## Storage

World data:

```text
<world>/data/gisketchs_chowkingdom_mod/shipping_bin/bins.json
```

Stored stacks persist full item data when a server registry is available, including data components such as Quality Food quality.

Pending offline payout notifications are stored in the same file under `pendingRewards`.

Payout timing uses the shared Chow Kingdom clock. When Better Days is installed, Chow Kingdom reads `betterdays-common.toml` and interprets `payout_hour` through that in-game clock. Store resets are separate and remain real-life time.
