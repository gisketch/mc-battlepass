package dev.gisketch.chowkingdom.recipes

import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EquipmentSlotGroup
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.ItemAttributeModifierEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object RecipeDisablerFeature {
    private var disabledRecipeIds: Set<ResourceLocation> = emptySet()
    private var cosmeticItemIds: Set<ResourceLocation> = emptySet()
    private val warnedNonWearables = mutableSetOf<ResourceLocation>()

    fun register() {
        reloadConfig()
        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        reloadConfig()
        apply(event.server)
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
        warnedNonWearables.clear()
    }

    private fun apply(server: MinecraftServer) {
        if (disabledRecipeIds.isEmpty()) return
        val manager = server.recipeManager
        val recipes = manager.recipes
        val filtered = recipes.filterNot { recipe -> recipe.id() in disabledRecipeIds }
        val removed = recipes.size - filtered.size
        if (removed <= 0) {
            ChowKingdomMod.LOGGER.warn("RecipeDisabler configured {} recipes, but none matched loaded recipes.", disabledRecipeIds.size)
            return
        }
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
)
