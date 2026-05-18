package dev.gisketch.chowkingdom.shipping

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowClock
import dev.gisketch.chowkingdom.ChowClockConfig
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionScope
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionService
import dev.gisketch.chowkingdom.battlepass.BattlepassPassRegistry
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import dev.gisketch.chowkingdom.integrations.QualityFoodSupport
import dev.gisketch.chowkingdom.relicroulette.RelicRouletteFeature
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
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
    private val weeklyItemCounts: MutableMap<String, MutableMap<String, Int>> = linkedMapOf()
    private var lastPayoutDay = Long.MIN_VALUE
    private var quotaPeriodKey = ""
    private var totalItemsSold = 0L
    private var totalChowcoinsSold = 0L
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("shipping_bin").resolve("bins.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        bins.clear()
        weeklyItemCounts.clear()
        lastPayoutDay = Long.MIN_VALUE
        quotaPeriodKey = currentQuotaPeriodKey()
        totalItemsSold = 0L
        totalChowcoinsSold = 0L
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, StoredShippingBins::class.java, ::StoredShippingBins)
                data.players.forEach { (playerId, stacks) -> bins[playerId] = stacks.toMutableList() }
                data.weeklyItemCounts.forEach { (playerId, items) -> weeklyItemCounts[playerId] = items.toMutableMap() }
                data.pendingRewards.forEach { (playerId, reward) ->
                    pendingRewards[playerId] = StoredPendingReward(
                        reward.itemCount.coerceAtLeast(0),
                        reward.amount.coerceAtLeast(0L),
                        reward.qualityFoodItemCount.coerceAtLeast(0),
                        reward.qualityFoodAmount.coerceAtLeast(0L),
                        reward.ironQualityFoodItemCount.coerceAtLeast(0),
                        reward.goldQualityFoodItemCount.coerceAtLeast(0),
                        reward.diamondQualityFoodItemCount.coerceAtLeast(0),
                    )
                }
                lastPayoutDay = data.lastPayoutDay
                quotaPeriodKey = data.quotaPeriodKey.ifBlank { currentQuotaPeriodKey() }
                totalItemsSold = data.totalItemsSold.coerceAtLeast(0L)
                totalChowcoinsSold = data.totalChowcoinsSold.coerceAtLeast(0L)
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load shipping bin store {}", file, exception)
            }
        }
        refreshQuotaPeriod()
        loaded = true
    }

    fun container(playerId: UUID): SimpleContainer {
        if (!loaded) load()
        return object : SimpleContainer(SLOT_COUNT) {
            private var hydrating = true
            private var sanitizing = false

            init {
                bins[playerId.toString()].orEmpty().forEach { stack ->
                    if (stack.slot in 0 until SLOT_COUNT) setItem(stack.slot, stack.toItemStack())
                }
                hydrating = false
            }

            override fun setChanged() {
                super.setChanged()
                if (hydrating || sanitizing) return
                sanitizing = true
                RelicRouletteFeature.removeTransferBlockedFromContainer(playerId, this)
                sanitizeContainer(playerId, this)
                sanitizing = false
                saveContainer(playerId, this)
            }
        }
    }

    fun access(playerId: UUID): ShippingBinAccess = ShippingBinRules.accessForXp(
        BattlepassXpStore.getXp(playerId, COZY_PASS_ID),
        BattlepassXpStore.getXp(playerId, COMBAT_PASS_ID),
    )

    fun quotaSnapshot(playerId: UUID): Map<String, Int> {
        if (!loaded) load()
        refreshQuotaPeriod()
        return weeklyItemCounts[playerId.toString()].orEmpty().toMap()
    }

    fun weeklyQuotaUsed(playerId: UUID, itemKey: String): Int {
        if (!loaded) load()
        refreshQuotaPeriod()
        return weeklyItemCounts[playerId.toString()]?.get(itemKey) ?: 0
    }

    fun currentWeeklyQuotaPeriod(): String {
        if (!loaded) load()
        refreshQuotaPeriod()
        return quotaPeriodKey
    }

    fun totalItemsSold(): Long {
        if (!loaded) load()
        return totalItemsSold
    }

    fun totalChowcoinsSold(): Long {
        if (!loaded) load()
        return totalChowcoinsSold
    }

    fun debugSetTotalChowcoinsSold(amount: Long): Long {
        if (!loaded) load()
        totalChowcoinsSold = amount.coerceAtLeast(0L)
        save()
        return totalChowcoinsSold
    }

    fun hasDueSellableItems(day: Long): Boolean {
        if (!loaded) load()
        refreshQuotaPeriod()
        return bins.values.any { stacks ->
            stacks.any { stored ->
                val stack = stored.toItemStack()
                eligibleForAutoPayout(stored, day) && !stack.isEmpty && !RelicRouletteFeature.isTransferBlocked(stack) && ShippingBinConfig.priceFor(stack) > 0L
            }
        }
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
        pendingRewards[key] = StoredPendingReward(
            current.itemCount + payout.itemCount,
            current.amount + payout.amount,
            current.qualityFoodItemCount + payout.qualityFoodItemCount,
            current.qualityFoodAmount + payout.qualityFoodAmount,
            current.ironQualityFoodItemCount + payout.ironQualityFoodItemCount,
            current.goldQualityFoodItemCount + payout.goldQualityFoodItemCount,
            current.diamondQualityFoodItemCount + payout.diamondQualityFoodItemCount,
        )
        save()
    }

    fun consumePendingReward(player: ServerPlayer): ShippingBinPayout {
        if (!loaded) load()
        val reward = pendingRewards.remove(player.stringUUID) ?: return ShippingBinPayout()
        if (reward.amount > 0L) save()
        return ShippingBinPayout(
            reward.itemCount,
            reward.amount,
            reward.qualityFoodItemCount,
            reward.qualityFoodAmount,
            reward.ironQualityFoodItemCount,
            reward.goldQualityFoodItemCount,
            reward.diamondQualityFoodItemCount,
        )
    }

    fun payout(player: ServerPlayer): ShippingBinPayout {
        val total = payout(player.uuid)
        if (total.hasReward()) ChowcoinNetwork.syncTo(player)
        return total
    }

    fun payout(playerId: UUID): ShippingBinPayout {
        return payout(playerId) { true }
    }

    fun payoutDue(playerId: UUID, day: Long): ShippingBinPayout {
        return payout(playerId) { stored -> eligibleForAutoPayout(stored, day) }
    }

    private fun eligibleForAutoPayout(stored: StoredStack, day: Long): Boolean = stored.eligibleDay <= day || stored.eligibleDay == Long.MIN_VALUE || stored.eligibleDay > day + 1

    private fun payout(playerId: UUID, shouldSell: (StoredStack) -> Boolean): ShippingBinPayout {
        if (!loaded) load()
        refreshQuotaPeriod()
        val key = playerId.toString()
        val stacks = bins[key].orEmpty()
        var total = 0L
        var itemCount = 0
        var qualityFoodTotal = 0L
        var qualityFoodItemCount = 0
        var ironQualityFoodItemCount = 0
        var goldQualityFoodItemCount = 0
        var diamondQualityFoodItemCount = 0
        val remaining = mutableListOf<StoredStack>()
        val quotaDeltas: MutableMap<String, Int> = linkedMapOf()
        stacks.forEach { stored ->
            val stack = stored.toItemStack()
            if (!shouldSell(stored)) {
                remaining += stored
                return@forEach
            }
            if (RelicRouletteFeature.isTransferBlocked(stack)) {
                remaining += stored
                return@forEach
            }
            val price = ShippingBinConfig.priceFor(stack)
            if (price > 0L) {
                val itemKey = ShippingBinRules.itemKey(stack)
                val stackTotal = quotaAdjustedValue(playerId, itemKey, price, stack.count, quotaDeltas.getOrDefault(itemKey, 0))
                quotaDeltas[itemKey] = quotaDeltas.getOrDefault(itemKey, 0) + stack.count
                itemCount += stack.count
                total += stackTotal
                when (QualityFoodSupport.qualityLevel(stack)) {
                    1 -> {
                        qualityFoodItemCount += stack.count
                        qualityFoodTotal += stackTotal
                        ironQualityFoodItemCount += stack.count
                    }
                    2 -> {
                        qualityFoodItemCount += stack.count
                        qualityFoodTotal += stackTotal
                        goldQualityFoodItemCount += stack.count
                    }
                    3 -> {
                        qualityFoodItemCount += stack.count
                        qualityFoodTotal += stackTotal
                        diamondQualityFoodItemCount += stack.count
                    }
                    else -> Unit
                }
            } else {
                remaining += stored
            }
        }
        if (total <= 0L) return ShippingBinPayout()
        quotaDeltas.forEach { (itemKey, count) -> recordQuota(playerId, itemKey, count) }
        bins[key] = remaining.toMutableList()
        totalItemsSold += itemCount.toLong()
        totalChowcoinsSold += total
        ChowcoinStore.add(playerId, total)
        save()
        return ShippingBinPayout(itemCount, total, qualityFoodItemCount, qualityFoodTotal, ironQualityFoodItemCount, goldQualityFoodItemCount, diamondQualityFoodItemCount)
    }

    private fun saveContainer(playerId: UUID, container: SimpleContainer) {
        val key = playerId.toString()
        val currentDay = currentShippingDay(playerId)
        val previousBySlot = bins[key].orEmpty().associateBy { stored -> stored.slot }
        bins[playerId.toString()] = (0 until SLOT_COUNT)
            .map { slot -> container.getItem(slot) }
            .mapIndexedNotNull { slot, stack ->
                if (RelicRouletteFeature.isTransferBlocked(stack)) null else StoredStack.from(slot, stack, previousBySlot[slot], currentDay)
            }
            .toMutableList()
        save()
    }

    private fun currentShippingDay(playerId: UUID): Long {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return Long.MIN_VALUE
        val level = server.playerList.getPlayer(playerId)?.level() ?: server.overworld()
        return ChowClock.now(level, ChowClockConfig.current()).day
    }

    private fun sanitizeContainer(playerId: UUID, container: SimpleContainer) {
        val player = ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(playerId)
        val access = access(playerId)
        val seenItems = linkedSetOf<String>()
        var blockedReason: String? = null
        for (slot in 0 until SLOT_COUNT) {
            val stack = container.getItem(slot)
            if (stack.isEmpty) continue
            val itemKey = ShippingBinRules.itemKey(stack)
            when {
                slot >= access.unlockedSlots -> {
                    val removed = stack.copy()
                    container.setItem(slot, ItemStack.EMPTY)
                    returnToPlayer(player, removed)
                    val unlockLevel = ShippingBinRules.unlockLevelForSlot(slot)
                    blockedReason = if (unlockLevel == null) "Shipping bin maxes at 27 sell slots." else "Slot unlocks at level $unlockLevel."
                }
                itemKey in seenItems -> {
                    val removed = stack.copy()
                    container.setItem(slot, ItemStack.EMPTY)
                    returnToPlayer(player, removed)
                    blockedReason = "One slot per item. Quality tiers count as the same item."
                }
                else -> {
                    seenItems += itemKey
                    if (stack.count > access.maxStackSize) {
                        val extra = stack.copy()
                        extra.count = stack.count - access.maxStackSize
                        stack.count = access.maxStackSize
                        returnToPlayer(player, extra)
                        blockedReason = "Max ${access.maxStackSize} per slot at shipping level ${access.level}."
                    }
                }
            }
        }
        if (player != null && blockedReason != null) notifyBlocked(player, blockedReason)
    }

    private fun returnToPlayer(player: ServerPlayer?, stack: ItemStack) {
        if (stack.isEmpty) return
        if (player == null) return
        val returned = stack.copy()
        val server = player.server
        val playerId = player.uuid
        server.execute {
            val currentPlayer = server.playerList.getPlayer(playerId) ?: player
            if (!currentPlayer.inventory.add(returned)) currentPlayer.drop(returned, false)
        }
    }

    private fun notifyBlocked(player: ServerPlayer, reason: String) {
        SnackbarNetwork.send(player, SnackbarNotification.item(SnackbarIcons.ERROR, "SHIPPING BIN BLOCKED", reason, SnackbarType.ERROR, SnackbarSounds.ERROR))
    }

    private fun quotaAdjustedValue(playerId: UUID, itemKey: String, price: Long, count: Int, pendingCount: Int): Long {
        val used = weeklyQuotaUsed(playerId, itemKey) + pendingCount.coerceAtLeast(0)
        val normalCount = (ShippingBinRules.WEEKLY_ITEM_QUOTA - used).coerceIn(0, count)
        val reducedCount = (count - normalCount).coerceAtLeast(0)
        return price * normalCount.toLong() + (price * reducedCount.toLong()) / 10L
    }

    private fun recordQuota(playerId: UUID, itemKey: String, count: Int) {
        val playerCounts = weeklyItemCounts.getOrPut(playerId.toString()) { linkedMapOf() }
        playerCounts[itemKey] = playerCounts.getOrDefault(itemKey, 0) + count
    }

    private fun refreshQuotaPeriod() {
        val current = currentQuotaPeriodKey()
        if (quotaPeriodKey == current) return
        quotaPeriodKey = current
        weeklyItemCounts.clear()
        save()
    }

    private fun currentQuotaPeriodKey(): String {
        val weeklyEvents = BattlepassPassRegistry.get(COZY_PASS_ID)?.weeklyEvents ?: BattlepassPassRegistry.get(COMBAT_PASS_ID)?.weeklyEvents
        return weeklyEvents?.let { definition -> BattlepassMissionService.periodKey(BattlepassMissionScope.WEEKLY, definition) } ?: "weekly:unknown"
    }

    private fun save() {
        TomlConfigIO.write(file, StoredShippingBins(lastPayoutDay, quotaPeriodKey, bins, pendingRewards, weeklyItemCounts, totalItemsSold, totalChowcoinsSold))
    }

    private const val SLOT_COUNT = ShippingBinRules.SLOT_COUNT
    private const val COZY_PASS_ID = "cozy"
    private const val COMBAT_PASS_ID = "combat"
}

class StoredShippingBins(
    var lastPayoutDay: Long = Long.MIN_VALUE,
    var quotaPeriodKey: String = "",
    var players: MutableMap<String, MutableList<StoredStack>> = linkedMapOf(),
    var pendingRewards: MutableMap<String, StoredPendingReward> = linkedMapOf(),
    var weeklyItemCounts: MutableMap<String, MutableMap<String, Int>> = linkedMapOf(),
    var totalItemsSold: Long = 0L,
    var totalChowcoinsSold: Long = 0L,
)

data class ShippingBinPayout(
    val itemCount: Int = 0,
    val amount: Long = 0L,
    val qualityFoodItemCount: Int = 0,
    val qualityFoodAmount: Long = 0L,
    val ironQualityFoodItemCount: Int = 0,
    val goldQualityFoodItemCount: Int = 0,
    val diamondQualityFoodItemCount: Int = 0,
) {
    fun hasReward(): Boolean = itemCount > 0 && amount > 0L
}

class StoredPendingReward(
    var itemCount: Int = 0,
    var amount: Long = 0L,
    var qualityFoodItemCount: Int = 0,
    var qualityFoodAmount: Long = 0L,
    var ironQualityFoodItemCount: Int = 0,
    var goldQualityFoodItemCount: Int = 0,
    var diamondQualityFoodItemCount: Int = 0,
)

class StoredStack(
    var slot: Int = 0,
    var item: String = "minecraft:air",
    var count: Int = 0,
    var data: String? = null,
    var depositedDay: Long = Long.MIN_VALUE,
    var eligibleDay: Long = Long.MIN_VALUE,
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
        fun from(slot: Int, stack: ItemStack, previous: StoredStack? = null, currentDay: Long = Long.MIN_VALUE): StoredStack {
            if (stack.isEmpty) return StoredStack(slot)
            val registryAccess = ServerLifecycleHooks.getCurrentServer()?.registryAccess()
            val data = registryAccess?.let { access -> runCatching { StringTagVisitor().visit(stack.saveOptional(access)) }.getOrNull() }
            val itemKey = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            val previousStack = previous?.toItemStack()
            val preserveDeposit = previous != null && previous.item == itemKey && previousStack != null && !previousStack.isEmpty && stack.count <= previousStack.count
            val depositedDay = if (preserveDeposit) previous.depositedDay else currentDay
            val eligibleDay = if (preserveDeposit) previous.eligibleDay else currentDay + 1
            return StoredStack(slot, itemKey, stack.count, data, depositedDay, eligibleDay)
        }
    }
}
