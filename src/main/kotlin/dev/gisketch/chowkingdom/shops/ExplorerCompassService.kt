package dev.gisketch.chowkingdom.shops

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.EventPriority
import net.neoforged.fml.ModList
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object ExplorerCompassService {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var registered = false
    private var state = ExplorerCompassState()
    private var statePath: Path? = null
    private val warnedMissingItems = mutableSetOf<String>()

    fun register() {
        if (registered) return
        registered = true
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickItem)
    }

    fun isExplorerCompassOffer(offer: StoreOffer): Boolean =
        offer.offerType.equals(OFFER_TYPE, ignoreCase = true)

    fun isExplorerOffer(offer: StoreOffer): Boolean =
        ExplorerMapService.isExplorerMapOffer(offer) || isExplorerCompassOffer(offer)

    fun expandOffers(player: ServerPlayer?, offer: StoreOffer): List<StoreOffer> {
        if (!isExplorerCompassOffer(offer)) return listOf(offer)
        if (offer.targets.isNotEmpty()) return listOf(normalizedConcreteOffer(offer))
        val dimension = offer.dimension.trim().ifBlank { offer.dimensions.firstOrNull()?.trim().orEmpty() }
        if (player == null || dimension.isBlank()) return emptyList()
        val targetType = offer.targetType.trim().lowercase(Locale.ROOT)
        val categories = when (targetType) {
            "biome" -> ExplorerTargetCatalog.biomeCategoryIds(player.server)
            "structure" -> ExplorerTargetCatalog.structureCategoryIds(player.server)
            else -> emptyList()
        }.filter { category -> offer.categoryAllowed(category) }
        val bands = offer.bandList().ifEmpty { listOf(NEAR_BAND.id) }
            .filter { band -> band in DISTANCE_BANDS_BY_ID }
        return categories.flatMap { category ->
            bands.map { band ->
                StoreOffer(
                    id = generatedOfferId(offer.id, dimension, category, band),
                    item = offer.item,
                    offerType = OFFER_TYPE,
                    targetType = targetType,
                    targets = mutableListOf("category:$category"),
                    dimensions = mutableListOf(dimension),
                    dimension = dimension,
                    category = category,
                    distanceBand = band,
                    label = offer.label.trim().ifBlank { "${dimensionLabel(dimension)} ${categoryLabel(category)} ${bandLabel(band)} Compass" },
                    mapScale = offer.mapScale,
                    marker = offer.marker,
                    minDistanceBlocks = DISTANCE_BANDS_BY_ID[band]?.min ?: offer.minDistanceBlocks,
                    maxDistanceBlocks = DISTANCE_BANDS_BY_ID[band]?.max ?: offer.maxDistanceBlocks,
                    uses = 1,
                    priceAmount = offer.priceAmount,
                    stockCount = offer.stockCount,
                    weight = offer.weight,
                )
            }
        }
    }

    fun preview(player: ServerPlayer, offer: StoreOffer): ExplorerCompassPreview? {
        if (!hasAvailableTargets(player.server, offer)) return null
        val stack = createProfileStack(player, "", "", "", offer, preview = true)
        return if (stack.isEmpty) null else ExplorerCompassPreview(stack, offer.stockCount.coerceAtLeast(0))
    }

    fun purchaseStack(player: ServerPlayer, storeId: String, stockKey: String, pool: ShopViewPool, offer: StoreOffer): ItemStack? {
        if (!hasAvailableTargets(player.server, offer)) return null
        val stack = createProfileStack(player, storeId, stockKey, pool.id, offer, preview = false)
        return stack.takeUnless(ItemStack::isEmpty)
    }

    fun recordPurchase(player: ServerPlayer, offer: StoreOffer) {
        // V2 claims exact locations only on compass use. Purchase does not mutate discovery state.
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        load(event.server)
    }

    private fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val stack = event.itemStack
        var profile = profileData(stack) ?: return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS

        val player = event.entity as? ServerPlayer ?: return
        if (profile.owner.isNotBlank() && profile.owner != player.uuid.toString()) {
            player.displayClientMessage(Component.literal("This compass belongs to another explorer."), true)
            return
        }
        if (profile.pending) {
            profile = profile.copy(pending = false, pendingStartedTick = 0)
            writeProfileData(stack, profile)
            decorateStack(stack, profile, used = false)
        }
        if (profile.usesRemaining <= 0) {
            player.displayClientMessage(Component.literal("This compass has already spent its search."), true)
            return
        }

        val level = profile.configuredLevel(player.server)
        if (level == null) {
            refundFailedCompass(player, stack, profile, "No ${profile.dimensionLabel()} world is loaded")
            return
        }
        val result = when (profile.targetType) {
            "biome" -> locateAndLockBiome(player, level, stack, profile)
            "structure" -> locateAndLockStructure(level, stack, profile)
            else -> null
        }
        if (result == null) {
            refundFailedCompass(player, stack, profile, "No undiscovered ${profile.categoryLabel()} target found in ${profile.dimensionLabel()} ${profile.bandLabel()}")
            return
        }
        state.foundTargets += result.claimKey
        val used = profile.copy(
            pending = false,
            pendingStartedTick = 0,
            usesRemaining = 0,
            lastTarget = result.targetId,
            foundX = result.x,
            foundZ = result.z,
        )
        writeProfileData(stack, used)
        decorateStack(stack, used, used = true)
        save()
        SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "COMPASS LOCKED", "${profile.dimensionLabel()} ${labelFor(result.targetId, profile.targetType)}", SnackbarType.SUCCESS, SnackbarSounds.SUCCESS))
    }

    private fun locateAndLockBiome(player: ServerPlayer, level: ServerLevel, stack: ItemStack, profile: CompassProfileData): CompassSearchResult? {
        val band = profile.distanceBand()
        val targetIds = ExplorerTargetCatalog.resolveBiomeTargetIds(level, profile.targetList()).map(ResourceLocation::toString).toSet()
        if (targetIds.isEmpty()) return null
        repeat(BIOME_SAMPLE_ATTEMPTS) {
            val sample = randomOriginRingPosition(level, band)
            val pair = level.findClosestBiome3d(
                { holder -> holder.unwrapKey().map { key -> key.location().toString() in targetIds }.orElse(false) },
                sample,
                BIOME_LOCATE_RADIUS,
                BIOME_SAMPLE_HORIZONTAL,
                BIOME_SAMPLE_VERTICAL,
            ) ?: return@repeat
            val pos = surfacePos(level, pair.first)
            val targetKey = pair.second.unwrapKey()
            if (targetKey.isEmpty) return@repeat
            val targetId = targetKey.get().location()
            if (!band.contains(pos.x, pos.z)) return@repeat
            val claimKey = claimKey(profile.targetType, profile.dimension, targetId.toString(), pos.x, pos.z)
            if (claimKey in state.foundTargets) return@repeat
            if (!CompassCompat.lockBiome(player, stack, targetId, pos.x, pos.z, band.max)) return@repeat
            return CompassSearchResult(targetId.toString(), pos.x, pos.z, claimKey)
        }
        return null
    }

    private fun locateAndLockStructure(level: ServerLevel, stack: ItemStack, profile: CompassProfileData): CompassSearchResult? {
        if (!level.server.worldData.worldGenOptions().generateStructures()) return null
        val targetIds = ExplorerTargetCatalog.resolveStructureTargetIds(level, profile.targetList()).map(ResourceLocation::toString).toSet()
        if (targetIds.isEmpty()) return null
        val holders = ExplorerTargetCatalog.structureHolders(level, targetIds.toList())
        if (holders.isEmpty()) return null
        val band = profile.distanceBand()
        repeat(STRUCTURE_SAMPLE_ATTEMPTS) {
            val sample = randomOriginRingPosition(level, band)
            val pair = level.chunkSource.generator.findNearestMapStructure(level, HolderSet.direct(holders), sample, STRUCTURE_LOCATE_RADIUS_CHUNKS, false) ?: return@repeat
            val pos = surfacePos(level, pair.first)
            val targetKey = pair.second.unwrapKey()
            if (targetKey.isEmpty) return@repeat
            val targetId = targetKey.get().location()
            if (targetId.toString() !in targetIds) return@repeat
            if (!band.contains(pos.x, pos.z)) return@repeat
            val claimKey = claimKey(profile.targetType, profile.dimension, targetId.toString(), pos.x, pos.z)
            if (claimKey in state.foundTargets) return@repeat
            if (!CompassCompat.lockStructure(stack, targetId, pos.x, pos.z, band.max)) return@repeat
            return CompassSearchResult(targetId.toString(), pos.x, pos.z, claimKey)
        }
        return null
    }

    private fun createProfileStack(player: ServerPlayer, storeId: String, stockKey: String, poolId: String, offer: StoreOffer, preview: Boolean): ItemStack {
        val targetType = offer.targetType.trim().lowercase(Locale.ROOT)
        val configuredItem = offer.item.trim()
        val itemId = if (configuredItem.isBlank() || configuredItem == "minecraft:air") {
            when (targetType) {
                "biome" -> NATURES_COMPASS_ITEM
                "structure" -> EXPLORERS_COMPASS_ITEM
                else -> "minecraft:air"
            }
        } else configuredItem
        val item = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
            ?: Items.AIR
        if (item == Items.AIR) {
            if (warnedMissingItems.add(itemId)) ChowKingdomMod.LOGGER.warn("Explorer compass store item {} is not present; install the compass mod or fix the configured item id.", itemId)
            return ItemStack.EMPTY
        }
        val band = offer.distanceBand.trim().lowercase(Locale.ROOT).ifBlank { offer.bandList().firstOrNull().orEmpty() }.ifBlank { NEAR_BAND.id }
        val dimension = offer.dimension.trim().ifBlank { offer.dimensions.firstOrNull().orEmpty() }
        val category = offer.category.trim().ifBlank { offer.targets.firstOrNull { it.startsWith("category:") }?.removePrefix("category:").orEmpty() }
        val profile = CompassProfileData(
            profileId = offer.id,
            owner = if (preview) "" else player.uuid.toString(),
            targetType = targetType,
            label = offer.label.trim().ifBlank { "${dimensionLabel(dimension)} ${categoryLabel(category)} ${bandLabel(band)} Compass" },
            targets = offer.targets.joinToString("\n"),
            dimension = dimension,
            category = category,
            band = band,
            minDistanceBlocks = DISTANCE_BANDS_BY_ID[band]?.min ?: offer.minDistanceBlocks,
            maxDistanceBlocks = DISTANCE_BANDS_BY_ID[band]?.max ?: offer.maxDistanceBlocks,
            usesRemaining = offer.uses.coerceAtLeast(1),
            priceAmount = offer.priceAmount.coerceAtLeast(0L),
            storeId = storeId,
            stockKey = stockKey,
            poolId = poolId,
            offerId = offer.id,
        )
        writeProfileData(stack = ItemStack(item), profile = profile).also { stack ->
            decorateStack(stack, profile, used = false)
            return stack
        }
    }

    private fun decorateStack(stack: ItemStack, profile: CompassProfileData, used: Boolean) {
        val stateText = when {
            profile.pending -> "Searching"
            used || profile.usesRemaining <= 0 -> "Locked target"
            else -> "One expedition"
        }
        val lockedLines = if ((used || profile.usesRemaining <= 0) && profile.lastTarget.isNotBlank()) {
            listOf(
                Component.literal("Found: ${labelFor(profile.lastTarget, profile.targetType)}").withStyle(ChatFormatting.GREEN),
                Component.literal("Coordinates: X ${profile.foundX}, Z ${profile.foundZ}").withStyle(ChatFormatting.GREEN),
            )
        } else emptyList()
        stack.set(DataComponents.ITEM_NAME, Component.literal(profile.label).withStyle(ChatFormatting.GOLD))
        stack.set(
            DataComponents.LORE,
            ItemLore(
                listOf(
                    Component.literal("${profile.targetType.replaceFirstChar(Char::titlecase)} expedition").withStyle(ChatFormatting.GRAY),
                    Component.literal(stateText).withStyle(if (used) ChatFormatting.GREEN else ChatFormatting.YELLOW),
                ) + lockedLines + listOf(
                    Component.literal("Category: ${profile.categoryLabel()}").withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal("Dimension: ${profile.dimensionLabel()}").withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal("Range: ${profile.bandLabel()} (${profile.minDistanceBlocks}-${profile.maxDistanceBlocks})").withStyle(ChatFormatting.DARK_GRAY),
                ),
            ),
        )
    }

    private fun hasAvailableTargets(server: MinecraftServer, offer: StoreOffer): Boolean {
        val level = configuredLevel(server, offer.dimension.trim().ifBlank { offer.dimensions.firstOrNull().orEmpty() }) ?: return false
        return when (offer.targetType.trim().lowercase(Locale.ROOT)) {
            "biome" -> ExplorerTargetCatalog.resolveBiomeTargetIds(level, offer.targets).isNotEmpty()
            "structure" -> ExplorerTargetCatalog.resolveStructureTargetIds(level, offer.targets).isNotEmpty()
            else -> false
        }
    }

    private fun refundFailedCompass(player: ServerPlayer, stack: ItemStack, profile: CompassProfileData, reason: String) {
        stack.shrink(1)
        val amount = profile.priceAmount.coerceAtLeast(0L)
        if (amount > 0L) {
            ChowcoinStore.set(player, ChowcoinStore.get(player).saturatingAdd(amount))
            ChowcoinNetwork.syncTo(player)
        }
        if (profile.storeId.isNotBlank() && profile.stockKey.isNotBlank() && profile.poolId.isNotBlank() && profile.offerId.isNotBlank()) {
            StoreShopFeature.restoreStock(profile.storeId, profile.stockKey, profile.poolId, profile.offerId, 1)
        }
        SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "REFUNDED", reason, SnackbarType.ERROR, SnackbarSounds.ERROR))
    }

    private fun profileData(stack: ItemStack): CompassProfileData? {
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (!root.contains(CUSTOM_DATA_TAG)) return null
        val tag = root.getCompound(CUSTOM_DATA_TAG)
        return CompassProfileData(
            profileId = tag.getString("profile_id"),
            owner = tag.getString("owner"),
            targetType = tag.getString("target_type"),
            label = tag.getString("label"),
            targets = tag.getString("targets"),
            dimension = tag.getString("dimension").ifBlank { tag.getString("dimensions").lines().firstOrNull().orEmpty() },
            category = tag.getString("category"),
            band = tag.getString("band"),
            minDistanceBlocks = tag.getInt("min_distance_blocks"),
            maxDistanceBlocks = tag.getInt("max_distance_blocks"),
            usesRemaining = tag.getInt("uses_remaining"),
            pending = tag.getBoolean("pending"),
            pendingStartedTick = tag.getInt("pending_started_tick"),
            lastTarget = tag.getString("last_target"),
            foundX = tag.getInt("found_x"),
            foundZ = tag.getInt("found_z"),
            priceAmount = tag.getLong("price_amount"),
            storeId = tag.getString("store_id"),
            stockKey = tag.getString("stock_key"),
            poolId = tag.getString("pool_id"),
            offerId = tag.getString("offer_id"),
        )
    }

    private fun writeProfileData(stack: ItemStack, profile: CompassProfileData): ItemStack {
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        root.put(CUSTOM_DATA_TAG, CompoundTag().also { tag ->
            tag.putString("profile_id", profile.profileId)
            tag.putString("owner", profile.owner)
            tag.putString("target_type", profile.targetType)
            tag.putString("label", profile.label)
            tag.putString("targets", profile.targets)
            tag.putString("dimension", profile.dimension)
            tag.putString("category", profile.category)
            tag.putString("band", profile.band)
            tag.putInt("min_distance_blocks", profile.minDistanceBlocks)
            tag.putInt("max_distance_blocks", profile.maxDistanceBlocks)
            tag.putInt("uses_remaining", profile.usesRemaining)
            tag.putBoolean("pending", profile.pending)
            tag.putInt("pending_started_tick", profile.pendingStartedTick)
            tag.putString("last_target", profile.lastTarget)
            tag.putInt("found_x", profile.foundX)
            tag.putInt("found_z", profile.foundZ)
            tag.putLong("price_amount", profile.priceAmount)
            tag.putString("store_id", profile.storeId)
            tag.putString("stock_key", profile.stockKey)
            tag.putString("pool_id", profile.poolId)
            tag.putString("offer_id", profile.offerId)
        })
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root)
        return stack
    }

    private fun load(server: MinecraftServer) {
        val path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ChowKingdomMod.MOD_ID).resolve("explorer_compasses").resolve("state.json")
        statePath = path
        path.parent.createDirectories()
        state = if (path.exists()) {
            try {
                path.bufferedReader().use { reader -> gson.fromJson(reader, ExplorerCompassState::class.java) } ?: ExplorerCompassState()
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load explorer compass state {}", path, exception)
                ExplorerCompassState()
            }
        } else ExplorerCompassState()
    }

    private fun save() {
        val path = statePath ?: return
        path.parent.createDirectories()
        val temp = Files.createTempFile(path.parent, "explorer_compasses", ".json.tmp")
        temp.bufferedWriter().use { writer -> gson.toJson(state, writer) }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun randomOriginRingPosition(level: ServerLevel, band: DistanceBand): BlockPos {
        val min2 = band.min.toDouble() * band.min.toDouble()
        val max2 = band.max.toDouble() * band.max.toDouble()
        val radius = sqrt(Random.nextDouble(min2, max2.coerceAtLeast(min2 + 1.0)))
        val angle = Random.nextDouble(0.0, PI * 2.0)
        return BlockPos((cos(angle) * radius).roundToInt(), level.seaLevel, (sin(angle) * radius).roundToInt())
    }

    private fun configuredLevel(server: MinecraftServer, dimensionId: String): ServerLevel? {
        val id = runCatching { ResourceLocation.parse(dimensionId) }.getOrNull() ?: return null
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id))
    }

    private fun CompassProfileData.configuredLevel(server: MinecraftServer): ServerLevel? = configuredLevel(server, dimension)

    private fun surfacePos(level: ServerLevel, pos: BlockPos): BlockPos =
        level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos)

    private fun claimKey(type: String, dimension: String, targetId: String, x: Int, z: Int): String =
        "${type.lowercase(Locale.ROOT)}|${dimension.lowercase(Locale.ROOT)}|${targetId.lowercase(Locale.ROOT)}|$x|$z"

    private fun normalizedConcreteOffer(offer: StoreOffer): StoreOffer {
        if (offer.dimension.isNotBlank() || offer.dimensions.isEmpty()) return offer
        offer.dimension = offer.dimensions.first()
        return offer
    }

    private fun StoreOffer.bandList(): List<String> =
        (distanceBands + listOf(distanceBand)).map { it.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank).distinct()

    private fun StoreOffer.categoryAllowed(category: String): Boolean {
        val normalized = category.lowercase(Locale.ROOT)
        val included = categoryIncludes.map { it.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank)
        val excluded = categoryExcludes.map { it.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank)
        return normalized !in excluded && (included.isEmpty() || normalized in included)
    }

    private fun generatedOfferId(base: String, dimension: String, category: String, band: String): String =
        listOf(base, dimension, category, band).joinToString("_") { part ->
            part.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        }.trim('_').take(96)

    private fun labelFor(value: String, type: String): String {
        val base = value.substringAfter(':').replace('_', ' ').replace('/', ' ')
            .split(' ')
            .filter(String::isNotBlank)
            .joinToString(" ") { word -> word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() } }
            .ifBlank { "Explorer" }
        return if (base.endsWith("Compass", ignoreCase = true)) base else "$base ${type.replaceFirstChar(Char::titlecase)}"
    }

    private fun categoryLabel(category: String): String =
        category.ifBlank { "Any" }.replace('_', ' ').split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
            word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
        }

    private fun dimensionLabel(dimension: String): String =
        dimension.substringAfter(':', dimension).replace('_', ' ').split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
            word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
        }.ifBlank { "Any Dimension" }

    private fun bandLabel(band: String): String = when (band.lowercase(Locale.ROOT)) {
        "near" -> "Near"
        "far" -> "Far"
        "very_far" -> "Very Far"
        else -> categoryLabel(band)
    }

    private fun CompassProfileData.categoryLabel(): String = categoryLabel(category)
    private fun CompassProfileData.dimensionLabel(): String = dimensionLabel(dimension)
    private fun CompassProfileData.bandLabel(): String = bandLabel(band)
    private fun CompassProfileData.targetList(): List<String> = targets.lines().map { it.trim() }.filter(String::isNotBlank)
    private fun CompassProfileData.distanceBand(): DistanceBand = DISTANCE_BANDS_BY_ID[band] ?: DistanceBand(band, minDistanceBlocks, maxDistanceBlocks.coerceAtLeast(1))

    private fun Long.saturatingAdd(other: Long): Long =
        if (other <= 0L) this else if (this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other

    private const val OFFER_TYPE = "explorer_compass"
    private const val CUSTOM_DATA_TAG = "CkdmExplorerCompass"
    private const val NATURES_COMPASS_ITEM = "naturescompass:naturescompass"
    private const val EXPLORERS_COMPASS_ITEM = "explorerscompass:explorerscompass"
    private const val BIOME_SAMPLE_ATTEMPTS = 64
    private const val STRUCTURE_SAMPLE_ATTEMPTS = 48
    private const val BIOME_LOCATE_RADIUS = 512
    private const val BIOME_SAMPLE_HORIZONTAL = 32
    private const val BIOME_SAMPLE_VERTICAL = 64
    private const val STRUCTURE_LOCATE_RADIUS_CHUNKS = 32
    private val NEAR_BAND = DistanceBand("near", 0, 1000)
    private val DISTANCE_BANDS_BY_ID = listOf(
        NEAR_BAND,
        DistanceBand("far", 1000, 2000),
        DistanceBand("very_far", 2000, 12000),
    ).associateBy(DistanceBand::id)
}

data class ExplorerCompassPreview(val stack: ItemStack, val stockCount: Int)

private data class CompassSearchResult(val targetId: String, val x: Int, val z: Int, val claimKey: String)

private data class DistanceBand(val id: String, val min: Int, val max: Int) {
    fun contains(x: Int, z: Int): Boolean {
        val distance = sqrt(x.toDouble() * x.toDouble() + z.toDouble() * z.toDouble())
        return distance >= min && distance <= max
    }
}

private data class CompassProfileData(
    val profileId: String = "",
    val owner: String = "",
    val targetType: String = "",
    val label: String = "",
    val targets: String = "",
    val dimension: String = "",
    val category: String = "",
    val band: String = "near",
    val minDistanceBlocks: Int = 0,
    val maxDistanceBlocks: Int = 12000,
    val usesRemaining: Int = 1,
    val pending: Boolean = false,
    val pendingStartedTick: Int = 0,
    val lastTarget: String = "",
    val foundX: Int = 0,
    val foundZ: Int = 0,
    val priceAmount: Long = 0L,
    val storeId: String = "",
    val stockKey: String = "",
    val poolId: String = "",
    val offerId: String = "",
)

private class ExplorerCompassState(
    @SerializedName("found_targets") var foundTargets: MutableSet<String> = linkedSetOf(),
    @SerializedName("players") var players: MutableMap<String, ExplorerCompassPlayerState> = linkedMapOf(),
)

private class ExplorerCompassPlayerState(
    @SerializedName("visited_biomes") var visitedBiomes: MutableSet<String> = linkedSetOf(),
    @SerializedName("used_targets") var usedTargets: MutableSet<String> = linkedSetOf(),
    @SerializedName("purchased_profiles") var purchasedProfiles: MutableSet<String> = linkedSetOf(),
)

private object CompassCompat {
    fun lockBiome(player: ServerPlayer, stack: ItemStack, target: ResourceLocation, x: Int, z: Int, radius: Int): Boolean {
        if (!ModList.get().isLoaded("naturescompass")) return false
        return runCatching {
            val item = Class.forName("com.chaosthedude.naturescompass.NaturesCompass").getField("naturesCompass").get(null)
            item.javaClass.methods.firstOrNull { method -> method.name == "setBiomeKey" && method.parameterTypes.size == 3 }
                ?.invoke(item, stack, target, player)
            item.javaClass.methods.first { method -> method.name == "succeed" && method.parameterTypes.size == 7 }
                .invoke(item, stack, player, x, z, emptyList<BlockPos>(), radius, false)
            true
        }.onFailure { exception -> ChowKingdomMod.LOGGER.warn("Nature's Compass profile lock failed", exception) }.getOrDefault(false)
    }

    fun lockStructure(stack: ItemStack, target: ResourceLocation, x: Int, z: Int, radius: Int): Boolean {
        if (!ModList.get().isLoaded("explorerscompass")) return false
        return runCatching {
            val item = Class.forName("com.chaosthedude.explorerscompass.ExplorersCompass").getField("explorersCompass").get(null)
            item.javaClass.methods.first { method -> method.name == "succeed" && method.parameterTypes.size == 8 }
                .invoke(item, stack, target, false, x, z, emptyList<BlockPos>(), radius, false)
            true
        }.onFailure { exception -> ChowKingdomMod.LOGGER.warn("Explorer's Compass profile lock failed", exception) }.getOrDefault(false)
    }
}
