package dev.gisketch.chowkingdom.shipping

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowKingdomMod
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

    val SHIPPING_BIN: DeferredHolder<Block, ShippingBinBlock> = BLOCKS.register("shipping_bin", Supplier { ShippingBinBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL)) })

    val SHIPPING_BIN_ITEM: DeferredHolder<Item, BlockItem> = ITEMS.register("shipping_bin", Supplier { BlockItem(SHIPPING_BIN.get(), Item.Properties()) })

    private var tickCounter = 0

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
        tickCounter = (tickCounter + 1) % PAYOUT_CHECK_INTERVAL_TICKS
        if (tickCounter != 0) return

        val level = event.server.overworld()
        val dayTime = level.dayTime
        val day = dayTime / DAY_TICKS
        val timeOfDay = Math.floorMod(dayTime, DAY_TICKS)
        if (timeOfDay < ShippingBinConfig.payoutTick()) return
        if (!ShippingBinStore.tryMarkPayoutDay(day)) return

        ShippingBinStore.playerIds().forEach { playerId ->
            val payout = ShippingBinStore.payout(playerId)
            if (payout.hasReward()) {
                val player = event.server.playerList.getPlayer(playerId)
                if (player != null) {
                    ChowcoinNetwork.syncTo(player)
                    notifyReward(player, payout)
                    event.server.playerList.broadcastSystemMessage(Component.literal("${player.gameProfile.name} shipped ${payout.itemCount} items for ${payout.amount} chowcoins."), false)
                } else {
                    ShippingBinStore.addPendingReward(playerId, payout)
                }
            }
        }
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val payout = ShippingBinStore.consumePendingReward(player)
        if (payout.hasReward()) {
            ChowcoinNetwork.syncTo(player)
            notifyReward(player, payout)
        }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(shippingBinRoot("shippingbin"))
        event.dispatcher.register(Commands.literal("chowkingdom").then(shippingBinRoot("shippingbin")))
        event.dispatcher.register(Commands.literal("ck").then(shippingBinRoot("shippingbin")))
    }

    private fun shippingBinRoot(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .then(Commands.literal("sell").requires { source -> source.hasPermission(2) }.executes(::sellNow))

    private fun sellNow(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val payout = ShippingBinStore.payout(player)
        context.source.sendSuccess({ Component.literal("Sold ${payout.itemCount} shipping bin items for ${payout.amount} chowcoins.") }, true)
        if (payout.hasReward()) notifyReward(player, payout)
        return payout.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun notifyReward(player: ServerPlayer, payout: ShippingBinPayout) {
        ShippingBinNetwork.notifySale(player, payout)
    }

    private const val DAY_TICKS = 24_000L
    private const val PAYOUT_CHECK_INTERVAL_TICKS = 200
}