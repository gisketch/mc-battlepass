package dev.gisketch.chowkingdom.recipes

import com.google.gson.annotations.SerializedName
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.common.loot.LootModifier
import net.neoforged.neoforge.event.ItemAttributeModifierEvent
import net.neoforged.neoforge.event.OnDatapackSyncEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object RecipeDisablerFeature {
    private val LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ChowKingdomMod.MOD_ID)
    val LOOT_TABLE_DESTROYER_CODEC = LOOT_MODIFIER_SERIALIZERS.register("loot_table_destroyer", Supplier { LootTableDestroyerModifier.CODEC })

    private var disabledRecipeIds: Set<ResourceLocation> = emptySet()
    private var cosmeticItemIds: Set<ResourceLocation> = emptySet()
    private var lootDestroyerPatterns: List<LootItemPattern> = emptyList()
    private val warnedNonWearables = mutableSetOf<ResourceLocation>()
    private var warnedNoRecipeMatches = false

    fun register(modBus: IEventBus) {
        LOOT_MODIFIER_SERIALIZERS.register(modBus)
        reloadConfig()
        NeoForge.EVENT_BUS.register(this)
    }

    fun isCosmeticized(stack: ItemStack): Boolean {
        if (stack.isEmpty || cosmeticItemIds.isEmpty()) return false
        return BuiltInRegistries.ITEM.getKey(stack.item) in cosmeticItemIds
    }

    fun shouldDestroyLoot(stack: ItemStack): Boolean {
        if (stack.isEmpty || lootDestroyerPatterns.isEmpty()) return false
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        return lootDestroyerPatterns.any { pattern -> pattern.matches(itemId) }
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        reloadConfig()
        applyDisabledRecipes(event.server, warnIfNoMatch = true)
    }

    @SubscribeEvent
    fun onDatapackSync(event: OnDatapackSyncEvent) {
        reloadConfig()
        applyDisabledRecipes(event.playerList.server, warnIfNoMatch = false)
    }

    @SubscribeEvent
    fun onItemAttributeModifiers(event: ItemAttributeModifierEvent) {
        if (cosmeticItemIds.isEmpty()) return
        val itemId = BuiltInRegistries.ITEM.getKey(event.itemStack.item)
        if (itemId !in cosmeticItemIds) return
        if (!hasWearableModifiers(event)) {
            if (warnedNonWearables.add(itemId)) ChowKingdomMod.LOGGER.warn("RecipeDisabler cosmeticize entry {} is not an armor/wearable attribute item; leaving attributes unchanged.", itemId)
            return
        }
        event.clearModifiers()
    }

    private fun reloadConfig() {
        RecipeDisablerConfig.load()
        disabledRecipeIds = RecipeDisablerConfig.disabledRecipeIds()
        cosmeticItemIds = RecipeDisablerConfig.cosmeticItemIds()
        lootDestroyerPatterns = (RecipeDisablerConfig.lootDestroyerPatterns() + cosmeticItemIds.map(ResourceLocation::toString))
            .map(::LootItemPattern)
        warnedNonWearables.clear()
    }

    private fun applyDisabledRecipes(server: MinecraftServer, warnIfNoMatch: Boolean) {
        if (disabledRecipeIds.isEmpty()) return
        val manager = server.recipeManager
        val recipes = manager.recipes
        val filtered = recipes.filterNot { recipe -> recipe.id() in disabledRecipeIds }
        val removed = recipes.size - filtered.size
        if (removed <= 0) {
            if (warnIfNoMatch && !warnedNoRecipeMatches) {
                warnedNoRecipeMatches = true
                ChowKingdomMod.LOGGER.warn("RecipeDisabler configured {} recipes, but none matched loaded recipes.", disabledRecipeIds.size)
            }
            return
        }
        warnedNoRecipeMatches = false
        manager.replaceRecipes(filtered)
        ChowKingdomMod.LOGGER.info("RecipeDisabler removed {} recipes: {}", removed, disabledRecipeIds.joinToString(", "))
    }

    private fun hasWearableModifiers(event: ItemAttributeModifierEvent): Boolean = event.defaultModifiers.modifiers().any { entry -> entry.slot() in WEARABLE_SLOT_GROUPS }

    private val WEARABLE_SLOT_GROUPS = setOf(EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET, EquipmentSlotGroup.ARMOR, EquipmentSlotGroup.BODY)
}

object RecipeDisablerConfig {
    private var config = RecipeDisablerDefinition()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("recipe_disabler.toml")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) TomlConfigIO.write(file, RecipeDisablerDefinition())
        config = try {
            TomlConfigIO.read(file, RecipeDisablerDefinition::class.java, ::RecipeDisablerDefinition)
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load RecipeDisabler config {}", file, exception)
            RecipeDisablerDefinition()
        }
    }

    fun disabledRecipeIds(): Set<ResourceLocation> = config.disabledRecipes.asSequence()
        .toResourceLocationSet("recipe id")

    fun cosmeticItemIds(): Set<ResourceLocation> = config.cosmeticize.asSequence()
        .toResourceLocationSet("cosmetic item id")

    fun lootDestroyerPatterns(): List<String> = config.lootTableDestroyer.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

    private fun Sequence<String>.toResourceLocationSet(label: String): Set<ResourceLocation> = this
        .map(String::trim)
        .filter(String::isNotBlank)
        .mapNotNull { value ->
            runCatching { ResourceLocation.parse(value) }
                .onFailure { ChowKingdomMod.LOGGER.warn("RecipeDisabler ignored invalid {} {}", label, value) }
                .getOrNull()
        }
        .toSet()
}

class RecipeDisablerDefinition(
    @SerializedName("disabled_recipes") var disabledRecipes: MutableList<String> = mutableListOf(
        "minecraft:paper",
    ),
    var cosmeticize: MutableList<String> = mutableListOf(),
    @SerializedName("loot_table_destroyer") var lootTableDestroyer: MutableList<String> = mutableListOf(),
)

class LootTableDestroyerModifier(private val lootConditions: Array<LootItemCondition>) : LootModifier(lootConditions) {
    override fun doApply(generatedLoot: ObjectArrayList<ItemStack>, context: LootContext): ObjectArrayList<ItemStack> {
        generatedLoot.removeIf(RecipeDisablerFeature::shouldDestroyLoot)
        return generatedLoot
    }

    override fun codec(): MapCodec<out IGlobalLootModifier> = RecipeDisablerFeature.LOOT_TABLE_DESTROYER_CODEC.get()

    companion object {
        val CODEC: MapCodec<LootTableDestroyerModifier> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                IGlobalLootModifier.LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(LootTableDestroyerModifier::lootConditions),
            ).apply(instance, ::LootTableDestroyerModifier)
        }
    }
}

private class LootItemPattern(private val raw: String) {
    fun matches(itemId: ResourceLocation): Boolean {
        val value = raw.trim()
        if (value.isBlank()) return false
        return if (value.contains(':')) globMatches(value, itemId.toString()) else globMatches(value, itemId.path)
    }

    private fun globMatches(pattern: String, value: String): Boolean {
        val regex = pattern.split('*').joinToString(".*") { part -> Regex.escape(part) }
        return Regex("^$regex$", RegexOption.IGNORE_CASE).matches(value)
    }
}
