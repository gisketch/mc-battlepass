# Shipping Bin

Shipping bin is a Stardew-style sale container for Chow Kingdom.

## Behavior

- Block id: `gisketchs_chowkingdom_mod:shipping_bin`.
- Right-click opens vanilla double chest UI.
- The chest title area renders a coin icon, current chowcoin balance, and a live green `+<preview>` value for sellable contents.
- The preview updates every render and animates changed values by fading/sliding the old value out and the new value in.
- Each player has a private 54-slot bin inventory. Any placed shipping bin opens that player's own bin.
- At configured in-game time, server sells priced items from every saved player's bin.
- Sold items grant chowcoins. Unpriced items remain in bin.
- Player gets an animated top-center `Sold <items> items for <amount> chowcoins` HUD notification with sale sounds.
- Offline players get their chowcoins immediately in world data and receive the reward notification on next login.
- Server broadcasts `<name> shipped items for <amount> chowcoins.`
- Server ops can run `/shippingbin sell` to sell their own bin immediately for testing.
- Items priced by this config append a `coins.png <amount>` price row under the item name in hover tooltips.

## Config

File created on first load:

```text
config/gisketchs_chowkingdom_mod/shipping_bin/prices.toml
```

Versioned samples:

- [docs/samples/shipping_bin_prices.json](samples/shipping_bin_prices.json)
- [docs/samples/shipping_bin_quality_food_multipliers.json](samples/shipping_bin_quality_food_multipliers.json)

Default shape:

```toml
payout_hour = 5
payout_minute = 0
entries = [
  { item = "minecraft:wheat", price_amount = 100 },
  { item = "minecraft:carrot", price_amount = 75 },
  { tag = "minecraft:crops", price_amount = 50 },
]

[quality_food]
enabled = true
iron_quality = 1.1
gold_quality = 1.25
diamond_quality = 1.5
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

With the default shipping config, one wheat sells for `100`, iron wheat sells for `110`, gold wheat sells for `125`, and diamond wheat sells for `150`.

## Storage

World data:

```text
<world>/data/gisketchs_chowkingdom_mod/shipping_bin/bins.json
```

Stored stacks persist full item data when a server registry is available, including data components such as Quality Food quality.

Pending offline payout notifications are stored in the same file under `pendingRewards`.

Payout timing uses the shared Chow Kingdom clock. When Better Days is installed, Chow Kingdom reads `betterdays-common.toml` and interprets `payout_hour` through that in-game clock. Store resets are separate and remain real-life time.
