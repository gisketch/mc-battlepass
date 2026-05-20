package dev.gisketch.chowkingdom.shipping

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.roundToLong

object ShippingBinAudit {
    fun writeReport(server: MinecraftServer): Path {
        ShippingBinConfig.load()
        val prices = ShippingBinConfig.pricedItemSnapshot()
        val recipes = RecipeAudit.from(server, prices)
        val rows = BuiltInRegistries.ITEM.asSequence()
            .map { item -> ItemStack(item) }
            .filterNot(ItemStack::isEmpty)
            .map { stack -> row(stack, prices, recipes) }
            .filter { row -> row.currentPrice > 0L || row.isCandidate }
            .sortedWith(compareByDescending<AuditRow> { it.flags.isNotEmpty() }.thenBy { it.itemId })
            .toList()

        val output = Path.of("docs", "generated", "shipping-bin-audit.md")
        Files.createDirectories(output.parent)
        Files.writeString(output, render(rows, prices, recipes))
        ChowKingdomMod.LOGGER.info("Wrote shipping bin audit report to {}", output.toAbsolutePath())
        return output
    }

    private fun row(stack: ItemStack, prices: Map<String, Long>, recipes: Map<String, RecipeAudit>): AuditRow {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val recipe = recipes[itemId]
        val current = prices[itemId] ?: 0L
        val suggested = suggestedPrice(stack, recipe)
        val candidate = isCandidate(stack, recipe)
        val flags = mutableListOf<String>()
        if (current > 0L) {
            if (current > suggested * 2 && current - suggested >= 100) flags += "over 2x suggested"
            if (current > 500L && recipe?.knownCostPerOutput != null && recipe.knownCostPerOutput <= 300L) flags += "high price, cheap known recipe"
            if (current > 800L && !rareOrBossLike(itemId)) flags += "high non-rare shipping price"
            if (current > 0L && !candidate) flags += "priced but weak sellable signal"
        } else if (candidate) {
            flags += "missing candidate"
        }
        return AuditRow(
            itemId = itemId,
            name = stack.hoverName.string,
            currentPrice = current,
            suggestedPrice = suggested,
            knownRecipeCost = recipe?.knownCostPerOutput,
            ingredientCount = recipe?.ingredientCount ?: 0,
            recipeCount = recipe?.recipeCount ?: 0,
            isFood = isFood(stack),
            isCandidate = candidate,
            flags = flags,
        )
    }

    private fun suggestedPrice(stack: ItemStack, recipe: RecipeAudit?): Long {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        recipe?.knownCostPerOutput?.let { cost ->
            if (cost > 0L) return clampByTier(itemId, ceil(cost * 1.45).roundToLong())
        }
        return heuristicPrice(itemId, isFood(stack))
    }

    private fun clampByTier(itemId: String, raw: Long): Long {
        val max = when {
            rareOrBossLike(itemId) -> 1200L
            itemId.contains("feast") || itemId.endsWith("_block") -> 650L
            mealLike(itemId) -> 500L
            cookedLike(itemId) -> 160L
            cropLike(itemId) -> 40L
            else -> 300L
        }
        return raw.coerceIn(1L, max)
    }

    private fun heuristicPrice(itemId: String, food: Boolean): Long = when {
        seedLike(itemId) -> 1L
        rareOrBossLike(itemId) -> 900L
        itemId.contains("feast") || itemId.endsWith("_block") -> 500L
        mealLike(itemId) -> 180L
        cookedLike(itemId) -> 55L
        fishLike(itemId) -> 26L
        cropLike(itemId) -> 10L
        food -> 40L
        else -> 12L
    }

    private fun isCandidate(stack: ItemStack, recipe: RecipeAudit?): Boolean {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        if (blocked(itemId)) return false
        if (isFood(stack)) return true
        if (recipe != null && foodMod(itemId)) return true
        return foodNameLike(itemId)
    }

    private fun isFood(stack: ItemStack): Boolean = stack.get(DataComponents.FOOD) != null

    private fun foodMod(itemId: String): Boolean {
        val namespace = itemId.substringBefore(':')
        return namespace in FOOD_MODS
    }

    private fun foodNameLike(itemId: String): Boolean =
        cropLike(itemId) || fishLike(itemId) || cookedLike(itemId) || mealLike(itemId) || seedLike(itemId)

    private fun blocked(itemId: String): Boolean {
        val path = itemId.substringAfter(':')
        return path.contains("spawn_egg") ||
            path.contains("creative") ||
            path.contains("knife") ||
            path.contains("sword") ||
            path.contains("armor") ||
            path.contains("helmet") ||
            path.contains("chestplate") ||
            path.contains("leggings") ||
            path.contains("boots") ||
            path.contains("bucket") ||
            path.contains("crate") ||
            path.contains("bag") ||
            path.contains("block") && !path.contains("feast") && !foodMod(itemId)
    }

    private fun seedLike(itemId: String): Boolean {
        val path = itemId.substringAfter(':')
        return path.endsWith("_seeds") || path.endsWith("_seed")
    }

    private fun cropLike(itemId: String): Boolean {
        val path = itemId.substringAfter(':')
        return CROP_WORDS.any(path::contains)
    }

    private fun fishLike(itemId: String): Boolean {
        val path = itemId.substringAfter(':')
        return FISH_WORDS.any(path::contains)
    }

    private fun cookedLike(itemId: String): Boolean {
        val path = itemId.substringAfter(':')
        return path.startsWith("cooked_") || path.contains("_cooked_") || path.contains("baked_") || path.contains("fried_") || path.contains("grilled_")
    }

    private fun mealLike(itemId: String): Boolean {
        val path = itemId.substringAfter(':')
        return MEAL_WORDS.any(path::contains)
    }

    private fun rareOrBossLike(itemId: String): Boolean {
        val path = itemId.substringAfter(':')
        return RARE_WORDS.any(path::contains)
    }

    private fun render(rows: List<AuditRow>, prices: Map<String, Long>, recipes: Map<String, RecipeAudit>): String {
        val highRisk = rows.filter { row -> row.currentPrice > 0L && row.flags.isNotEmpty() }
        val missing = rows.filter { row -> row.currentPrice <= 0L && row.isCandidate }.take(250)
        val expensive = rows.filter { row -> row.currentPrice > 0L }.sortedByDescending { it.currentPrice }.take(80)
        return buildString {
            appendLine("# Shipping Bin Audit")
            appendLine()
            appendLine("Generated from loaded server registries. This is an audit report only; it does not change prices.")
            appendLine()
            appendLine("## Summary")
            appendLine()
            appendLine("- Priced sellable registry items: ${prices.size}")
            appendLine("- Recipe outputs with readable costs: ${recipes.values.count { it.knownCostPerOutput != null }}")
            appendLine("- Suspicious priced entries: ${highRisk.size}")
            appendLine("- Missing sellable candidates shown: ${missing.size}")
            appendLine()
            appendTable("Suspicious Priced Entries", highRisk.take(200))
            appendTable("Most Expensive Current Sellables", expensive)
            appendTable("Missing Sellable Candidates", missing)
        }
    }

    private fun StringBuilder.appendTable(title: String, rows: List<AuditRow>) {
        appendLine("## $title")
        appendLine()
        if (rows.isEmpty()) {
            appendLine("None.")
            appendLine()
            return
        }
        appendLine("| Item | Name | Current | Suggested | Known Cost | Ingredients | Flags |")
        appendLine("|---|---:|---:|---:|---:|---:|---|")
        rows.forEach { row ->
            appendLine("| `${row.itemId}` | ${escape(row.name)} | ${money(row.currentPrice)} | ${row.suggestedPrice} | ${row.knownRecipeCost?.toString() ?: "?"} | ${row.ingredientCount} | ${escape(row.flags.joinToString(", "))} |")
        }
        appendLine()
    }

    private fun escape(value: String): String = value.replace("|", "\\|")

    private fun money(value: Long): String = if (value <= 0L) "-" else value.toString()

    private data class AuditRow(
        val itemId: String,
        val name: String,
        val currentPrice: Long,
        val suggestedPrice: Long,
        val knownRecipeCost: Long?,
        val ingredientCount: Int,
        val recipeCount: Int,
        val isFood: Boolean,
        val isCandidate: Boolean,
        val flags: List<String>,
    )

    private data class RecipeAudit(
        val recipeCount: Int,
        val ingredientCount: Int,
        val knownCostPerOutput: Long?,
    ) {
        companion object {
            fun from(server: MinecraftServer, prices: Map<String, Long>): Map<String, RecipeAudit> {
                val audits = linkedMapOf<String, MutableList<Pair<Int, Long?>>>()
                recipes(server).forEach { holder ->
                    val recipe = call(holder, "value") ?: holder
                    val result = resultStack(recipe, server) ?: return@forEach
                    if (result.isEmpty) return@forEach
                    val resultId = BuiltInRegistries.ITEM.getKey(result.item).toString()
                    val ingredients = ingredients(recipe)
                    val knownCost = ingredients.mapNotNull { ingredient -> ingredientCost(ingredient, prices) }.takeIf { costs -> costs.size == ingredients.size }?.sum()
                    val perOutput = knownCost?.let { cost -> ceil(cost.toDouble() / result.count.coerceAtLeast(1).toDouble()).roundToLong().coerceAtLeast(1L) }
                    audits.getOrPut(resultId) { mutableListOf() } += ingredients.size to perOutput
                }
                return audits.mapValues { (_, values) ->
                    val best = values.mapNotNull { it.second }.minOrNull()
                    RecipeAudit(values.size, values.minOfOrNull { it.first } ?: 0, best)
                }
            }

            private fun recipes(server: MinecraftServer): List<Any> {
                val manager = server.recipeManager
                val method = manager.javaClass.methods.firstOrNull { method -> method.name == "getRecipes" && method.parameterCount == 0 }
                val result = method?.invoke(manager) ?: return emptyList()
                return (result as? Iterable<*>)?.filterNotNull().orEmpty()
            }

            private fun resultStack(recipe: Any, server: MinecraftServer): ItemStack? {
                val method = recipe.javaClass.methods.firstOrNull { method -> method.name == "getResultItem" && method.parameterCount == 1 } ?: return null
                return method.invoke(recipe, server.registryAccess()) as? ItemStack
            }

            private fun ingredients(recipe: Any): List<Any> {
                val method = recipe.javaClass.methods.firstOrNull { method -> method.name == "getIngredients" && method.parameterCount == 0 } ?: return emptyList()
                val result = method.invoke(recipe)
                return (result as? Iterable<*>)?.filterNotNull().orEmpty()
            }

            private fun ingredientCost(ingredient: Any, prices: Map<String, Long>): Long? {
                val method = ingredient.javaClass.methods.firstOrNull { method -> method.name == "getItems" && method.parameterCount == 0 } ?: return null
                val stacks = (method.invoke(ingredient) as? Array<*>)?.filterIsInstance<ItemStack>().orEmpty()
                if (stacks.isEmpty()) return null
                return stacks.mapNotNull { stack -> prices[BuiltInRegistries.ITEM.getKey(stack.item).toString()] }.minOrNull()
            }

            private fun call(target: Any, name: String): Any? = target.javaClass.methods.firstOrNull { method -> method.name == name && method.parameterCount == 0 }?.invoke(target)
        }
    }

    private val FOOD_MODS = setOf(
        "minecraft",
        "farmersdelight",
        "expandeddelight",
        "oceansdelight",
        "ubesdelight",
        "beachparty",
        "brewery",
        "vinery",
        "create",
        "cobblemon",
    )
    private val CROP_WORDS = listOf("wheat", "carrot", "potato", "beetroot", "cabbage", "tomato", "onion", "rice", "berry", "berries", "corn", "pepper", "asparagus", "cranberr", "grape", "apricorn")
    private val FISH_WORDS = listOf("cod", "salmon", "fish", "puffer", "mussel", "squid", "guardian", "shrimp", "crab", "lobster", "clam")
    private val MEAL_WORDS = listOf("soup", "stew", "pie", "salad", "sandwich", "burger", "pasta", "roll", "rice", "cake", "cookie", "feast", "platter", "meal", "skewer", "wrap", "ham")
    private val RARE_WORDS = listOf("guardian", "elder_guardian", "nether", "blaze", "dragon", "chorus", "starf", "lansat", "enigma", "custap", "micle", "rowap", "jaboca")
}
