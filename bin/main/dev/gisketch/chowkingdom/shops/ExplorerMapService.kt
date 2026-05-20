package dev.gisketch.chowkingdom.shops

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.mojang.datafixers.util.Pair
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.saveddata.maps.MapDecorationType
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.math.ceil
import kotlin.math.sqrt

object ExplorerMapService {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var registered = false
    private var state = ExplorerMapState()
    private var statePath: Path? = null

    fun register() {
        if (registered) return
        registered = true
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
    }

    fun isExplorerMapOffer(offer: StoreOffer): Boolean =
        offer.offerType.equals(OFFER_TYPE, ignoreCase = true)

    fun prepare(player: ServerPlayer, stockKey: String, pool: ShopViewPool, offer: StoreOffer, period: String): Boolean {
        if (cachedTarget(player, stockKey, pool, offer, period) != null) return true
        val level = player.level() as? ServerLevel ?: return false
        val target = locateTarget(player, level, offer) ?: return false
        state.offers[offerKey(player.uuid, stockKey, pool, offer, period)] = ExplorerOfferState.from(player.uuid, stockKey, pool.id, offer.id, period, target)
        save()
        return true
    }

    fun preview(player: ServerPlayer, stockKey: String, pool: ShopViewPool, offer: StoreOffer, period: String): ExplorerMapPreview? {
        val target = cachedTarget(player, stockKey, pool, offer, period) ?: return null
        return ExplorerMapPreview(previewStack(offer, target), 1)
    }

    fun purchaseStack(player: ServerPlayer, stockKey: String, pool: ShopViewPool, offer: StoreOffer, period: String): ItemStack? {
        val target = cachedTarget(player, stockKey, pool, offer, period) ?: return null
        return createMapStack(player.level() as? ServerLevel ?: return null, offer, target)
    }

    fun recordPurchase(player: ServerPlayer, stockKey: String, pool: ShopViewPool, offer: StoreOffer, period: String) {
        val target = cachedTarget(player, stockKey, pool, offer, period) ?: return
        playerState(player.uuid).soldTargets.add(target.soldKey)
        state.offers.remove(offerKey(player.uuid, stockKey, pool, offer, period))
        save()
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        load(event.server)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        if (server.tickCount % DISCOVERY_SCAN_INTERVAL_TICKS != 0) return
        server.playerList.players.forEach { player ->
            recordCurrentBiome(player)
            completeNearbySoldTargets(player)
        }
    }

    private fun cachedTarget(player: ServerPlayer, stockKey: String, pool: ShopViewPool, offer: StoreOffer, period: String): ExplorerMapTarget? {
        var changed = state.offers.entries.removeIf { (_, value) ->
            value.player == player.uuid.toString() && value.stockKey == stockKey && value.pool == pool.id && value.offer == offer.id && value.period != period
        }
        val key = offerKey(player.uuid, stockKey, pool, offer, period)
        val cached = state.offers[key]
        if (cached != null && cached.period == period) {
            val target = cached.toTarget()
            if (!isKnown(player.uuid, target)) return target
            state.offers.remove(key)
            changed = true
        }
        if (changed) save()
        return null
    }

    private fun locateTarget(player: ServerPlayer, level: ServerLevel, offer: StoreOffer): ExplorerMapTarget? {
        val origin = player.blockPosition()
        val targetType = offer.targetType.trim().lowercase(Locale.ROOT)
        val maxDistance = offer.maxDistanceBlocks.coerceIn(1, MAX_ALLOWED_DISTANCE_BLOCKS)
        val minDistance = offer.minDistanceBlocks.coerceIn(0, maxDistance)
        return when (targetType) {
            "biome" -> locateBiome(player.uuid, level, origin, offer, minDistance, maxDistance)
            "structure" -> locateStructure(player.uuid, level, origin, offer, minDistance, maxDistance)
            else -> null
        }
    }

    private fun locateBiome(playerId: UUID, level: ServerLevel, origin: BlockPos, offer: StoreOffer, minDistance: Int, maxDistance: Int): ExplorerMapTarget? {
        val targets = cleanTargets(offer.targets)
        val pair = level.findClosestBiome3d(
            { holder -> targets.any { target -> biomeMatches(holder, target) } },
            origin,
            maxDistance,
            BIOME_SAMPLE_HORIZONTAL,
            BIOME_SAMPLE_VERTICAL,
        ) ?: return null
        val pos = surfacePos(level, pair.first)
        val targetId = pair.second.getRegisteredName()
        val target = ExplorerMapTarget("biome", level.dimension().location().toString(), targetId, pos.x, pos.y, pos.z, displayName(offer, targetId))
        val distance = horizontalDistance(origin, pos)
        return target.takeIf { distance in minDistance.toDouble()..maxDistance.toDouble() && !isKnown(playerId, it) }
    }

    private fun locateStructure(playerId: UUID, level: ServerLevel, origin: BlockPos, offer: StoreOffer, minDistance: Int, maxDistance: Int): ExplorerMapTarget? {
        if (!level.server.worldData.worldGenOptions().generateStructures()) return null
        val holders = structureHolders(level, offer.targets)
        if (holders.isEmpty()) return null
        val searchRadiusChunks = ceil(maxDistance / 16.0).toInt().coerceIn(1, MAX_STRUCTURE_SEARCH_RADIUS_CHUNKS)
        val pair: Pair<BlockPos, Holder<Structure>> = level.chunkSource.generator
            .findNearestMapStructure(level, HolderSet.direct(holders), origin, searchRadiusChunks, false)
            ?: return null
        val pos = surfacePos(level, pair.first)
        val targetId = pair.second.getRegisteredName()
        val target = ExplorerMapTarget("structure", level.dimension().location().toString(), targetId, pos.x, pos.y, pos.z, displayName(offer, targetId))
        val distance = horizontalDistance(origin, pos)
        return target.takeIf { distance in minDistance.toDouble()..maxDistance.toDouble() && !isKnown(playerId, it) }
    }

    private fun structureHolders(level: ServerLevel, rawTargets: List<String>): List<Holder<Structure>> {
        val registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        return cleanTargets(rawTargets).flatMap { target ->
            if (target.startsWith("#")) {
                registry.getTag(TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(target.removePrefix("#"))))
                    .map { named -> named.toList() }
                    .orElse(emptyList())
            } else {
                registry.getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(target)))
                    .map { holder -> listOf<Holder<Structure>>(holder) }
                    .orElse(emptyList())
            }
        }.distinctBy { holder -> holder.getRegisteredName() }
    }

    private fun biomeMatches(holder: Holder<Biome>, target: String): Boolean =
        if (target.startsWith("#")) {
            holder.`is`(TagKey.create(Registries.BIOME, ResourceLocation.parse(target.removePrefix("#"))))
        } else {
            holder.unwrapKey().map { key -> key.location().toString() == target }.orElse(false)
        }

    private fun recordCurrentBiome(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        val biomeId = level.getBiome(player.blockPosition()).unwrapKey().map { key -> key.location().toString() }.orElse("")
        if (biomeId.isBlank()) return
        val playerState = playerState(player.uuid)
        val key = "${level.dimension().location()}|$biomeId"
        if (playerState.visitedBiomes.add(key)) save()
    }

    private fun completeNearbySoldTargets(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        val playerState = playerState(player.uuid)
        val pos = player.blockPosition()
        val completed = playerState.soldTargets.filter { raw ->
            val parts = raw.split('|')
            parts.size == 6 &&
                parts[0] == "structure" &&
                parts[1] == level.dimension().location().toString() &&
                horizontalDistance(pos, BlockPos(parts[3].toIntOrNull() ?: 0, pos.y, parts[5].toIntOrNull() ?: 0)) <= COMPLETE_RADIUS_BLOCKS
        }
        if (completed.isNotEmpty() && playerState.completedTargets.addAll(completed)) save()
    }

    private fun previewStack(offer: StoreOffer, target: ExplorerMapTarget): ItemStack {
        val stack = ItemStack(Items.FILLED_MAP)
        decorateStack(stack, offer, target)
        return stack
    }

    private fun createMapStack(level: ServerLevel, offer: StoreOffer, target: ExplorerMapTarget): ItemStack {
        val stack = MapItem.create(level, target.x, target.z, offer.mapScale.coerceIn(0, 4).toByte(), true, true)
        MapItem.renderBiomePreviewMap(level, stack)
        MapItemSavedData.addTargetDecoration(stack, BlockPos(target.x, target.y, target.z), "+", marker(offer.marker))
        decorateStack(stack, offer, target)
        return stack
    }

    private fun decorateStack(stack: ItemStack, offer: StoreOffer, target: ExplorerMapTarget) {
        stack.set(DataComponents.ITEM_NAME, Component.literal(target.label).withStyle(ChatFormatting.GOLD))
        stack.set(
            DataComponents.LORE,
            ItemLore(
                listOf(
                    Component.literal(target.targetId).withStyle(ChatFormatting.GRAY),
                    Component.literal("${target.dimension} ${target.x}, ${target.z}").withStyle(ChatFormatting.DARK_GRAY),
                ),
            ),
        )
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        root.put(EXPLORER_MAP_TAG, CompoundTag().also { tag ->
            tag.putString("type", target.targetType)
            tag.putString("dimension", target.dimension)
            tag.putString("target", target.targetId)
            tag.putInt("x", target.x)
            tag.putInt("y", target.y)
            tag.putInt("z", target.z)
            tag.putString("offer", offer.id)
            tag.putString("mod", ChowKingdomMod.MOD_ID)
        })
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root)
    }

    private fun marker(raw: String): Holder<MapDecorationType> = when (raw.trim().lowercase(Locale.ROOT)) {
        "mansion", "woodland_mansion" -> MapDecorationTypes.WOODLAND_MANSION
        "monument", "ocean_monument" -> MapDecorationTypes.OCEAN_MONUMENT
        "village_desert" -> MapDecorationTypes.DESERT_VILLAGE
        "village_plains" -> MapDecorationTypes.PLAINS_VILLAGE
        "village_savanna" -> MapDecorationTypes.SAVANNA_VILLAGE
        "village_snowy" -> MapDecorationTypes.SNOWY_VILLAGE
        "village_taiga" -> MapDecorationTypes.TAIGA_VILLAGE
        "jungle_temple" -> MapDecorationTypes.JUNGLE_TEMPLE
        "swamp_hut" -> MapDecorationTypes.SWAMP_HUT
        "trial_chambers" -> MapDecorationTypes.TRIAL_CHAMBERS
        "red_x" -> MapDecorationTypes.RED_X
        "point", "target_point" -> MapDecorationTypes.TARGET_POINT
        else -> MapDecorationTypes.TARGET_X
    }

    private fun isKnown(playerId: UUID, target: ExplorerMapTarget): Boolean {
        val playerState = playerState(playerId)
        if (target.soldKey in playerState.soldTargets) return true
        if (target.targetType == "biome" && "${target.dimension}|${target.targetId}" in playerState.visitedBiomes) return true
        return false
    }

    private fun playerState(playerId: UUID): ExplorerPlayerState =
        state.players.getOrPut(playerId.toString()) { ExplorerPlayerState() }

    private fun offerKey(playerId: UUID, stockKey: String, pool: ShopViewPool, offer: StoreOffer, period: String): String =
        listOf(playerId, stockKey, pool.id, offer.id, period).joinToString("|")

    private fun surfacePos(level: ServerLevel, pos: BlockPos): BlockPos =
        level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos)

    private fun horizontalDistance(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dz * dz)
    }

    private fun cleanTargets(values: List<String>): List<String> =
        values.map { value -> value.trim().lowercase(Locale.ROOT) }
            .filter { value -> value.isNotBlank() && runCatching { ResourceLocation.parse(value.removePrefix("#")) }.isSuccess }
            .distinct()

    private fun displayName(offer: StoreOffer, targetId: String): String =
        offer.label.trim().ifBlank {
            targetId.substringAfter(':').replace('_', ' ').split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
                word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
            } + " Map"
        }

    private fun load(server: MinecraftServer) {
        val path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ChowKingdomMod.MOD_ID).resolve("explorer_maps").resolve("state.json")
        statePath = path
        path.parent.createDirectories()
        state = if (path.exists()) {
            try {
                path.bufferedReader().use { reader -> gson.fromJson(reader, ExplorerMapState::class.java) } ?: ExplorerMapState()
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load explorer map state {}", path, exception)
                ExplorerMapState()
            }
        } else ExplorerMapState()
    }

    private fun save() {
        val path = statePath ?: return
        path.parent.createDirectories()
        val temp = Files.createTempFile(path.parent, "explorer_maps", ".json.tmp")
        temp.bufferedWriter().use { writer -> gson.toJson(state, writer) }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
    }

    private const val OFFER_TYPE = "explorer_map"
    private const val EXPLORER_MAP_TAG = "CkdmExplorerMap"
    private const val DISCOVERY_SCAN_INTERVAL_TICKS = 200
    private const val BIOME_SAMPLE_HORIZONTAL = 32
    private const val BIOME_SAMPLE_VERTICAL = 64
    private const val MAX_ALLOWED_DISTANCE_BLOCKS = 12000
    private const val MAX_STRUCTURE_SEARCH_RADIUS_CHUNKS = 750
    private const val COMPLETE_RADIUS_BLOCKS = 96.0
}

data class ExplorerMapPreview(val stack: ItemStack, val stockCount: Int)

private data class ExplorerMapTarget(
    val targetType: String,
    val dimension: String,
    val targetId: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val label: String,
) {
    val soldKey: String
        get() = if (targetType == "biome") "$targetType|$dimension|$targetId" else "$targetType|$dimension|$targetId|$x|$y|$z"
}

private class ExplorerMapState(
    @SerializedName("players") var players: MutableMap<String, ExplorerPlayerState> = linkedMapOf(),
    @SerializedName("offers") var offers: MutableMap<String, ExplorerOfferState> = linkedMapOf(),
)

private class ExplorerPlayerState(
    @SerializedName("visited_biomes") var visitedBiomes: MutableSet<String> = linkedSetOf(),
    @SerializedName("sold_targets") var soldTargets: MutableSet<String> = linkedSetOf(),
    @SerializedName("completed_targets") var completedTargets: MutableSet<String> = linkedSetOf(),
)

private class ExplorerOfferState(
    @SerializedName("player") var player: String = "",
    @SerializedName("stock_key") var stockKey: String = "",
    @SerializedName("pool") var pool: String = "",
    @SerializedName("offer") var offer: String = "",
    @SerializedName("period") var period: String = "",
    @SerializedName("target_type") var targetType: String = "",
    @SerializedName("dimension") var dimension: String = "",
    @SerializedName("target_id") var targetId: String = "",
    @SerializedName("x") var x: Int = 0,
    @SerializedName("y") var y: Int = 0,
    @SerializedName("z") var z: Int = 0,
    @SerializedName("label") var label: String = "",
) {
    fun toTarget(): ExplorerMapTarget = ExplorerMapTarget(targetType, dimension, targetId, x, y, z, label)

    companion object {
        fun from(playerId: UUID, stockKey: String, pool: String, offerId: String, period: String, target: ExplorerMapTarget): ExplorerOfferState =
            ExplorerOfferState(
                player = playerId.toString(),
                stockKey = stockKey,
                pool = pool,
                offer = offerId,
                period = period,
                targetType = target.targetType,
                dimension = target.dimension,
                targetId = target.targetId,
                x = target.x,
                y = target.y,
                z = target.z,
                label = target.label,
            )
    }
}
