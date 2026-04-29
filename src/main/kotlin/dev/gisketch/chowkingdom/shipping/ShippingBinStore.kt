package dev.gisketch.chowkingdom.shipping

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.StringTagVisitor
import net.minecraft.nbt.TagParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object ShippingBinStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val bins: MutableMap<String, MutableList<StoredStack>> = linkedMapOf()
    private val pendingRewards: MutableMap<String, StoredPendingReward> = linkedMapOf()
    private var lastPayoutDay = Long.MIN_VALUE
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("shipping_bin").resolve("bins.json")
        }

    fun load() {
        file.parent.createDirectories()
        bins.clear()
        lastPayoutDay = Long.MIN_VALUE
        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredShippingBins::class.java) }
                data?.players?.forEach { (playerId, stacks) -> bins[playerId] = stacks.toMutableList() }
                data?.pendingRewards?.forEach { (playerId, reward) ->
                    pendingRewards[playerId] = StoredPendingReward(reward.itemCount.coerceAtLeast(0), reward.amount.coerceAtLeast(0L))
                }
                lastPayoutDay = data?.lastPayoutDay ?: Long.MIN_VALUE
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load shipping bin store {}", file, exception)
            }
        }
        loaded = true
    }

    fun container(playerId: UUID): SimpleContainer {
        if (!loaded) load()
        return object : SimpleContainer(SLOT_COUNT) {
            private var hydrating = true

            init {
                bins[playerId.toString()].orEmpty().forEach { stack ->
                    if (stack.slot in 0 until SLOT_COUNT) setItem(stack.slot, stack.toItemStack())
                }
                hydrating = false
            }

            override fun setChanged() {
                super.setChanged()
                if (hydrating) return
                saveContainer(playerId, this)
            }
        }
    }

    fun tryMarkPayoutDay(day: Long): Boolean {
        if (!loaded) load()
        if (lastPayoutDay >= day) return false
        lastPayoutDay = day
        save()
        return true
    }

    fun playerIds(): List<UUID> {
        if (!loaded) load()
        return bins.keys.mapNotNull { playerId -> runCatching { UUID.fromString(playerId) }.getOrNull() }
    }

    fun addPendingReward(playerId: UUID, payout: ShippingBinPayout) {
        if (!loaded) load()
        if (!payout.hasReward()) return
        val key = playerId.toString()
        val current = pendingRewards[key] ?: StoredPendingReward()
        pendingRewards[key] = StoredPendingReward(current.itemCount + payout.itemCount, current.amount + payout.amount)
        save()
    }

    fun consumePendingReward(player: ServerPlayer): ShippingBinPayout {
        if (!loaded) load()
        val reward = pendingRewards.remove(player.stringUUID) ?: return ShippingBinPayout()
        if (reward.amount > 0L) save()
        return ShippingBinPayout(reward.itemCount, reward.amount)
    }

    fun payout(player: ServerPlayer): ShippingBinPayout {
        val total = payout(player.uuid)
        if (total.hasReward()) ChowcoinNetwork.syncTo(player)
        return total
    }

    fun payout(playerId: UUID): ShippingBinPayout {
        if (!loaded) load()
        val key = playerId.toString()
        val stacks = bins[key].orEmpty()
        var total = 0L
        var itemCount = 0
        val remaining = mutableListOf<StoredStack>()
        stacks.forEach { stored ->
            val stack = stored.toItemStack()
            val price = ShippingBinConfig.priceFor(stack)
            if (price > 0L) {
                itemCount += stack.count
                total += price * stack.count.toLong()
            } else {
                remaining += stored
            }
        }
        if (total <= 0L) return ShippingBinPayout()
        bins[key] = remaining.toMutableList()
        ChowcoinStore.add(playerId, total)
        save()
        return ShippingBinPayout(itemCount, total)
    }

    private fun saveContainer(playerId: UUID, container: SimpleContainer) {
        bins[playerId.toString()] = (0 until SLOT_COUNT).map { slot -> StoredStack.from(slot, container.getItem(slot)) }.toMutableList()
        save()
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "shipping_bins", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredShippingBins(lastPayoutDay, bins, pendingRewards), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private const val SLOT_COUNT = 54
}

class StoredShippingBins(
    var lastPayoutDay: Long = Long.MIN_VALUE,
    var players: MutableMap<String, MutableList<StoredStack>> = linkedMapOf(),
    var pendingRewards: MutableMap<String, StoredPendingReward> = linkedMapOf(),
)

data class ShippingBinPayout(val itemCount: Int = 0, val amount: Long = 0L) {
    fun hasReward(): Boolean = itemCount > 0 && amount > 0L
}

class StoredPendingReward(
    var itemCount: Int = 0,
    var amount: Long = 0L,
)

class StoredStack(
    var slot: Int = 0,
    var item: String = "minecraft:air",
    var count: Int = 0,
    var data: String? = null,
) {
    fun toItemStack(): ItemStack {
        data?.let { raw ->
            val registryAccess = ServerLifecycleHooks.getCurrentServer()?.registryAccess()
            val stack = registryAccess?.let { access -> runCatching { ItemStack.parseOptional(access, TagParser.parseTag(raw)) }.getOrNull() }
            if (stack != null && !stack.isEmpty) return stack
        }
        val itemValue = runCatching { ResourceLocation.parse(item) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
            ?: Items.AIR
        return if (itemValue == Items.AIR || count <= 0) ItemStack.EMPTY else ItemStack(itemValue, count.coerceIn(1, itemValue.defaultMaxStackSize))
    }

    companion object {
        fun from(slot: Int, stack: ItemStack): StoredStack {
            if (stack.isEmpty) return StoredStack(slot)
            val registryAccess = ServerLifecycleHooks.getCurrentServer()?.registryAccess()
            val data = registryAccess?.let { access -> runCatching { StringTagVisitor().visit(stack.saveOptional(access)) }.getOrNull() }
            return StoredStack(slot, BuiltInRegistries.ITEM.getKey(stack.item).toString(), stack.count, data)
        }
    }
}