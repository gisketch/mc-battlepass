param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ModsDir = (Join-Path $RepoRoot "runs/client/mods"),
    [string]$PricesPath = (Join-Path $RepoRoot "runs/client/config/gisketchs_chowkingdom_mod/shipping_bin/prices.toml"),
    [string]$OutputPath = (Join-Path $RepoRoot "docs/generated/shipping-bin-offline-audit.md")
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$foodMods = @("minecraft", "farmersdelight", "expandeddelight", "oceansdelight", "ubesdelight", "beachparty", "brewery", "vinery", "create", "cobblemon")
$cropWords = @("wheat", "carrot", "potato", "beetroot", "cabbage", "tomato", "onion", "rice", "berry", "berries", "corn", "pepper", "asparagus", "cranberr", "grape", "apricorn")
$fishWords = @("cod", "salmon", "fish", "puffer", "mussel", "squid", "guardian", "shrimp", "crab", "lobster", "clam")
$mealWords = @("soup", "stew", "pie", "salad", "sandwich", "burger", "pasta", "roll", "rice", "cake", "cookie", "feast", "platter", "meal", "skewer", "wrap", "ham")
$rareWords = @("guardian", "elder_guardian", "nether", "blaze", "dragon", "chorus", "starf", "lansat", "enigma", "custap", "micle", "rowap", "jaboca")

function Has-Any([string]$Value, [string[]]$Words) {
    foreach ($word in $Words) {
        if ($Value.Contains($word)) { return $true }
    }
    return $false
}

function Is-SeedLike([string]$ItemId) {
    $path = $ItemId.Split(":", 2)[1]
    return $path.EndsWith("_seed") -or $path.EndsWith("_seeds")
}

function Is-Blocked([string]$ItemId) {
    $path = $ItemId.Split(":", 2)[1]
    if ($path.Contains("spawn_egg") -or $path.Contains("creative") -or $path.Contains("knife") -or $path.Contains("sword")) { return $true }
    if ($path.Contains("armor") -or $path.Contains("helmet") -or $path.Contains("chestplate") -or $path.Contains("leggings") -or $path.Contains("boots")) { return $true }
    if ($path.Contains("bucket") -or $path.Contains("crate") -or $path.Contains("bag")) { return $true }
    return $false
}

function Is-Candidate([string]$ItemId) {
    if (Is-Blocked $ItemId) { return $false }
    $parts = $ItemId.Split(":", 2)
    $namespace = $parts[0]
    $path = $parts[1]
    if ($foodMods -contains $namespace) { return (Has-Any $path ($cropWords + $fishWords + $mealWords + @("food", "drink", "juice", "wine", "milk", "tea", "coffee"))) -or (Is-SeedLike $ItemId) }
    return Has-Any $path ($cropWords + $fishWords + $mealWords)
}

function Rare-Like([string]$ItemId) {
    return Has-Any ($ItemId.Split(":", 2)[1]) $rareWords
}

function Suggested-Price([string]$ItemId, [Nullable[int]]$KnownCost) {
    $path = $ItemId.Split(":", 2)[1]
    if ($KnownCost.HasValue -and $KnownCost.Value -gt 0) {
        $raw = [Math]::Ceiling($KnownCost.Value * 1.45)
        if (Rare-Like $ItemId) { return [Math]::Min(1200, [Math]::Max(1, $raw)) }
        if ($path.Contains("feast") -or $path.EndsWith("_block")) { return [Math]::Min(650, [Math]::Max(1, $raw)) }
        if (Has-Any $path $mealWords) { return [Math]::Min(500, [Math]::Max(1, $raw)) }
        if ($path.StartsWith("cooked_") -or $path.Contains("_cooked_") -or $path.Contains("baked_") -or $path.Contains("fried_") -or $path.Contains("grilled_")) { return [Math]::Min(160, [Math]::Max(1, $raw)) }
        if (Has-Any $path $cropWords) { return [Math]::Min(40, [Math]::Max(1, $raw)) }
        return [Math]::Min(300, [Math]::Max(1, $raw))
    }
    if (Is-SeedLike $ItemId) { return 1 }
    if (Rare-Like $ItemId) { return 900 }
    if ($path.Contains("feast") -or $path.EndsWith("_block")) { return 500 }
    if (Has-Any $path $mealWords) { return 180 }
    if ($path.StartsWith("cooked_") -or $path.Contains("_cooked_") -or $path.Contains("baked_") -or $path.Contains("fried_") -or $path.Contains("grilled_")) { return 55 }
    if (Has-Any $path $fishWords) { return 26 }
    if (Has-Any $path $cropWords) { return 10 }
    return 40
}

function Escape-Cell([string]$Value) {
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

$prices = [ordered]@{}
Select-String -Path $PricesPath -Pattern '\{ item = "([^"]+)", price_amount = ([0-9]+)' | ForEach-Object {
    if ($_.Line -match '\{ item = "([^"]+)", price_amount = ([0-9]+)') {
        $prices[$matches[1]] = [int]$matches[2]
    }
}

$names = @{}
$recipeIngredients = @{}
$itemsSeen = [System.Collections.Generic.HashSet[string]]::new()
$recipeCount = @{}

Get-ChildItem -LiteralPath $ModsDir -Filter *.jar | ForEach-Object {
    $zip = [IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match '^assets/([^/]+)/lang/en_us\.json$') {
                $modId = $matches[1]
                $json = $null
                try { $json = Read-ZipText $zip $entry | ConvertFrom-Json } catch { continue }
                foreach ($property in $json.PSObject.Properties) {
                    if ($property.Name -match '^item\.([^\.]+)\.(.+)$') {
                        $itemId = "$($matches[1]):$($matches[2])"
                        $names[$itemId] = [string]$property.Value
                        [void]$itemsSeen.Add($itemId)
                    } elseif ($property.Name -match '^block\.([^\.]+)\.(.+)$') {
                        $itemId = "$($matches[1]):$($matches[2])"
                        $names[$itemId] = [string]$property.Value
                        [void]$itemsSeen.Add($itemId)
                    }
                }
            } elseif ($entry.FullName -match '^data/([^/]+)/recipe/.+\.json$') {
                $modId = $matches[1]
                $json = $null
                try { $json = Read-ZipText $zip $entry | ConvertFrom-Json } catch { continue }
                $result = $null
                $count = 1
                if ($json.result -is [string]) {
                    $result = $json.result
                } elseif ($json.result) {
                    $result = $json.result.item
                    if (-not $result) { $result = $json.result.id }
                    if ($json.result.count) { $count = [int]$json.result.count }
                }
                if (-not $result -or $result -notmatch ':') { continue }
                [void]$itemsSeen.Add($result)
                $recipeCount[$result] = 1 + [int]($recipeCount[$result] ?? 0)
                $ingredients = @()
                $rawIngredients = @()
                if ($json.ingredients) { $rawIngredients += @($json.ingredients) }
                if ($json.ingredient) { $rawIngredients += @($json.ingredient) }
                foreach ($ingredient in $rawIngredients) {
                    if ($ingredient -is [string]) {
                        if ($ingredient -match ':') { $ingredients += $ingredient }
                    } elseif ($ingredient.item) {
                        $ingredients += [string]$ingredient.item
                    } elseif ($ingredient.items) {
                        $candidates = @($ingredient.items | Where-Object { $_ -is [string] -and $_ -match ':' })
                        if ($candidates.Count -gt 0) { $ingredients += $candidates[0] }
                    }
                }
                if ($ingredients.Count -gt 0) {
                    if (-not $recipeIngredients.ContainsKey($result)) { $recipeIngredients[$result] = @() }
                    $recipeIngredients[$result] += ,@($ingredients, $count)
                }
            }
        }
    } finally {
        $zip.Dispose()
    }
}

foreach ($itemId in $prices.Keys) { [void]$itemsSeen.Add($itemId) }

$rows = foreach ($itemId in ($itemsSeen | Sort-Object)) {
    $current = if ($prices.Contains($itemId)) { [int]$prices[$itemId] } else { 0 }
    $knownCost = $null
    $ingredientCount = 0
    if ($recipeIngredients.ContainsKey($itemId)) {
        $costs = @()
        foreach ($recipeInfo in $recipeIngredients[$itemId]) {
            $ingredients = @($recipeInfo[0])
            $outputCount = [int]$recipeInfo[1]
            $ingredientCount = [Math]::Max($ingredientCount, $ingredients.Count)
            $canCost = $true
            $sum = 0
            foreach ($ingredient in $ingredients) {
                if ($prices.Contains($ingredient)) {
                    $sum += [int]$prices[$ingredient]
                } else {
                    $canCost = $false
                    break
                }
            }
            if ($canCost -and $sum -gt 0) { $costs += [Math]::Ceiling($sum / [Math]::Max(1, $outputCount)) }
        }
        if ($costs.Count -gt 0) { $knownCost = [int]($costs | Measure-Object -Minimum).Minimum }
    }
    $suggested = Suggested-Price $itemId $knownCost
    $candidate = Is-Candidate $itemId
    $flags = @()
    if ($current -gt 0) {
        if ($current -gt ($suggested * 2) -and ($current - $suggested) -ge 100) { $flags += "over 2x suggested" }
        if ($current -gt 500 -and $knownCost -ne $null -and $knownCost -le 300) { $flags += "high price, cheap known recipe" }
        if ($current -gt 800 -and -not (Rare-Like $itemId)) { $flags += "high non-rare shipping price" }
        if (-not $candidate) { $flags += "priced but weak sellable signal" }
    } elseif ($candidate) {
        $flags += "missing candidate"
    }
    [pscustomobject]@{
        Item = $itemId
        Name = if ($names.ContainsKey($itemId)) { $names[$itemId] } else { $itemId.Split(":", 2)[1] }
        Current = $current
        Suggested = $suggested
        KnownCost = $knownCost
        IngredientCount = $ingredientCount
        RecipeCount = [int]($recipeCount[$itemId] ?? 0)
        Candidate = $candidate
        Flags = ($flags -join ", ")
    }
}

$suspicious = @($rows | Where-Object { $_.Current -gt 0 -and $_.Flags })
$missing = @($rows | Where-Object { $_.Current -le 0 -and $_.Candidate } | Select-Object -First 300)
$expensive = @($rows | Where-Object { $_.Current -gt 0 } | Sort-Object Current -Descending | Select-Object -First 100)

function Add-Table([System.Text.StringBuilder]$Builder, [string]$Title, $Rows) {
    [void]$Builder.AppendLine("## $Title")
    [void]$Builder.AppendLine()
    if (@($Rows).Count -eq 0) {
        [void]$Builder.AppendLine("None.")
        [void]$Builder.AppendLine()
        return
    }
    [void]$Builder.AppendLine("| Item | Name | Current | Suggested | Known Cost | Ingredients | Recipes | Flags |")
    [void]$Builder.AppendLine("|---|---:|---:|---:|---:|---:|---:|---|")
    foreach ($row in $Rows) {
        $current = if ($row.Current -gt 0) { "$($row.Current)" } else { "-" }
        $cost = if ($row.KnownCost -ne $null) { "$($row.KnownCost)" } else { "?" }
        [void]$Builder.AppendLine("| ``$($row.Item)`` | $(Escape-Cell $row.Name) | $current | $($row.Suggested) | $cost | $($row.IngredientCount) | $($row.RecipeCount) | $(Escape-Cell $row.Flags) |")
    }
    [void]$Builder.AppendLine()
}

$builder = [System.Text.StringBuilder]::new()
[void]$builder.AppendLine("# Shipping Bin Offline Audit")
[void]$builder.AppendLine()
[void]$builder.AppendLine('Generated from `runs/client/mods` jar lang/recipe data and current `shipping_bin/prices.toml`.')
[void]$builder.AppendLine()
[void]$builder.AppendLine("## Summary")
[void]$builder.AppendLine()
[void]$builder.AppendLine("- Current priced item entries: $($prices.Count)")
[void]$builder.AppendLine("- Candidate item ids discovered: $(@($rows | Where-Object Candidate).Count)")
[void]$builder.AppendLine("- Suspicious priced entries: $($suspicious.Count)")
[void]$builder.AppendLine("- Missing candidates shown: $($missing.Count)")
[void]$builder.AppendLine()
Add-Table $builder "Suspicious Priced Entries" ($suspicious | Select-Object -First 250)
Add-Table $builder "Most Expensive Current Sellables" $expensive
Add-Table $builder "Missing Sellable Candidates" $missing

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
Set-Content -LiteralPath $OutputPath -Value ($builder.ToString().TrimEnd() + "`r`n") -Encoding UTF8
Write-Host "Wrote $OutputPath"
