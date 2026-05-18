# Poke Clothing Cosmetics

Goal: add selected `refs/poke-clothing` outfits as CKDM no-stat wearable cosmetics and sell them through the live Prism cosmetics store.

Acceptance:
- Only complete wearable clothing assets are registered.
- Tailoring station, cloth/fabric materials, Fabric metadata/code, frying pan, and incomplete sets are excluded.
- Registered items use CKDM namespace and have zero armor stats.
- Runtime `recipe_disabler.toml` cosmeticizes the registered ids.
- Runtime `stores/cosmetics.toml` includes grouped Poke Clothing outfit sets.
- `.\gradlew.bat build` passes.

Result:
- Registered 79 CKDM no-stat armor cosmetics across 21 Poke Clothing outfit sets.
- Copied only item icons and armor layer textures needed by those sets.
- Added the live Prism `poke_clothing` cosmetics store category.
- Added the live Prism `recipe_disabler.toml` cosmeticize entries.
- Excluded tailoring station, cloth/fabric assets, Fabric metadata/code, and frying pan.
