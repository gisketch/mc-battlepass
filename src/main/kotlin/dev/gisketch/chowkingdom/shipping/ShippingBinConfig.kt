package dev.gisketch.chowkingdom.shipping

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.math.roundToLong

object ShippingBinConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var config = ShippingBinPriceConfig()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("shipping_bin").resolve("prices.json")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            file.bufferedReader().use { reader -> gson.fromJson(reader, ShippingBinPriceConfig::class.java) } ?: ShippingBinPriceConfig()
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load shipping bin config {}", file, exception)
            ShippingBinPriceConfig()
        }
    }

    fun payoutHour(): Int = config.payoutHour.coerceIn(0, 23)

    fun payoutMinute(): Int = config.payoutMinute.coerceIn(0, 59)

    fun priceFor(stack: ItemStack): Long {
        val basePrice = basePriceFor(stack)
        if (basePrice <= 0L) return 0L
        return (basePrice * qualityFoodMultiplier(stack)).roundToLong().coerceAtLeast(0L)
    }

    private fun basePriceFor(stack: ItemStack): Long {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        config.entries.firstOrNull { entry -> entry.item == itemId }?.let { entry -> return entry.priceAmount.coerceAtLeast(0L) }
        config.entries.firstOrNull { entry -> entry.tag?.let { tag -> stack.`is`(itemTag(tag)) } == true }?.let { entry -> return entry.priceAmount.coerceAtLeast(0L) }
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

    private fun itemTag(raw: String): TagKey<Item> {
        val id = raw.removePrefix("#")
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id))
    }

    private fun writeDefault() {
        Files.createTempFile(file.parent, "shipping_bin_prices", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(defaultConfig(), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun defaultConfig(): ShippingBinPriceConfig = ShippingBinPriceConfig(
        payoutHour = 5,
        payoutMinute = 0,
        entries = mutableListOf(
            ShippingBinPriceEntry(item = "minecraft:wheat", priceAmount = 100),
            ShippingBinPriceEntry(item = "minecraft:carrot", priceAmount = 75),
            ShippingBinPriceEntry(tag = "minecraft:crops", priceAmount = 50),
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
    @SerializedName("iron_quality") var ironQuality: Double = 1.1,
    @SerializedName("gold_quality") var goldQuality: Double = 1.25,
    @SerializedName("diamond_quality") var diamondQuality: Double = 1.5,
)

class ShippingBinPriceEntry(
    var item: String? = null,
    var tag: String? = null,
    @SerializedName("price_amount") var priceAmount: Long = 0L,
)
