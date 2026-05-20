package dev.gisketch.chowkingdom.shipping

import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.math.roundToLong

object ShippingBinConfig {
    private var config = ShippingBinPriceConfig()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("shipping_bin").resolve("prices.toml")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            TomlConfigIO.read(file, ShippingBinPriceConfig::class.java, ::ShippingBinPriceConfig)
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load shipping bin config {}", file, exception)
            ShippingBinPriceConfig()
        }
        warnMissingEntries()
    }

    fun payoutHour(): Int = config.payoutHour.coerceIn(0, 23)

    fun payoutMinute(): Int = config.payoutMinute.coerceIn(0, 59)

    fun priceFor(stack: ItemStack): Long {
        val basePrice = basePriceFor(stack)
        if (basePrice <= 0L) return 0L
        return (basePrice * qualityFoodMultiplier(stack)).roundToLong().coerceAtLeast(0L)
    }

    fun pricedItemSnapshot(): Map<String, Long> = BuiltInRegistries.ITEM.asSequence()
        .map { item -> ItemStack(item) }
        .filterNot(ItemStack::isEmpty)
        .mapNotNull { stack ->
            val price = priceFor(stack)
            if (price > 0L) BuiltInRegistries.ITEM.getKey(stack.item).toString() to price else null
        }
        .toMap(linkedMapOf())

    fun sellableItemIds(): List<String> = pricedItemSnapshot().keys.sorted()

    private fun basePriceFor(stack: ItemStack): Long {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        config.entries.firstOrNull { entry -> entry.item == itemId }?.let { entry -> return entry.priceAmount.coerceAtLeast(0L) }
        config.entries.firstOrNull { entry -> entry.tag?.let { tag -> itemTag(tag)?.let { key -> stack.`is`(key) } } == true }?.let { entry -> return entry.priceAmount.coerceAtLeast(0L) }
        return 0L
    }

    private fun qualityFoodMultiplier(stack: ItemStack): Double {
        val qualityFood = config.qualityFood
        if (!qualityFood.enabled) return 1.0
        return when (qualityFoodLevel(stack)) {
            1 -> qualityFood.ironQuality
            2 -> qualityFood.goldQuality
            3 -> qualityFood.diamondQuality
            else -> 1.0
        }.coerceAtLeast(0.0)
    }

    private fun qualityFoodLevel(stack: ItemStack): Int {
        val componentType = qualityFoodComponentType() ?: return 0
        val component = stack.get(componentType) ?: return 0
        return runCatching { component.javaClass.getMethod("level").invoke(component) as? Int ?: 0 }.getOrDefault(0).coerceIn(0, 3)
    }

    @Suppress("UNCHECKED_CAST")
    private fun qualityFoodComponentType(): DataComponentType<Any>? = BuiltInRegistries.DATA_COMPONENT_TYPE
        .getOptional(ResourceLocation.fromNamespaceAndPath("quality_food", "quality"))
        .orElse(null) as? DataComponentType<Any>

    private fun itemTag(raw: String): TagKey<Item>? {
        val id = raw.removePrefix("#")
        return runCatching { TagKey.create(Registries.ITEM, ResourceLocation.parse(id)) }.getOrNull()
    }

    private fun warnMissingEntries() {
        config.entries.forEach { entry ->
            entry.item?.takeIf(String::isNotBlank)?.let { itemId ->
                val id = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
                if (id == null) {
                    ChowKingdomMod.LOGGER.warn("Shipping bin price entry has invalid item id {}; keeping config entry but it will not match items.", itemId)
                } else if (BuiltInRegistries.ITEM.getOptional(id).isEmpty) {
                    ChowKingdomMod.LOGGER.warn("Shipping bin price entry {} is not present in the current modlist; keeping config entry.", itemId)
                }
            }
            entry.tag?.takeIf(String::isNotBlank)?.let { tagId ->
                if (itemTag(tagId) == null) ChowKingdomMod.LOGGER.warn("Shipping bin price entry has invalid item tag {}; keeping config entry but it will not match items.", tagId)
            }
        }
    }

    private fun writeDefault() {
        TomlConfigIO.write(file, defaultConfig())
    }

    private fun defaultConfig(): ShippingBinPriceConfig = ShippingBinPriceConfig(
        payoutHour = 5,
        payoutMinute = 0,
        entries = mutableListOf(
            ShippingBinPriceEntry(item = "minecraft:wheat", priceAmount = 8),
            ShippingBinPriceEntry(item = "minecraft:carrot", priceAmount = 10),
            ShippingBinPriceEntry(tag = "minecraft:crops", priceAmount = 8),
        ),
        qualityFood = ShippingBinQualityFoodConfig(),
    )
}

class ShippingBinPriceConfig(
    @SerializedName("payout_hour") var payoutHour: Int = 5,
    @SerializedName("payout_minute") var payoutMinute: Int = 0,
    var entries: MutableList<ShippingBinPriceEntry> = mutableListOf(),
    @SerializedName("quality_food") var qualityFood: ShippingBinQualityFoodConfig = ShippingBinQualityFoodConfig(),
)

class ShippingBinQualityFoodConfig(
    var enabled: Boolean = true,
    @SerializedName("iron_quality") var ironQuality: Double = 1.25,
    @SerializedName("gold_quality") var goldQuality: Double = 1.6,
    @SerializedName("diamond_quality") var diamondQuality: Double = 2.25,
)

class ShippingBinPriceEntry(
    var item: String? = null,
    var tag: String? = null,
    @SerializedName("price_amount") var priceAmount: Long = 0L,
)
