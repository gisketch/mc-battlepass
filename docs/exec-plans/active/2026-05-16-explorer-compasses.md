# Explorer Compass Store

Goal: Replace expensive explorer-map stock preparation with one-use Nature's Compass / Explorer's Compass store profiles.

Acceptance:

- Store refresh rolls only profile ids and stock counts.
- No biome/structure locate work runs on login or store open.
- Bought profile compasses run search only on right-click.
- Store rows generate dimension/category/band offers from live registry tags so future worldgen mods work.
- CKDM compasses search their configured dimension from origin bands and claim exact targets globally.
- Failed searches refund chowcoins, restore stock, and do not mutate global discovery.
- CKDM compass right-clicks cancel the native Nature's/Explorer's Compass GUI.
- `/extract biome`, `/extract structure`, and `/extract biome_structures` report runtime ids grouped by category to Discord.
- Finn/explorer store uses compass profiles.

Plan:

- [x] Inspect current map store and extract commands.
- [x] Add runtime target catalog.
- [x] Add one-use compass profile stack/service.
- [x] Add server-wide origin-band target claims and refund flow.
- [x] Add generated store rows for daily/weekly expedition pools.
- [x] Wire store offer type and Finn explorer config.
- [x] Validate build and documented checks.

Validation:

- `.\gradlew.bat build --console=plain`
- `C:\Program Files\Git\bin\bash.exe -lc './scripts/check-sonata.sh'`
