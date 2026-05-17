param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ModsDir = (Join-Path $RepoRoot "runs/client/mods"),
    [string]$PricesPath = (Join-Path $RepoRoot "runs/client/config/gisketchs_chowkingdom_mod/shipping_bin/prices.toml"),
    [string]$OutputPath = (Join-Path $RepoRoot "docs/generated/shipping-bin-offline-audit.md"),
    [string]$CsvOutputPath = (Join-Path $RepoRoot "docs/generated/shipping-bin-full-audit.csv"),
    [string]$SuggestionsPath = (Join-Path $RepoRoot "docs/generated/shipping-bin-price-suggestions.toml")
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$foodMods = @(
    "minecraft", "farmersdelight", "expandeddelight", "oceansdelight", "ubesdelight",
    "beachparty", "brewery", "vinery", "create", "cobblemon", "herbalbrews",
    "candlelight", "bakery", "meadow"
)
$cropWords = @("wheat", "carrot", "potato", "beetroot", "cabbage", "tomato", "onion", "rice", "berry", "berries", "corn", "pepper", "asparagus", "cranberr", "grape", "apricorn", "apple", "cherry", "lemon", "peanut", "cinnamon", "tea", "coffee", "cocoa", "pumpkin", "melon")
$fishWords = @("cod", "salmon", "fish", "puffer", "mussel", "squid", "guardian", "shrimp", "crab", "lobster", "clam")
$animalWords = @("egg", "milk", "honey", "cheese", "bacon", "beef", "chicken", "mutton", "pork", "ham", "wool")
$mealWords = @("soup", "stew", "pie", "salad", "sandwich", "burger", "pasta", "roll", "rice", "cake", "cookie", "feast", "platter", "meal", "skewer", "wrap", "noodles", "dumpling", "ratatouille", "toast", "frittata", "casserole")
$drinkWords = @("juice", "wine", "cider", "mead", "_tea", "tea_", "coffee", "cocktail", "smoothie", "milkshake", "brew")
$rareWords = @("guardian", "elder_guardian", "nether", "blaze", "dragon", "chorus", "starf", "lansat", "enigma", "custap", "micle", "rowap", "jaboca", "netherite", "diamond")
$blockedWords = @(
    "spawn_egg", "creative", "debug", "command", "sword", "dagger", "knife", "bow", "crossbow", "wand", "staff",
    "helmet", "chestplate", "leggings", "boots", "armor", "shield", "relic", "token", "artifact", "scroll", "spell_book",
    "upgrade", "template", "apron", "hammer", "fossil", "athame", "lamp", "cask", "rack", "table", "chair", "cabinet", "drawer", "shelf", "window", "door", "fence", "slab",
    "stairs", "trapdoor", "sign", "button", "pressure_plate", "planks", "log", "wood", "leaves", "sapling", "boat",
    "lattice", "beam", "barrel", "press", "floorboard", "bush", "grapevine"
)
$exactBlocked = @("minecraft:bowl", "minecraft:glass_bottle", "vinery:wine_bottle")
$containerPrices = @{
    "minecraft:bowl" = 2
    "minecraft:glass_bottle" = 4
    "minecraft:bucket" = 8
    "minecraft:milk_bucket" = 18
    "vinery:wine_bottle" = 12
}
$toolLike = @("knife", "cutting_board", "pot", "pan", "skillet", "basket", "cup", "glass")
$blockedNamespaces = @(
    "arsenal", "artifacts", "betterarcheology", "cataclysm", "cobblesafari", "mega_showdown", "cluttered",
    "another_furniture", "relics", "simplyswords", "simplymore", "irons_spellbooks"
)

function Has-Any([string]$Value, [string[]]$Words) {
    foreach ($word in $Words) {
        if ($Value.Contains($word)) { return $true }
    }
    return $false
}

function Item-Path([string]$ItemId) {
    if ($ItemId -notmatch ":") { return $ItemId }
    return $ItemId.Split(":", 2)[1]
}

function Namespace-Of([string]$ItemId) {
    if ($ItemId -notmatch ":") { return "" }
    return $ItemId.Split(":", 2)[0]
}

function Is-SeedLike([string]$ItemId) {
    $path = Item-Path $ItemId
    return $path.EndsWith("_seed") -or $path.EndsWith("_seeds") -or $path.Contains("seeds_")
}

function Is-Blocked([string]$ItemId) {
    if ($exactBlocked -contains $ItemId) { return $true }
    if ($blockedNamespaces -contains (Namespace-Of $ItemId)) { return $true }
    $path = Item-Path $ItemId
    if (Has-Any $path $blockedWords) { return $true }
    if ($path.EndsWith("_bag") -or $path.Contains("_crate") -or $path.Contains("_box")) { return $true }
    return $false
}

function Is-RareLike([string]$ItemId) {
    return Has-Any (Item-Path $ItemId) $rareWords
}

function Category-Of([string]$ItemId) {
    $ns = Namespace-Of $ItemId
    $path = Item-Path $ItemId
    if (Is-Blocked $ItemId) { return "blocked" }
    if (Is-SeedLike $ItemId) { return "seed" }
    if (Has-Any $path $drinkWords) { return "drink" }
    if ($path.Contains("feast") -or $path.EndsWith("_block") -or $path.Contains("platter")) { return "feast" }
    if (Has-Any $path $mealWords) { return "meal" }
    if ($path.StartsWith("cooked_") -or $path.Contains("_cooked_") -or $path.Contains("baked_") -or $path.Contains("fried_") -or $path.Contains("grilled_") -or $path.Contains("roasted_")) { return "cooked" }
    if (Has-Any $path $fishWords) { return "fish" }
    if (Has-Any $path $animalWords) { return "animal" }
    if (Has-Any $path $cropWords) { return "crop" }
    if (($foodMods -contains $ns) -and (Has-Any $path ($cropWords + $fishWords + $animalWords + $mealWords + $drinkWords))) { return "food-like" }
    return "manual"
}

function Is-SellCandidate([string]$ItemId, [bool]$CurrentlyPriced) {
    if (Is-Blocked $ItemId) { return $false }
    $category = Category-Of $ItemId
    if ($category -ne "manual" -and $category -ne "blocked") { return $true }
    if ($CurrentlyPriced) { return $true }
    return $false
}

function Category-Cap([string]$Category, [string]$ItemId) {
    if (Is-RareLike $ItemId) { return 1200 }
    switch ($Category) {
        "seed" { return 2 }
        "crop" { return 40 }
        "fish" { return 80 }
        "animal" { return 90 }
        "cooked" { return 120 }
        "meal" { return 250 }
        "drink" { return 450 }
        "feast" { return 700 }
        "food-like" { return 180 }
        default { return 120 }
    }
}

function Profit-Multiplier([string]$Category, [int]$StepCount) {
    $stepBonus = [Math]::Min(0.20, [Math]::Max(0, $StepCount - 1) * 0.05)
    switch ($Category) {
        "cooked" { return 1.20 + $stepBonus }
        "meal" { return 1.30 + $stepBonus }
        "drink" { return 1.35 + $stepBonus }
        "feast" { return 1.35 + $stepBonus }
        default { return 1.15 + $stepBonus }
    }
}

function Process-Price([string]$ItemId, [string]$Category, [int]$KnownCost, [int]$StepCount, [string]$Processes) {
    if ($Processes -match "vinery_wine") {
        $raw = ($KnownCost * 2.0) + 60
        if (Is-RareLike $ItemId -or (Item-Path $ItemId) -match "jellie|noir|solaris|lilitu|mojang|special|fright|creeper") {
            $raw = ($KnownCost * 2.2) + 90
        }
        return [Math]::Min((Category-Cap $Category $ItemId), (Round-Price $raw))
    }
    $raw = [double]$KnownCost * (Profit-Multiplier $Category $StepCount)
    return [Math]::Min((Category-Cap $Category $ItemId), (Round-Price $raw))
}

function Round-Price([double]$Value) {
    if ($Value -le 20) { return [int][Math]::Ceiling($Value) }
    if ($Value -le 100) { return [int]([Math]::Ceiling($Value / 5.0) * 5) }
    return [int]([Math]::Ceiling($Value / 10.0) * 10)
}

function Heuristic-BasePrice([string]$ItemId) {
    if ($containerPrices.ContainsKey($ItemId)) { return [int]$containerPrices[$ItemId] }
    $category = Category-Of $ItemId
    switch ($category) {
        "seed" { return 1 }
        "crop" { return 10 }
        "fish" { return 24 }
        "animal" { return 24 }
        "cooked" { return 36 }
        "meal" { return 90 }
        "drink" { return 40 }
        "feast" { return 320 }
        default { return $null }
    }
}

function Escape-Cell([string]$Value) {
    if ($null -eq $Value) { return "" }
    return ($Value -replace '\|', '\|')
}

function Read-ZipText($Zip, $Entry) {
    $stream = $Entry.Open()
    try {
        $reader = [System.IO.StreamReader]::new($stream)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $stream.Dispose()
    }
}

function Convert-Ingredient($Ingredient) {
    $items = @()
    if ($null -eq $Ingredient) { return $items }
    if ($Ingredient -is [string]) {
        if ($Ingredient -match ":") { return @([pscustomobject]@{ Kind = "item"; Value = $Ingredient; Count = 1 }) }
        return $items
    }
    if ($Ingredient.item) { return @([pscustomobject]@{ Kind = "item"; Value = [string]$Ingredient.item; Count = 1 }) }
    if ($Ingredient.id) { return @([pscustomobject]@{ Kind = "item"; Value = [string]$Ingredient.id; Count = 1 }) }
    if ($Ingredient.tag) { return @([pscustomobject]@{ Kind = "tag"; Value = [string]$Ingredient.tag; Count = 1 }) }
    if ($Ingredient.items) {
        foreach ($candidate in @($Ingredient.items)) {
            if ($candidate -is [string] -and $candidate -match ":") {
                return @([pscustomobject]@{ Kind = "item"; Value = $candidate; Count = 1 })
            }
        }
    }
    return $items
}

function Add-IngredientMany([System.Collections.Generic.List[object]]$List, $Ingredient, [int]$Count) {
    foreach ($entry in Convert-Ingredient $Ingredient) {
        $List.Add([pscustomobject]@{ Kind = $entry.Kind; Value = $entry.Value; Count = $Count })
    }
}

function Get-ResultEntries($Json) {
    $results = @()
    foreach ($nodeName in @("result", "output")) {
        $node = $Json.$nodeName
        if ($null -eq $node) { continue }
        if ($node -is [string]) {
            if ($node -match ":") { $results += [pscustomobject]@{ Item = $node; Count = 1 } }
        } elseif ($node.item -or $node.id) {
            $item = if ($node.item) { [string]$node.item } else { [string]$node.id }
            $count = if ($node.count) { [int]$node.count } else { 1 }
            if ($item -match ":") { $results += [pscustomobject]@{ Item = $item; Count = $count } }
        }
    }
    if ($Json.results) {
        foreach ($node in @($Json.results)) {
            if ($node.item -or $node.id) {
                $item = if ($node.item) { [string]$node.item } else { [string]$node.id }
                $count = if ($node.count) { [int]$node.count } else { 1 }
                if ($item -match ":") { $results += [pscustomobject]@{ Item = $item; Count = $count } }
            }
        }
    }
    return $results
}

function Juice-Item([string]$Type) {
    switch ($Type) {
        "apple" { return "vinery:apple_juice" }
        "red_general" { return "vinery:red_grapejuice" }
        "white_general" { return "vinery:white_grapejuice" }
        "red_jungle" { return "vinery:red_jungle_grapejuice" }
        "white_jungle" { return "vinery:white_jungle_grapejuice" }
        "red_savanna" { return "vinery:red_savanna_grapejuice" }
        "white_savanna" { return "vinery:white_savanna_grapejuice" }
        "red_taiga" { return "vinery:red_taiga_grapejuice" }
        "white_taiga" { return "vinery:white_taiga_grapejuice" }
        default { return $null }
    }
}

function Recipe-FromJson($Json, [string]$Path) {
    $results = @(Get-ResultEntries $Json)
    if ($results.Count -eq 0) { return @() }
    $type = [string]$Json.type
    $ingredients = [System.Collections.Generic.List[object]]::new()
    $process = "recipe"

    if ($type -match "crafting_shaped" -and $Json.key -and $Json.pattern) {
        $counts = @{}
        foreach ($line in @($Json.pattern)) {
            foreach ($char in ([string]$line).ToCharArray()) {
                $symbol = [string]$char
                if ($symbol -eq " ") { continue }
                $counts[$symbol] = 1 + [int]($counts[$symbol] ?? 0)
            }
        }
        foreach ($property in $Json.key.PSObject.Properties) {
            if ($counts.ContainsKey($property.Name)) {
                Add-IngredientMany $ingredients $property.Value ([int]$counts[$property.Name])
            }
        }
    } else {
        foreach ($field in @("ingredients", "ingredient", "input", "inputs")) {
            if ($Json.$field) {
                foreach ($ingredient in @($Json.$field)) {
                    Add-IngredientMany $ingredients $ingredient 1
                }
            }
        }
    }

    if ($type -match "smelting|smoking|campfire|blasting") { $process = "cooking" }
    if ($type -match "cutting") { $process = "cutting" }
    if ($type -match "farmersdelight") { $process = "farmersdelight" }
    if ($type -match "create") { $process = "create" }
    if ($type -match "vinery:wine_fermentation") {
        $process = "vinery_wine"
        if ($Json.juice -and $Json.juice.type) {
            $juiceItem = Juice-Item ([string]$Json.juice.type)
            if ($juiceItem) {
                $amount = if ($Json.juice.amount) { [int]$Json.juice.amount } else { 10 }
                $ingredients.Add([pscustomobject]@{ Kind = "item"; Value = $juiceItem; Count = [Math]::Max(1, [Math]::Ceiling($amount / 10.0)) })
            }
        }
        if ($Json.wine_bottle -and $Json.wine_bottle.required -eq $true) {
            $ingredients.Add([pscustomobject]@{ Kind = "item"; Value = "vinery:wine_bottle"; Count = 1 })
        }
    } elseif ($type -match "vinery:apple_mashing|vinery:apple_fermenting") {
        $process = "vinery_process"
        if ($Json.wine_bottle -and $Json.wine_bottle.required -eq $true) {
            $ingredients.Add([pscustomobject]@{ Kind = "item"; Value = "vinery:wine_bottle"; Count = 1 })
        }
    }

    $recipes = @()
    foreach ($result in $results) {
        if ($ingredients.Count -eq 0) { continue }
        $recipes += [pscustomobject]@{
            Result = $result.Item
            Count = [Math]::Max(1, [int]$result.Count)
            Ingredients = @($ingredients)
            Type = $type
            Process = $process
            Path = $Path
        }
    }
    return $recipes
}

$prices = [ordered]@{}
Select-String -Path $PricesPath -Pattern '\{ item = "([^"]+)", price_amount = ([0-9]+)' | ForEach-Object {
    if ($_.Line -match '\{ item = "([^"]+)", price_amount = ([0-9]+)') {
        $prices[$matches[1]] = [int]$matches[2]
    }
}

$names = @{}
$itemsSeen = [System.Collections.Generic.HashSet[string]]::new()
$recipesByResult = @{}
$recipeCount = @{}
$tagItems = @{}
$modJarCount = 0
$recipeSeen = 0

Get-ChildItem -LiteralPath $ModsDir -Filter *.jar | ForEach-Object {
    $modJarCount++
    $zip = [IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match '^assets/([^/]+)/lang/en_us\.json$') {
                $json = $null
                try { $json = Read-ZipText $zip $entry | ConvertFrom-Json } catch { continue }
                foreach ($property in $json.PSObject.Properties) {
                    if ($property.Name -match '^(item|block)\.([^\.]+)\.(.+)$') {
                        if ($matches[3] -match '\.') { continue }
                        $itemId = "$($matches[2]):$($matches[3])"
                        $names[$itemId] = [string]$property.Value
                        [void]$itemsSeen.Add($itemId)
                    }
                }
            } elseif ($entry.FullName -match '^data/([^/]+)/tags/items?/(.+)\.json$') {
                $tagId = "$($matches[1]):$($matches[2])"
                $json = $null
                try { $json = Read-ZipText $zip $entry | ConvertFrom-Json } catch { continue }
                if (-not $tagItems.ContainsKey($tagId)) { $tagItems[$tagId] = [System.Collections.Generic.HashSet[string]]::new() }
                foreach ($value in @($json.values)) {
                    $id = $null
                    if ($value -is [string]) { $id = $value }
                    elseif ($value.id) { $id = [string]$value.id }
                    if ($id -and $id -match ":" -and -not $id.StartsWith("#")) {
                        [void]$tagItems[$tagId].Add($id)
                        [void]$itemsSeen.Add($id)
                    }
                }
            } elseif ($entry.FullName -match '^data/([^/]+)/(recipe|recipes)/.+\.json$') {
                $json = $null
                try { $json = Read-ZipText $zip $entry | ConvertFrom-Json } catch { continue }
                foreach ($recipe in @(Recipe-FromJson $json $entry.FullName)) {
                    [void]$itemsSeen.Add($recipe.Result)
                    if (-not $recipesByResult.ContainsKey($recipe.Result)) { $recipesByResult[$recipe.Result] = @() }
                    $recipesByResult[$recipe.Result] += $recipe
                    $recipeCount[$recipe.Result] = 1 + [int]($recipeCount[$recipe.Result] ?? 0)
                    $recipeSeen++
                    foreach ($ingredient in @($recipe.Ingredients)) {
                        if ($ingredient.Kind -eq "item") { [void]$itemsSeen.Add($ingredient.Value) }
                    }
                }
            }
        }
    } finally {
        $zip.Dispose()
    }
}

foreach ($itemId in $prices.Keys) { [void]$itemsSeen.Add($itemId) }

$value = @{}
$source = @{}
$steps = @{}
foreach ($itemId in $itemsSeen) {
    $base = Heuristic-BasePrice $itemId
    if ($null -ne $base) {
        $value[$itemId] = [int]$base
        $source[$itemId] = "heuristic"
        $steps[$itemId] = 0
    }
    if ($prices.Contains($itemId)) {
        $category = Category-Of $itemId
        $cap = Category-Cap $category $itemId
        $current = [int]$prices[$itemId]
        if ($category -in @("seed", "crop", "fish", "animal") -and $current -le $cap) {
            $value[$itemId] = $current
            $source[$itemId] = "current-base"
            $steps[$itemId] = 0
        }
    }
}
foreach ($key in $containerPrices.Keys) {
    $value[$key] = [int]$containerPrices[$key]
    $source[$key] = "container"
    $steps[$key] = 0
}

function Ingredient-Value($Ingredient, $Values, $TagItems) {
    if ($Ingredient.Kind -eq "item") {
        if ($Values.ContainsKey($Ingredient.Value)) { return [int]$Values[$Ingredient.Value] * [int]$Ingredient.Count }
        $path = Item-Path $Ingredient.Value
        if (Has-Any $path $toolLike) { return 0 }
        return $null
    }
    if ($Ingredient.Kind -eq "tag") {
        $tag = $Ingredient.Value
        if (-not $TagItems.ContainsKey($tag)) { return $null }
        $costs = @()
        foreach ($item in $TagItems[$tag]) {
            if ($Values.ContainsKey($item)) { $costs += [int]$Values[$item] }
        }
        if ($costs.Count -eq 0) { return $null }
        return [int](($costs | Measure-Object -Minimum).Minimum) * [int]$Ingredient.Count
    }
    return $null
}

for ($pass = 1; $pass -le 8; $pass++) {
    foreach ($result in $recipesByResult.Keys) {
        $best = $null
        $bestStep = 0
        foreach ($recipe in @($recipesByResult[$result])) {
            $sum = 0
            $unknown = 0
            $maxStep = 0
            foreach ($ingredient in @($recipe.Ingredients)) {
                $iv = Ingredient-Value $ingredient $value $tagItems
                if ($null -eq $iv) { $unknown++ ; continue }
                $sum += [int]$iv
                if ($ingredient.Kind -eq "item" -and $steps.ContainsKey($ingredient.Value)) {
                    $maxStep = [Math]::Max($maxStep, [int]$steps[$ingredient.Value])
                }
            }
            if ($unknown -gt 0 -or $sum -le 0) { continue }
            $unitCost = [Math]::Ceiling($sum / [Math]::Max(1, [int]$recipe.Count))
            if ($null -eq $best -or $unitCost -lt $best) {
                $best = [int]$unitCost
                $bestStep = $maxStep + 1
            }
        }
        if ($null -ne $best) {
            if (-not $value.ContainsKey($result) -or $best -lt [int]$value[$result] -or $source[$result] -eq "heuristic") {
                $value[$result] = [int]$best
                $source[$result] = "recipe"
                $steps[$result] = $bestStep
            }
        }
    }
}

$rows = foreach ($itemId in ($itemsSeen | Sort-Object)) {
    $current = if ($prices.Contains($itemId)) { [int]$prices[$itemId] } else { 0 }
    $category = Category-Of $itemId
    $candidate = Is-SellCandidate $itemId ($current -gt 0)
    $knownCost = if ($value.ContainsKey($itemId)) { [int]$value[$itemId] } else { $null }
    $stepCount = if ($steps.ContainsKey($itemId)) { [int]$steps[$itemId] } else { 0 }
    $suggested = $null
    if ($candidate -and $knownCost -ne $null) {
    if ($category -in @("seed", "crop", "fish", "animal")) {
            $suggested = [Math]::Min((Category-Cap $category $itemId), [Math]::Max(1, [int]$knownCost))
        } else {
            $processText = if ($recipesByResult.ContainsKey($itemId)) { ((@($recipesByResult[$itemId]) | ForEach-Object { $_.Process } | Sort-Object -Unique) -join ",") } else { "" }
            $suggested = Process-Price $itemId $category ([int]$knownCost) $stepCount $processText
        }
    } elseif ($candidate) {
        $suggested = Heuristic-BasePrice $itemId
        if ($suggested -ne $null) { $suggested = [Math]::Min((Category-Cap $category $itemId), [int]$suggested) }
    }
    $recipes = if ($recipesByResult.ContainsKey($itemId)) { @($recipesByResult[$itemId]) } else { @() }
    $ingredientCount = 0
    $processes = @()
    foreach ($recipe in $recipes) {
        $ingredientCount = [Math]::Max($ingredientCount, @($recipe.Ingredients).Count)
        $processes += $recipe.Process
    }
    $flags = @()
    if ($current -gt 0 -and $suggested -ne $null) {
        if ($current -gt ([int]$suggested * 2) -and ($current - [int]$suggested) -ge 100) { $flags += "over 2x suggested" }
        if ($current -gt (Category-Cap $category $itemId)) { $flags += "over category cap" }
        if ($current -gt 800 -and -not (Is-RareLike $itemId)) { $flags += "high non-rare shipping price" }
    }
    if ($current -gt 0 -and -not $candidate) { $flags += "priced but blocked/weak signal" }
    if ($current -le 0 -and $candidate) { $flags += "missing candidate" }
    if ($candidate -and $knownCost -eq $null) { $flags += "unknown cost/manual review" }
    if ($category -eq "manual" -and $candidate) { $flags += "manual category" }
    [pscustomobject]@{
        Item = $itemId
        Name = if ($names.ContainsKey($itemId)) { $names[$itemId] } else { Item-Path $itemId }
        Namespace = Namespace-Of $itemId
        Category = $category
        Current = $current
        Suggested = if ($suggested -ne $null) { [int]$suggested } else { $null }
        KnownCost = $knownCost
        CostSource = if ($source.ContainsKey($itemId)) { $source[$itemId] } else { "" }
        Steps = $stepCount
        IngredientCount = $ingredientCount
        RecipeCount = [int]($recipeCount[$itemId] ?? 0)
        Processes = (($processes | Sort-Object -Unique) -join ",")
        Candidate = $candidate
        Flags = ($flags -join ", ")
    }
}

$candidateRows = @($rows | Where-Object { $_.Candidate -and $_.Category -ne "blocked" })
$suspicious = @($rows | Where-Object { $_.Current -gt 0 -and $_.Flags })
$missing = @($candidateRows | Where-Object { $_.Current -le 0 } | Sort-Object Namespace,Category,Item)
$expensive = @($rows | Where-Object { $_.Current -gt 0 } | Sort-Object Current -Descending | Select-Object -First 120)
$vinery = @($rows | Where-Object { $_.Namespace -eq "vinery" -and ($_.Candidate -or $_.Current -gt 0) } | Sort-Object Category,Item)
$farming = @($candidateRows | Where-Object { $_.Category -in @("seed", "crop", "animal", "fish") } | Sort-Object Category,Namespace,Item)

function Add-Table([System.Text.StringBuilder]$Builder, [string]$Title, $Rows, [int]$Limit = 250) {
    [void]$Builder.AppendLine("## $Title")
    [void]$Builder.AppendLine()
    $listed = @($Rows | Select-Object -First $Limit)
    if ($listed.Count -eq 0) {
        [void]$Builder.AppendLine("None.")
        [void]$Builder.AppendLine()
        return
    }
    [void]$Builder.AppendLine("| Item | Name | Cat | Current | Suggested | Cost | Source | Steps | Recipes | Process | Flags |")
    [void]$Builder.AppendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
    foreach ($row in $listed) {
        $current = if ($row.Current -gt 0) { "$($row.Current)" } else { "-" }
        $suggested = if ($row.Suggested -ne $null) { "$($row.Suggested)" } else { "?" }
        $cost = if ($row.KnownCost -ne $null) { "$($row.KnownCost)" } else { "?" }
        [void]$Builder.AppendLine("| ``$($row.Item)`` | $(Escape-Cell $row.Name) | $($row.Category) | $current | $suggested | $cost | $($row.CostSource) | $($row.Steps) | $($row.RecipeCount) | $($row.Processes) | $(Escape-Cell $row.Flags) |")
    }
    if (@($Rows).Count -gt $listed.Count) {
        [void]$Builder.AppendLine()
        [void]$Builder.AppendLine("_Showing $($listed.Count) of $(@($Rows).Count). See CSV for full data._")
    }
    [void]$Builder.AppendLine()
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
$rows | Export-Csv -LiteralPath $CsvOutputPath -NoTypeInformation -Encoding UTF8

$builder = [System.Text.StringBuilder]::new()
[void]$builder.AppendLine("# Shipping Bin Offline Audit")
[void]$builder.AppendLine()
[void]$builder.AppendLine('Generated from `runs/client/mods` jar item/tag/recipe data and current `shipping_bin/prices.toml`.')
[void]$builder.AppendLine()
[void]$builder.AppendLine("## Summary")
[void]$builder.AppendLine()
[void]$builder.AppendLine("- Mod jars scanned: $modJarCount")
[void]$builder.AppendLine("- Recipe/process outputs parsed: $recipeSeen")
[void]$builder.AppendLine("- Current priced item entries: $($prices.Count)")
[void]$builder.AppendLine("- Sell candidates discovered: $($candidateRows.Count)")
[void]$builder.AppendLine("- Suspicious priced entries: $($suspicious.Count)")
[void]$builder.AppendLine("- Missing candidates: $($missing.Count)")
[void]$builder.AppendLine('- Full CSV: `docs/generated/shipping-bin-full-audit.csv`')
[void]$builder.AppendLine('- Suggested TOML draft: `docs/generated/shipping-bin-price-suggestions.toml`')
[void]$builder.AppendLine()
[void]$builder.AppendLine("## Pricing Rule Used")
[void]$builder.AppendLine()
[void]$builder.AppendLine("- Conservative shipping baseline, not best-money source.")
[void]$builder.AppendLine("- Recipe cost comes from known ingredient value, tags use cheapest known tag member, tool-like inputs count as zero.")
[void]$builder.AppendLine("- Processed items use small profit multipliers and category caps; NPC commissions should pay more.")
[void]$builder.AppendLine("- Vinery recipes include juice amount, extra ingredients, and required wine bottle when present.")
[void]$builder.AppendLine()
Add-Table $builder "Suspicious Priced Entries" ($suspicious | Sort-Object @{ Expression = { $_.Current - ($_.Suggested ?? 0) }; Descending = $true }, Item) 260
Add-Table $builder "Vinery Wine And Farming Audit" $vinery 220
Add-Table $builder "Farming / Fish / Animal Candidates" $farming 220
Add-Table $builder "Most Expensive Current Sellables" $expensive 120
Add-Table $builder "Missing Sellable Candidates" $missing 300

Set-Content -LiteralPath $OutputPath -Value ($builder.ToString().TrimEnd() + "`r`n") -Encoding UTF8

$toml = [System.Text.StringBuilder]::new()
[void]$toml.AppendLine("# CKDM Shipping Bin Suggested Prices")
[void]$toml.AppendLine("# Review-only draft. Do not copy blindly.")
[void]$toml.AppendLine("# Generated by scripts/audit-shipping-bin.ps1")
[void]$toml.AppendLine("entries = [")
foreach ($row in ($candidateRows | Where-Object { $_.Suggested -ne $null } | Sort-Object Namespace,Category,Item)) {
    [void]$toml.AppendLine("  { item = `"$($row.Item)`", price_amount = $($row.Suggested) }, # $($row.Name) [$($row.Category); current=$($row.Current)]")
}
[void]$toml.AppendLine("]")
Set-Content -LiteralPath $SuggestionsPath -Value ($toml.ToString().TrimEnd() + "`r`n") -Encoding UTF8

Write-Host "Wrote $OutputPath"
Write-Host "Wrote $CsvOutputPath"
Write-Host "Wrote $SuggestionsPath"
