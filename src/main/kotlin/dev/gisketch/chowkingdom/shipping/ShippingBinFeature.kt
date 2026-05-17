package dev.gisketch.chowkingdom.shipping

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.battlepass.BattlepassMissionEventBank
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.ChowClock
import dev.gisketch.chowkingdom.ChowClockConfig
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordRelay
import dev.gisketch.chowkingdom.profiles.NicknameStore
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ShippingBinFeature {
    private val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(Registries.BLOCK, ChowKingdomMod.MOD_ID)
    private val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ChowKingdomMod.MOD_ID)
    private var lastAutoPayoutMinute = Long.MIN_VALUE

    val SHIPPING_BIN: DeferredHolder<Block, ShippingBinBlock> = BLOCKS.register("shipping_bin", Supplier { ShippingBinBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL)) })

    val SHIPPING_BIN_ITEM: DeferredHolder<Item, BlockItem> = ITEMS.register("shipping_bin", Supplier { BlockItem(SHIPPING_BIN.get(), Item.Properties()) })

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        ShippingBinNetwork.register(modBus)
        ShippingBinConfig.load()
        ShippingBinStore.load()
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        ShippingBinConfig.load()
        ShippingBinStore.load()
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val level = event.server.overworld()
        val clock = ChowClock.now(level, ChowClockConfig.current())
        val payoutHour = ShippingBinConfig.payoutHour()
        val payoutMinute = ShippingBinConfig.payoutMinute()
        val payoutMinuteOfDay = payoutHour * MINUTES_PER_HOUR + payoutMinute
        val currentMinuteOfDay = clock.hour * MINUTES_PER_HOUR + clock.minute
        if (currentMinuteOfDay < payoutMinuteOfDay) return
        val day = clock.day
        val minuteKey = day * MINUTES_PER_DAY + currentMinuteOfDay
        if (minuteKey == lastAutoPayoutMinute) return
        lastAutoPayoutMinute = minuteKey
        if (!ShippingBinStore.hasDueSellableItems(day)) return

        val sales = mutableListOf<ShippingBinSaleResult>()
        ShippingBinStore.playerIds().forEach { playerId ->
            val payout = ShippingBinStore.payoutDue(playerId, day)
            if (payout.hasReward()) {
                val player = event.server.playerList.getPlayer(playerId)
                val playerName = player?.let(NicknameStore::displayName) ?: playerId.toString().take(8)
                sales += ShippingBinSaleResult(playerName, payout)
                if (player != null) {
                    ChowcoinNetwork.syncTo(player)
                    notifyReward(player, payout)
                    recordShippingMissions(player, payout)
                    event.server.playerList.broadcastSystemMessage(Component.literal("$playerName shipped ${payout.itemCount} items for ${payout.amount} chowcoins."), false)
                } else {
                    ShippingBinStore.addPendingReward(playerId, payout)
                }
            }
        }
        announceTopSeller(event.server, sales)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        ShippingBinNetwork.syncTo(player)
        val payout = ShippingBinStore.consumePendingReward(player)
        if (payout.hasReward()) {
            ChowcoinNetwork.syncTo(player)
            notifyReward(player, payout)
            recordShippingMissions(player, payout)
        }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(shippingBinRoot("shippingbin"))
        event.dispatcher.register(Commands.literal("chowkingdom").then(shippingBinRoot("shippingbin")))
        event.dispatcher.register(Commands.literal("ck").then(shippingBinRoot("shippingbin")))
    }

    private fun shippingBinRoot(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .then(Commands.literal("sell").requires { source -> source.hasPermission(2) }.executes(::sellNow))
        .then(Commands.literal("audit").requires { source -> source.hasPermission(2) }.executes(::audit))

    private fun sellNow(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val payout = ShippingBinStore.payout(player)
        context.source.sendSuccess({ Component.literal("Sold ${payout.itemCount} shipping bin items for ${payout.amount} chowcoins.") }, true)
        if (payout.hasReward()) {
            notifyReward(player, payout)
            recordShippingMissions(player, payout)
            announceTopSeller(player.server, listOf(ShippingBinSaleResult(NicknameStore.displayName(player), payout)))
        }
        return payout.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun audit(context: CommandContext<CommandSourceStack>): Int {
        val output = ShippingBinAudit.writeReport(context.source.server)
        context.source.sendSuccess({ Component.literal("Wrote shipping bin audit: ${output.toAbsolutePath()}") }, true)
        return 1
    }

    private fun notifyReward(player: ServerPlayer, payout: ShippingBinPayout) {
        ShippingBinNetwork.notifySale(player, payout)
    }

    private fun recordShippingMissions(player: ServerPlayer, payout: ShippingBinPayout) {
        var changed = false
        if (payout.qualityFoodItemCount > 0) {
            changed = BattlepassMissionEventBank.record(player, QUALITY_FOOD_SOLD_EVENT, payout.qualityFoodItemCount) || changed
        }
        if (payout.ironQualityFoodItemCount > 0) {
            changed = BattlepassMissionEventBank.record(player, IRON_QUALITY_FOOD_SOLD_EVENT, payout.ironQualityFoodItemCount) || changed
        }
        if (payout.goldQualityFoodItemCount > 0) {
            changed = BattlepassMissionEventBank.record(player, GOLD_QUALITY_FOOD_SOLD_EVENT, payout.goldQualityFoodItemCount) || changed
        }
        if (payout.diamondQualityFoodItemCount > 0) {
            changed = BattlepassMissionEventBank.record(player, DIAMOND_QUALITY_FOOD_SOLD_EVENT, payout.diamondQualityFoodItemCount) || changed
        }
        if (payout.amount > 0L) {
            changed = BattlepassMissionEventBank.record(player, VALUE_SOLD_EVENT, payout.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) || changed
        }
        if (payout.qualityFoodAmount > 0L) {
            changed = BattlepassMissionEventBank.record(player, QUALITY_FOOD_VALUE_SOLD_EVENT, payout.qualityFoodAmount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) || changed
        }
        if (changed) BattlepassNetwork.syncAllPlayers()
    }

    private fun announceTopSeller(server: net.minecraft.server.MinecraftServer, sales: List<ShippingBinSaleResult>) {
        val top = sales.maxWithOrNull(compareBy<ShippingBinSaleResult> { it.payout.amount }.thenBy { it.payout.itemCount }) ?: return
        val content = "${top.playerName} sold ${top.payout.itemCount} items for ${top.payout.amount} chowcoins"
        SnackbarNetwork.sendToAllKnown(server, SnackbarNotification.item(SnackbarIcons.SHIPPING_BIN, "TOP SHIPPING SELLER", content, SnackbarType.SUCCESS, SnackbarSounds.SALE))
        DiscordRelay.shippingTopSeller(server, top.playerName, top.payout.itemCount, top.payout.amount)
    }

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val QUALITY_FOOD_SOLD_EVENT = "gisketchs_chowkingdom_mod:shipping_bin_quality_food_sold"
    private const val IRON_QUALITY_FOOD_SOLD_EVENT = "gisketchs_chowkingdom_mod:shipping_bin_iron_quality_food_sold"
    private const val GOLD_QUALITY_FOOD_SOLD_EVENT = "gisketchs_chowkingdom_mod:shipping_bin_gold_quality_food_sold"
    private const val DIAMOND_QUALITY_FOOD_SOLD_EVENT = "gisketchs_chowkingdom_mod:shipping_bin_diamond_quality_food_sold"
    private const val QUALITY_FOOD_VALUE_SOLD_EVENT = "gisketchs_chowkingdom_mod:shipping_bin_quality_food_value_sold"
    private const val VALUE_SOLD_EVENT = "gisketchs_chowkingdom_mod:shipping_bin_value_sold"
}

private data class ShippingBinSaleResult(val playerName: String, val payout: ShippingBinPayout)
