param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ConfigRoot = "C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1\instances\modsync-ckdm-2026\.minecraft\config\gisketchs_chowkingdom_mod",
    [int]$MaxLevel = 500
)

$ErrorActionPreference = "Stop"

$ModId = "gisketchs_chowkingdom_mod"
$PassDir = Join-Path $ConfigRoot "battlepass/passes"
$PoolDir = Join-Path $ConfigRoot "relic_roulette/pools"
$DocsDir = Join-Path $RepoRoot "docs/generated"

New-Item -ItemType Directory -Force -Path $PassDir, $PoolDir, $DocsDir | Out-Null

function Write-Text([string]$Path, [string]$Text) {
    $dir = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $normalized = $Text.TrimEnd() + "`r`n"
    [System.IO.File]::WriteAllText($Path, $normalized, [System.Text.UTF8Encoding]::new($false))
}

function Q([string]$Value) {
    '"' + (($Value -replace '\\', '\\') -replace '"', '\"') + '"'
}

function Number-Array([int[]]$Values) {
    "[" + ($Values -join ", ") + "]"
}

function String-Array([string[]]$Values) {
    "[" + (($Values | ForEach-Object { Q $_ }) -join ", ") + "]"
}

function Reward-Entry([string]$Item, [int]$Quantity, [int]$Max, [int]$ScaleEvery = 200) {
    @{ item = $Item; quantity = $Quantity; max = $Max; scale = $ScaleEvery }
}

function Mission-Entry([string]$Id, [string]$Event, [string]$Desc, [int[]]$Goals, [int[]]$Xp, [hashtable]$Filters = @{}, [string]$RotationGroup = "") {
    @{
        id = $Id
        event = $Event
        desc = $Desc
        goals = $Goals
        xp = $Xp
        filters = $Filters
        rotation_group = $RotationGroup
    }
}

function Mission-Toml($Mission) {
    $parts = [System.Collections.Generic.List[string]]::new()
    $parts.Add("id = $(Q $Mission.id)")
    $parts.Add("event = $(Q $Mission.event)")
    $parts.Add("type = `"progressive`"")
    $parts.Add("event_desc = $(Q $Mission.desc)")
    $parts.Add("progress = 0")
    $parts.Add("progress_goals = $(Number-Array $Mission.goals)")
    $parts.Add("progress_xp = $(Number-Array $Mission.xp)")
    if ($Mission.filters -and $Mission.filters.Count -gt 0) {
        $filterPairs = $Mission.filters.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name) = $(Q $_.Value)" }
        $parts.Add("filters = { $($filterPairs -join ', ') }")
    }
    if ($Mission.rotation_group) {
        $parts.Add("rotation_group = $(Q $Mission.rotation_group)")
    }
    "{ $($parts -join ', ') }"
}

function Token-For-Pool([string]$Pool) {
    switch ($Pool) {
        "common_cozy_relics" { "${ModId}:common_cozy_relic_token"; break }
        "rare_cozy_relics" { "${ModId}:rare_cozy_relic_token"; break }
        "common_combat_relics" { "${ModId}:common_combat_relic_token"; break }
        "rare_combat_relics" { "${ModId}:rare_combat_relic_token"; break }
        default { "minecraft:air" }
    }
}

function Fixed-Reward([string]$Item, [int]$Quantity = 1, [bool]$Prominent = $true) {
    "{ type = `"item`", item = $(Q $Item), quantity = $Quantity, is_prominent = $($Prominent.ToString().ToLowerInvariant()) }"
}

function Chowcoin-Reward([int]$Amount) {
    "{ type = `"chowcoin`", item = `"minecraft:gold_ingot`", quantity = $Amount, is_prominent = false }"
}

function Add-Chowcoin-Rewards([hashtable]$Rewards) {
    for ($level = 25; $level -le $MaxLevel; $level += 25) {
        $amount = switch ($level) {
            { $_ -le 100 } { 250; break }
            { $_ -le 250 } { 500; break }
            { $_ -le 400 } { 1000; break }
            default { 2000 }
        }
        $reward = Chowcoin-Reward $amount
        if ($Rewards.ContainsKey($level)) {
            $Rewards[$level] = @($Rewards[$level]) + $reward
        } else {
            $Rewards[$level] = @($reward)
        }
    }
    $Rewards
}

function Reward-Toml([int]$Level, $Tables, [hashtable]$RelicLevels) {
    if ($RelicLevels.ContainsKey($Level)) {
        $pool = $RelicLevels[$Level]
        return "{ type = `"relic_token`", item = $(Q (Token-For-Pool $pool)), quantity = 1, is_prominent = true, data = { pool = $(Q $pool) } }"
    }

    $special = @(25, 50, 75, 100, 150, 200, 250, 300, 350, 400, 500)
    if (($special -contains $Level) -or ($Level % 10 -eq 0)) {
        $entry = $Tables.prominent[($Level - 1) % $Tables.prominent.Count]
        $prominent = "true"
    } elseif ($Level % 5 -eq 0) {
        $entry = $Tables.utility[($Level - 1) % $Tables.utility.Count]
        $prominent = "false"
    } else {
        $entry = $Tables.normal[($Level - 1) % $Tables.normal.Count]
        $prominent = "false"
    }

    $qty = [Math]::Min($entry.max, $entry.quantity + [Math]::Floor($Level / $entry.scale))
    "{ type = `"item`", item = $(Q $entry.item), quantity = $qty, is_prominent = $prominent }"
}

function Pass-Toml([string]$Id, [string]$DisplayName, [string]$Description, [string]$TitleTexture, [int]$TitleHeight, $PermanentMissions, $WeeklyMissions, $Tables, [hashtable]$RelicLevels, [hashtable]$ExtraRewards = @{}) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Generated by scripts/generate-ckdm-balance.ps1. Do not hand-maintain reward tiers.")
    $lines.Add("id = $(Q $Id)")
    $lines.Add("displayName = $(Q $DisplayName)")
    $lines.Add("description = $(Q $Description)")
    $lines.Add("titleTexture = $(Q $TitleTexture)")
    $lines.Add("titleTextureWidth = 1024")
    $lines.Add("titleTextureHeight = $TitleHeight")
    $lines.Add("categories = $(String-Array @($Id, "season_1"))")
    $lines.Add("xpEvents = []")
    $lines.Add("")
    $lines.Add("permanent_events = [")
    foreach ($mission in $PermanentMissions) {
        $lines.Add("  $(Mission-Toml $mission),")
    }
    $lines.Add("]")
    $lines.Add("")
    $lines.Add("progression = [")
    for ($level = 1; $level -le $MaxLevel; $level++) {
        $xp = $level * 100
        $baseReward = Reward-Toml $level $Tables $RelicLevels
        $rewards = @($baseReward)
        if ($ExtraRewards.ContainsKey($level)) {
            $extra = @($ExtraRewards[$level])
            $specialItems = @($extra | Where-Object { -not $_.Contains('type = "chowcoin"') })
            $chowcoins = @($extra | Where-Object { $_.Contains('type = "chowcoin"') })
            $rewards = $specialItems + @($baseReward) + $chowcoins
        }
        $lines.Add("  { xp = $xp, rewards = [ $($rewards -join ', ') ] },")
    }
    $lines.Add("]")
    $lines.Add("")
    $lines.Add("[daily_events]")
    $lines.Add("count = 0")
    $lines.Add("events = []")
    $lines.Add('time_zone = "GMT+8"')
    $lines.Add("reset_hour = 5")
    $lines.Add("reset_minute = 0")
    $lines.Add('reset_on_day = "Sunday"')
    $lines.Add("")
    $lines.Add("[weekly_events]")
    $lines.Add("count = 5")
    $lines.Add('time_zone = "GMT+8"')
    $lines.Add("reset_hour = 5")
    $lines.Add("reset_minute = 0")
    $lines.Add('reset_on_day = "Sunday"')
    $lines.Add("events = [")
    foreach ($mission in $WeeklyMissions) {
        $lines.Add("  $(Mission-Toml $mission),")
    }
    $lines.Add("]")
    $lines -join "`r`n"
}

function Assert-Pass-Config([string]$Path) {
    $text = Get-Content -LiteralPath $Path -Raw
    $tierCount = ([regex]::Matches($text, '^\s*\{\s*xp\s*=', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($tierCount -ne $MaxLevel) { throw "$Path has $tierCount progression tiers, expected $MaxLevel." }

    $progressionIndex = $text.IndexOf("progression = [")
    $dailyIndex = $text.IndexOf("[daily_events]")
    $weeklyIndex = $text.IndexOf("[weekly_events]")
    if ($progressionIndex -lt 0) { throw "$Path is missing root progression." }
    if (($dailyIndex -ge 0 -and $progressionIndex -gt $dailyIndex) -or ($weeklyIndex -ge 0 -and $progressionIndex -gt $weeklyIndex)) {
        throw "$Path progression is inside a TOML table; progression must be a root key."
    }

    $banned = @(
        "minecraft:nether_star",
        "minecraft:netherite_ingot",
        "minecraft:totem_of_undying",
        "minecraft:enchanted_golden_apple",
        "minecraft:shulker_shell",
        "minecraft:enchanted_book",
        "cobblemon:master_ball"
    )
    foreach ($item in $banned) {
        if ($text.Contains($item)) { throw "$Path contains banned BP reward $item." }
    }
}

function Pool-Toml([string]$Id, [string]$DisplayName, [string]$Ticket, [string]$Rarity, [string[]]$Pool) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Generated by scripts/generate-ckdm-balance.ps1.")
    $lines.Add("id = $(Q $Id)")
    $lines.Add("displayName = $(Q $DisplayName)")
    $lines.Add("ticket = $(Q $Ticket)")
    $lines.Add("rarity = $(Q $Rarity)")
    $lines.Add("pool = [")
    foreach ($item in ($Pool | Sort-Object)) {
        $lines.Add("  $(Q $item),")
    }
    $lines.Add("]")
    $lines -join "`r`n"
}

$commonCozy = @(
    "artifacts:anglers_hat", "artifacts:cowboy_hat", "artifacts:novelty_drinking_hat", "artifacts:plastic_drinking_hat",
    "artifacts:onion_ring", "artifacts:snorkel", "artifacts:umbrella", "artifacts:villager_hat",
    "artifacts:whoopee_cushion", "artifacts:superstitious_hat"
)
$rareCozy = @(
    "artifacts:aqua_dashers", "artifacts:flippers", "artifacts:cloud_in_a_bottle", "artifacts:helium_flamingo",
    "artifacts:kitty_slippers", "artifacts:running_shoes", "artifacts:snowshoes", "artifacts:strider_shoes",
    "artifacts:rooted_boots", "artifacts:golden_hook", "artifacts:night_vision_goggles", "artifacts:universal_attractor",
    "artifacts:warp_drive", "relics:chef_hat", "relics:cut_glass_boot", "relics:jellyfish_necklace",
    "relics:leafy_mantle", "relics:roller_skate", "relics:springy_boot", "relics:rider_flute"
)
$commonCombat = @(
    "artifacts:antidote_vessel", "artifacts:obsidian_skull", "artifacts:panic_necklace", "artifacts:shock_pendant",
    "artifacts:thorn_pendant", "artifacts:withered_bracelet", "artifacts:charm_of_sinking", "artifacts:steadfast_spikes",
    "relics:hunting_belt", "relics:piglin_mask"
)
$rareCombat = @(
    "artifacts:cross_necklace", "artifacts:crystal_heart", "artifacts:feral_claws", "artifacts:fire_gauntlet",
    "artifacts:flame_pendant", "artifacts:lucky_scarf", "artifacts:pickaxe_heater", "artifacts:pocket_piston",
    "artifacts:power_glove", "artifacts:vampiric_glove", "artifacts:scarf_of_invisibility", "relics:chorus_staff",
    "relics:experience_disperser", "relics:golden_tooth", "relics:kinetic_belt", "relics:midnight_mantle",
    "relics:pet_bone", "relics:reflective_necklace", "relics:sphere_of_self_sacrifice"
)

Write-Text (Join-Path $PoolDir "common_cozy_relics.toml") (Pool-Toml "common_cozy_relics" "Common Cozy Relic" "${ModId}:common_cozy_relic_token" "common" $commonCozy)
Write-Text (Join-Path $PoolDir "rare_cozy_relics.toml") (Pool-Toml "rare_cozy_relics" "Rare Cozy Relic" "${ModId}:rare_cozy_relic_token" "rare" $rareCozy)
Write-Text (Join-Path $PoolDir "common_combat_relics.toml") (Pool-Toml "common_combat_relics" "Common Combat Relic" "${ModId}:common_combat_relic_token" "common" $commonCombat)
Write-Text (Join-Path $PoolDir "rare_combat_relics.toml") (Pool-Toml "rare_combat_relics" "Rare Combat Relic" "${ModId}:rare_combat_relic_token" "rare" $rareCombat)

$cozyTables = @{
    normal = @(
        Reward-Entry "minecraft:bread" 2 16
        Reward-Entry "minecraft:wheat_seeds" 4 32
        Reward-Entry "minecraft:carrot" 3 24
        Reward-Entry "minecraft:potato" 3 24
        Reward-Entry "minecraft:beetroot_seeds" 4 32
        Reward-Entry "minecraft:sweet_berries" 3 24
        Reward-Entry "minecraft:apple" 2 16
        Reward-Entry "minecraft:kelp" 6 48
        Reward-Entry "minecraft:sugar_cane" 4 32
        Reward-Entry "minecraft:cocoa_beans" 3 24
        Reward-Entry "cobblemon:red_apricorn_seed" 1 6 100
        Reward-Entry "cobblemon:blue_apricorn_seed" 1 6 100
        Reward-Entry "cobblemon:green_apricorn_seed" 1 6 100
    )
    utility = @(
        Reward-Entry "minecraft:bread" 4 32
        Reward-Entry "minecraft:string" 6 48
        Reward-Entry "minecraft:bowl" 4 32
        Reward-Entry "minecraft:cookie" 6 48
        Reward-Entry "minecraft:torch" 8 64
        Reward-Entry "minecraft:lantern" 1 8
        Reward-Entry "minecraft:bone_meal" 8 64
        Reward-Entry "minecraft:paper" 8 64
        Reward-Entry "cobblemon:poke_bait" 2 16
        Reward-Entry "cobblemon:poke_ball" 3 24
        Reward-Entry "cobblemon:great_ball" 1 12
        Reward-Entry "cobblemon:oran_berry" 3 24
        Reward-Entry "create:andesite_alloy" 2 16
    )
    prominent = @(
        Reward-Entry "minecraft:fishing_rod" 1 1 10000
        Reward-Entry "minecraft:campfire" 1 4
        Reward-Entry "minecraft:pumpkin_seeds" 6 48
        Reward-Entry "minecraft:melon_seeds" 6 48
        Reward-Entry "minecraft:name_tag" 1 4
        Reward-Entry "minecraft:saddle" 1 2 10000
        Reward-Entry "farmersdelight:cooking_pot" 1 2 10000
        Reward-Entry "farmersdelight:skillet" 1 2 10000
        Reward-Entry "farmersdelight:cutting_board" 1 2 10000
        Reward-Entry "create:andesite_alloy" 4 24
        Reward-Entry "cobblemon:poke_ball" 3 24
        Reward-Entry "cobblemon:great_ball" 2 16
        Reward-Entry "cobblemon:heal_ball" 2 16
        Reward-Entry "cobblemon:poke_bait" 6 32
    )
}

$combatTables = @{
    normal = @(
        Reward-Entry "minecraft:bread" 2 16
        Reward-Entry "minecraft:arrow" 8 64
        Reward-Entry "minecraft:torch" 8 64
        Reward-Entry "minecraft:bread" 3 24
        Reward-Entry "minecraft:coal" 4 32
        Reward-Entry "minecraft:bone" 4 32
        Reward-Entry "minecraft:string" 4 32
        Reward-Entry "minecraft:flint" 4 32
        Reward-Entry "minecraft:leather" 3 24
    )
    utility = @(
        Reward-Entry "minecraft:spectral_arrow" 6 48
        Reward-Entry "minecraft:iron_ingot" 3 24
        Reward-Entry "minecraft:gold_ingot" 2 18
        Reward-Entry "minecraft:lapis_lazuli" 8 64
        Reward-Entry "minecraft:redstone" 8 64
        Reward-Entry "minecraft:experience_bottle" 2 16
        Reward-Entry "minecraft:cooked_beef" 3 24
        Reward-Entry "minecraft:ender_pearl" 1 8
        Reward-Entry "minecraft:golden_carrot" 2 16
    )
    prominent = @(
        Reward-Entry "minecraft:shield" 1 2 10000
        Reward-Entry "minecraft:bow" 1 2 10000
        Reward-Entry "minecraft:crossbow" 1 2 10000
        Reward-Entry "minecraft:iron_ingot" 8 48
        Reward-Entry "minecraft:gold_ingot" 6 36
        Reward-Entry "minecraft:amethyst_shard" 8 48
        Reward-Entry "minecraft:experience_bottle" 4 24
        Reward-Entry "minecraft:ender_pearl" 2 12
        Reward-Entry "minecraft:golden_apple" 1 3 500
        Reward-Entry "minecraft:diamond" 1 4 500
    )
}

$cozyRelics = @{
    50 = "common_cozy_relics"; 100 = "rare_cozy_relics"; 200 = "common_cozy_relics"; 350 = "common_cozy_relics";
    500 = "rare_cozy_relics"
}
$combatRelics = @{
    50 = "common_combat_relics"; 100 = "rare_combat_relics"; 200 = "common_combat_relics"; 350 = "common_combat_relics";
    500 = "rare_combat_relics"
}
$cozyExtraRewards = @{
    1 = @((Fixed-Reward "minecraft:torch" 16 $false))
    3 = @((Fixed-Reward "paraglider:paraglider" 1 $true))
    10 = @((Fixed-Reward "cobblemon:poke_bait" 8 $true), (Fixed-Reward "cobblemon:poke_ball" 6 $true))
    18 = @((Fixed-Reward "cobblemon:poke_ball" 8 $true))
    35 = @((Fixed-Reward "cobblemon:great_ball" 4 $true))
    75 = @((Fixed-Reward "cobblemon:heal_ball" 4 $true))
    125 = @((Fixed-Reward "cobblemon:nest_ball" 4 $true))
    175 = @((Fixed-Reward "cobblemon:lure_ball" 4 $true))
    275 = @((Fixed-Reward "cobblemon:quick_ball" 4 $true))
    425 = @((Fixed-Reward "cobblemon:great_ball" 8 $true))
    500 = @((Fixed-Reward "minecraft:elytra" 1 $true))
}
$cozyExtraRewards = Add-Chowcoin-Rewards $cozyExtraRewards
$combatExtraRewards = @{
    1 = @((Fixed-Reward "minecraft:arrow" 32 $false))
    8 = @((Fixed-Reward "sophisticatedbackpacks:backpack" 1 $true))
    18 = @((Fixed-Reward "sophisticatedbackpacks:pickup_upgrade" 1 $true))
    32 = @((Fixed-Reward "sophisticatedbackpacks:filter_upgrade" 1 $true))
    48 = @((Fixed-Reward "sophisticatedbackpacks:stack_upgrade_starter_tier" 1 $true))
    64 = @((Fixed-Reward "sophisticatedbackpacks:deposit_upgrade" 1 $true))
    82 = @((Fixed-Reward "sophisticatedbackpacks:refill_upgrade" 1 $true))
    100 = @((Fixed-Reward "sophisticatedbackpacks:stack_upgrade_tier_1" 1 $true))
    128 = @((Fixed-Reward "sophisticatedbackpacks:restock_upgrade" 1 $true))
    165 = @((Fixed-Reward "sophisticatedbackpacks:crafting_upgrade" 1 $true))
    225 = @((Fixed-Reward "sophisticatedbackpacks:advanced_pickup_upgrade" 1 $true))
    275 = @((Fixed-Reward "sophisticatedbackpacks:advanced_filter_upgrade" 1 $true))
    300 = @((Fixed-Reward "sophisticatedbackpacks:stack_upgrade_tier_2" 1 $true))
    325 = @((Fixed-Reward "sophisticatedbackpacks:jukebox_upgrade" 1 $true))
    375 = @((Fixed-Reward "sophisticatedbackpacks:advanced_deposit_upgrade" 1 $true))
    425 = @((Fixed-Reward "sophisticatedbackpacks:advanced_refill_upgrade" 1 $true))
    475 = @((Fixed-Reward "sophisticatedbackpacks:advanced_restock_upgrade" 1 $true))
}
$combatExtraRewards = Add-Chowcoin-Rewards $combatExtraRewards

$pokemonTypes = @("normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy")
$generationMissions = @(
    @{ id = "kanto"; name = "Kanto"; scan_goals = @(25, 75, 151); scan_xp = @(100, 250, 400); catch_goals = @(10, 50, 100, 151); catch_xp = @(50, 100, 200, 400) }
    @{ id = "johto"; name = "Johto"; scan_goals = @(25, 75, 100); scan_xp = @(100, 225, 300); catch_goals = @(10, 40, 75, 100); catch_xp = @(50, 100, 175, 300) }
    @{ id = "hoenn"; name = "Hoenn"; scan_goals = @(30, 90, 135); scan_xp = @(100, 250, 400); catch_goals = @(10, 50, 100, 135); catch_xp = @(50, 100, 200, 400) }
    @{ id = "sinnoh"; name = "Sinnoh"; scan_goals = @(25, 75, 107); scan_xp = @(100, 225, 350); catch_goals = @(10, 40, 75, 107); catch_xp = @(50, 100, 175, 350) }
    @{ id = "unova"; name = "Unova"; scan_goals = @(30, 100, 156); scan_xp = @(100, 300, 500); catch_goals = @(10, 50, 100, 156); catch_xp = @(50, 125, 250, 500) }
    @{ id = "kalos"; name = "Kalos"; scan_goals = @(20, 50, 72); scan_xp = @(100, 200, 300); catch_goals = @(10, 30, 50, 72); catch_xp = @(50, 100, 150, 250) }
    @{ id = "alola"; name = "Alola"; scan_goals = @(25, 60, 88); scan_xp = @(100, 225, 350); catch_goals = @(10, 35, 60, 88); catch_xp = @(50, 100, 175, 300) }
    @{ id = "galar"; name = "Galar"; scan_goals = @(25, 70, 96); scan_xp = @(100, 250, 400); catch_goals = @(10, 40, 70, 96); catch_xp = @(50, 100, 175, 350) }
    @{ id = "paldea"; name = "Paldea"; scan_goals = @(30, 80, 120); scan_xp = @(100, 250, 450); catch_goals = @(10, 50, 90, 120); catch_xp = @(50, 100, 200, 400) }
)

$cozyPermanent = @(
    Mission-Entry "permanent_scan_pokedex" "cobblemon:pokedex_scanned" "Scan {goal} Pokedex Entries" @(25, 100, 250, 500, 750, 1000) @(75, 150, 300, 500, 750, 1200)
)
foreach ($generation in $generationMissions) {
    $cozyPermanent += Mission-Entry "permanent_scan_$($generation.id)_pokemon" "cobblemon:scan_$($generation.id)_pokemon" "Scan {goal} $($generation.name) Pokemon" $generation.scan_goals $generation.scan_xp
}
$cozyPermanent += @(
    Mission-Entry "permanent_pokemon_friendship" "cobblemon:pokemon_friendship_maxed" "Max Friendship with {goal} Pokemon" @(1, 3, 6, 10, 20) @(100, 200, 350, 500, 850)
    Mission-Entry "permanent_pokemon_rider" "cobblemon:pokemon_mount_traveled" "Travel {goal} Blocks on Pokemon" @(10000, 50000, 150000, 500000, 1000000) @(150, 400, 900, 1600, 2500)
    Mission-Entry "permanent_pokemon_flight" "cobblemon:pokemon_mount_flying_traveled" "Travel {goal} Blocks on Flying Pokemon" @(10000, 50000, 150000, 400000) @(150, 400, 900, 1600)
    Mission-Entry "permanent_pokemon_land_rider" "cobblemon:pokemon_mount_land_traveled" "Travel {goal} Blocks on Land Pokemon" @(10000, 50000, 150000, 400000) @(150, 400, 900, 1600)
    Mission-Entry "permanent_crop_keeper" "minecraft:crop_harvested" "Harvest {goal} Crops" @(256, 1024, 4096, 16000) @(150, 400, 900, 1800)
    Mission-Entry "permanent_quality_crop_keeper" "quality_food:gold_quality_crop_harvested" "Harvest {goal} Gold Quality Crops" @(64, 256, 1024, 4096) @(150, 400, 900, 1800)
    Mission-Entry "permanent_fisher" "minecraft:fish_caught" "Catch {goal} Fish" @(50, 250, 1000, 3000) @(150, 400, 900, 1800)
    Mission-Entry "permanent_animal_keeper" "minecraft:animal_bred" "Breed {goal} Animals" @(25, 100, 300, 1000) @(150, 400, 900, 1600)
    Mission-Entry "permanent_villager_trader" "minecraft:villager_traded" "Trade with Villagers {goal} Times" @(25, 100, 300, 1000) @(150, 400, 900, 1600)
    Mission-Entry "permanent_cooking_pot_meals" "farmersdelight:cooking_pot_meal_cooked" "Cook {goal} Cooking Pot Meals" @(25, 100, 300, 1000) @(200, 500, 1000, 2000)
    Mission-Entry "permanent_feast_servings" "farmersdelight:feast_served" "Serve {goal} Feast Portions" @(10, 50, 150, 500) @(200, 600, 1200, 2200)
    Mission-Entry "permanent_shipping_value" "gisketchs_chowkingdom_mod:shipping_bin_value_sold" "Ship {goal} Chowcoins Worth" @(10000, 50000, 200000, 750000) @(150, 500, 1200, 2500)
)
$cozyWeekly = @(
    Mission-Entry "weekly_harvest_crops" "minecraft:crop_harvested" "Harvest {goal} Crops" @(384) @(220)
    Mission-Entry "weekly_gold_quality_crops" "quality_food:gold_quality_crop_harvested" "Harvest {goal} Gold Quality Crops" @(64) @(260)
    Mission-Entry "weekly_go_fishing" "minecraft:fish_caught" "Catch {goal} Fish" @(30) @(220)
    Mission-Entry "weekly_cooking_pot_meals" "farmersdelight:cooking_pot_meal_cooked" "Cook {goal} Cooking Pot Meals" @(16) @(240)
    Mission-Entry "weekly_cutting_board_outputs" "farmersdelight:cutting_board_outputs" "Make {goal} Cutting Board Outputs" @(48) @(220)
    Mission-Entry "weekly_feast_servings" "farmersdelight:feast_served" "Serve {goal} Feast Portions" @(8) @(300)
    Mission-Entry "weekly_breed_animals" "minecraft:animal_bred" "Breed {goal} Animals" @(20) @(220)
    Mission-Entry "weekly_ship_value" "gisketchs_chowkingdom_mod:shipping_bin_value_sold" "Ship {goal} Chowcoins Worth" @(25000) @(300)
    Mission-Entry "weekly_ship_quality_value" "gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold" "Ship {goal} Quality Chowcoins Worth" @(10000) @(320)
    Mission-Entry "weekly_pokemon_mount_travel" "cobblemon:pokemon_mount_traveled" "Travel {goal} Blocks on Pokemon" @(10000) @(220)
    Mission-Entry "weekly_farmer_meals_eaten" "farmersdelight:meal_eaten" "Eat {goal} Farmer's Delight Meals" @(12) @(200)
)
$combatPermanent = @(
    Mission-Entry "permanent_pokemon_caught" "cobblemon:pokemon_caught" "Catch {goal} Pokemon" @(10, 50, 150, 400, 800) @(50, 100, 200, 400, 750)
)
foreach ($generation in $generationMissions) {
    $combatPermanent += Mission-Entry "permanent_catch_$($generation.id)_pokemon" "cobblemon:catch_$($generation.id)_pokemon" "Catch {goal} $($generation.name) Pokemon" $generation.catch_goals $generation.catch_xp
}
foreach ($type in $pokemonTypes) {
    $displayType = (Get-Culture).TextInfo.ToTitleCase($type)
    $combatPermanent += Mission-Entry "permanent_catch_$($type)_type" "cobblemon:catch_$($type)_type" "Catch {goal} $displayType-type Pokemon" @(5, 25, 75, 150) @(25, 75, 150, 250)
}
$combatPermanent += @(
    Mission-Entry "permanent_monster_slayer" "minecraft:monster_killed" "Defeat {goal} Monsters" @(100, 500, 2000, 8000) @(200, 600, 1400, 3000)
    Mission-Entry "permanent_zombie_slayer" "minecraft:entity_killed" "Defeat {goal} Zombies" @(50, 250, 750, 2500) @(150, 500, 1000, 2000) @{ entity = "minecraft:zombie" }
    Mission-Entry "permanent_skeleton_slayer" "minecraft:entity_killed" "Defeat {goal} Skeletons" @(50, 250, 750, 2500) @(150, 500, 1000, 2000) @{ entity = "minecraft:skeleton" }
    Mission-Entry "permanent_creeper_slayer" "minecraft:entity_killed" "Defeat {goal} Creepers" @(25, 100, 300, 1000) @(150, 500, 1000, 2200) @{ entity = "minecraft:creeper" }
    Mission-Entry "permanent_enderman_slayer" "minecraft:entity_killed" "Defeat {goal} Endermen" @(10, 50, 150, 500) @(150, 500, 1100, 2400) @{ entity = "minecraft:enderman" }
    Mission-Entry "permanent_nether_hunter" "minecraft:monster_killed" "Defeat {goal} Nether Monsters" @(50, 200, 750, 2500) @(200, 600, 1400, 3000) @{ dimension = "minecraft:the_nether" }
    Mission-Entry "permanent_combat_traveler" "minecraft:travel_on_foot" "Travel {goal} Blocks on Foot" @(25000, 100000, 500000, 1500000) @(150, 500, 1200, 2500)
)
$combatWeekly = @(
    Mission-Entry "weekly_hunt_mobs" "minecraft:monster_killed" "Defeat {goal} Monsters" @(100) @(240) @{} "combat:monster_general"
    Mission-Entry "weekly_hunt_zombies" "minecraft:entity_killed" "Defeat {goal} Zombies" @(30) @(220) @{ entity = "minecraft:zombie" } "combat:monster_specific"
    Mission-Entry "weekly_hunt_skeletons" "minecraft:entity_killed" "Defeat {goal} Skeletons" @(30) @(220) @{ entity = "minecraft:skeleton" } "combat:monster_specific"
    Mission-Entry "weekly_hunt_spiders" "minecraft:entity_killed" "Defeat {goal} Spiders" @(20) @(220) @{ entity = "minecraft:spider" } "combat:monster_specific"
    Mission-Entry "weekly_hunt_creepers" "minecraft:entity_killed" "Defeat {goal} Creepers" @(12) @(260) @{ entity = "minecraft:creeper" } "combat:monster_specific"
    Mission-Entry "weekly_hunt_endermen" "minecraft:entity_killed" "Defeat {goal} Endermen" @(8) @(300) @{ entity = "minecraft:enderman" } "combat:monster_specific"
    Mission-Entry "weekly_combat_travel" "minecraft:travel_on_foot" "Travel {goal} Blocks on Foot" @(10000) @(220)
    Mission-Entry "weekly_catch_pokemon" "cobblemon:pokemon_caught" "Catch {goal} Pokemon" @(8) @(260) @{} "combat:pokemon_catch"
    Mission-Entry "weekly_catch_fire_type" "cobblemon:catch_fire_type" "Catch {goal} Fire-type Pokemon" @(4) @(260) @{} "combat:pokemon_catch"
    Mission-Entry "weekly_catch_water_type" "cobblemon:catch_water_type" "Catch {goal} Water-type Pokemon" @(4) @(260) @{} "combat:pokemon_catch"
    Mission-Entry "weekly_catch_electric_type" "cobblemon:catch_electric_type" "Catch {goal} Electric-type Pokemon" @(4) @(260) @{} "combat:pokemon_catch"
)

Write-Text (Join-Path $PassDir "cozy.toml") (Pass-Toml "cozy" "Cozy Pass" "Long-season cozy progression from farming, cooking, fishing, shipping, and Cobblemon care." "${ModId}:textures/gui/cozy_pass.png" 230 $cozyPermanent $cozyWeekly $cozyTables $cozyRelics $cozyExtraRewards)
Write-Text (Join-Path $PassDir "combat.toml") (Pass-Toml "combat" "Combat Pass" "Long-season combat progression from limited missions, exploration, and boss preparation." "${ModId}:textures/gui/combat_pass.png" 215 $combatPermanent $combatWeekly $combatTables $combatRelics $combatExtraRewards)
Assert-Pass-Config (Join-Path $PassDir "cozy.toml")
Assert-Pass-Config (Join-Path $PassDir "combat.toml")

$summary = @"
# CKDM Generated Balance Summary

Generated by `scripts/generate-ckdm-balance.ps1`.

## Battlepasses

- Cozy pass tiers: 500
- Combat pass tiers: 500
- XP per level: 100
- Max tier XP: 50000
- Raw direct XP loops: disabled in generated pass configs

## Guaranteed Battlepass Relic Tokens

- Common Cozy: 3
- Rare Cozy: 2
- Common Combat: 3
- Rare Combat: 2
- Total: 10

## Starter Rewards

- Cozy level 1 grants bread and torches.
- Cozy level 3 grants paraglider:paraglider.
- Cozy level 10 grants cobblemon:poke_bait and cobblemon:poke_ball.
- Cozy milestone rewards include extra Poke Balls, Great Balls, Heal Balls, Nest Balls, Lure Balls, and Quick Balls.
- Combat level 1 grants bread and arrows.
- Combat level 8 grants sophisticatedbackpacks:backpack.

## Backpack Upgrades

- Backpack crafting remains enabled.
- Backpack upgrade recipes should be disabled through recipe_disabler.toml.
- Combat pass grants curated upgrades: pickup, filter, deposit, refill, restock, crafting, jukebox, starter/tier 1/tier 2 stack upgrades, and advanced pickup/filter/deposit/refill/restock.
- Combat pass still excludes high-risk upgrades: magnet, void, feeding, XP pump, everlasting, inception, infinity, tank, battery, and high stack tiers.

## Chowcoin Rewards

- Chowcoin rewards use type = chowcoin.
- Chowcoins appear every 25 levels on both passes.
- Levels 25-100 grant 250 each.
- Levels 125-250 grant 500 each.
- Levels 275-400 grant 1000 each.
- Levels 425-500 grant 2000 each.
- Total per pass: 18000.
- Total across both passes: 36000.

## Level 500 Elytra Gate

- Cozy level 500 grants 1 Elytra in addition to the rare Cozy relic token.
- All Elytra remain obtainable from loot, but cannot be worn until overall Battlepass Level 500.

## Relic Token Levels

- Common tokens: 50, 200, 350
- Rare tokens: 100, 500

## XP Mission Bands

- Weekly missions rotate 5 active one-time missions per pass from curated pools.
- Weekly Cozy missions: 200-320 XP.
- Weekly Combat missions: 220-300 XP.
- Generic Pokemon caught permanent chain totals 1500 XP across 800 catches.
- Generation catch chains total roughly 550-925 XP each.
- Type catch chains total 500 XP each.
- Pokedex scanned totals 2975 XP through 1000 scanned Pokemon.
- Friendship maxed totals 2000 XP through 20 max-friendship Pokemon.
- Large travel chains are intentionally long and capped as long-term bonuses.

## Store Configs

- Store configs are intentionally not generated by this script.
- Existing curated stores should be preserved.
- Future store balance should be targeted price edits only.

## Excluded From Generated Pass Rewards

- minecraft:nether_star
- minecraft:netherite_ingot
- minecraft:totem_of_undying
- minecraft:enchanted_golden_apple
- minecraft:shulker_shell
- minecraft:enchanted_book
- direct old common/rare relic token rewards

## Store Exclusions

- Master Ball
- direct relic tokens
- raw boss rewards
- netherite progression
- economy-breaking tech items
"@
Write-Text (Join-Path $DocsDir "ckdm-balance-summary.md") $summary

Write-Host "Generated CKDM balance configs and summary."
