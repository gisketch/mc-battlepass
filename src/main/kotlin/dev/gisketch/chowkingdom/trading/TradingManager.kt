package dev.gisketch.chowkingdom.trading

import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChatGlyphs
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import kotlin.math.min

object TradingManager {
    private const val REQUEST_TTL_TICKS = 30 * 20
    private const val MAX_DISTANCE_SQR = 12.0 * 12.0
    private val pendingRequests: MutableMap<Pair<UUID, UUID>, TradeRequest> = linkedMapOf()
    private val sessions: MutableMap<UUID, TradingSession> = linkedMapOf()
    private val sessionsByPlayer: MutableMap<UUID, UUID> = linkedMapOf()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onEntityInteract)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    fun session(id: UUID): TradingSession? = sessions[id]

    fun handleAction(player: ServerPlayer, payload: TradeActionPayload) {
        val session = sessions[payload.sessionId] ?: return
        if (sessionsByPlayer[player.uuid] != session.id) return
        when (payload.action) {
            TradeAction.SET_CHOWCOINS.id -> setChowcoins(player, session, payload.amount)
            TradeAction.READY.id -> setReady(player, session)
            TradeAction.CONFIRM.id -> setConfirmed(player, session)
            TradeAction.CANCEL.id -> cancel(session, "${player.gameProfile.name} cancelled the trade.")
        }
    }

    fun onMenuClosed(player: ServerPlayer, sessionId: UUID) {
        val session = sessions[sessionId] ?: return
        if (!session.completed) cancel(session, "${player.gameProfile.name} closed the trade.")
    }

    fun onOfferChanged(session: TradingSession, playerId: UUID) {
        if (session.suppressOfferChange || session.completed) return
        session.resetConsent(playerId)
        sync(session)
    }

    fun sync(session: TradingSession) {
        val first = player(session.firstId)
        val second = player(session.secondId)
        first?.let { TradingNetwork.syncTo(it, sessionStateFor(session, it.uuid)) }
        if (!session.debug) second?.let { TradingNetwork.syncTo(it, sessionStateFor(session, it.uuid)) }
        session.menus.values.forEach { it.broadcastChanges() }
    }

    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.isCanceled) return
        if (event.hand != InteractionHand.MAIN_HAND) return
        val requester = event.entity as? ServerPlayer ?: return
        val target = event.target as? ServerPlayer ?: return
        if (requester.level().isClientSide) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        handleRightClick(requester, target)
    }

    private fun handleRightClick(requester: ServerPlayer, target: ServerPlayer) {
        expireRequests(requester.server.tickCount)
        if (requester.uuid == target.uuid) return
        val reverseKey = target.uuid to requester.uuid
        val reverse = pendingRequests[reverseKey]
        if (reverse != null) {
            clearRequest(reverse)
            startSession(requester, target, debug = false)
            return
        }
        val ownKey = requester.uuid to target.uuid
        val existing = pendingRequests[ownKey]
        if (existing != null) {
            clearRequest(existing)
            requester.sendSystemMessage(Component.literal("Trade request cancelled.").withStyle(ChatFormatting.YELLOW))
            target.sendSystemMessage(Component.literal("${requester.gameProfile.name} cancelled their trade request.").withStyle(ChatFormatting.GRAY))
            return
        }
        if (isBusy(requester.uuid) || isBusy(target.uuid)) {
            requester.sendSystemMessage(Component.literal("Trade unavailable: one player is already trading or has a pending request.").withStyle(ChatFormatting.RED))
            return
        }
        val request = TradeRequest(requester.uuid, requester.gameProfile.name, target.uuid, target.gameProfile.name, requester.server.tickCount + REQUEST_TTL_TICKS)
        pendingRequests[ownKey] = request
        TradingNetwork.setGlow(requester, target.uuid, true)
        TradingNetwork.setGlow(target, requester.uuid, true)
        requester.sendSystemMessage(Component.literal("Trade request sent to ${target.gameProfile.name}.").withStyle(ChatFormatting.GREEN))
        target.sendSystemMessage(Component.literal("${requester.gameProfile.name} wants to trade. Right-click them to accept, or /ck trade decline.").withStyle(ChatFormatting.AQUA))
    }

    private fun startSession(first: ServerPlayer, second: ServerPlayer, debug: Boolean) {
        val session = TradingSession(
            UUID.randomUUID(),
            first.uuid,
            first.gameProfile.name,
            if (debug) TradingSession.DEBUG_PARTNER_ID else second.uuid,
            if (debug) TradingSession.DEBUG_PARTNER_NAME else second.gameProfile.name,
            debug,
        )
        if (debug) seedDebugOffer(session)
        sessions[session.id] = session
        sessionsByPlayer[first.uuid] = session.id
        if (!debug) sessionsByPlayer[second.uuid] = session.id
        openMenu(first, session)
        if (!debug) openMenu(second, session)
        first.sendSystemMessage(Component.literal("Trade opened with ${session.secondName}.").withStyle(ChatFormatting.GREEN))
        if (!debug) second.sendSystemMessage(Component.literal("Trade opened with ${session.firstName}.").withStyle(ChatFormatting.GREEN))
        sync(session)
    }

    private fun openMenu(player: ServerPlayer, session: TradingSession) {
        val provider = object : MenuProvider {
            override fun getDisplayName(): Component = Component.literal("Trade")

            override fun createMenu(containerId: Int, inventory: Inventory, playerEntity: Player): TradingMenu =
                TradingMenu.server(containerId, inventory, session, player.uuid)
        }
        player.openMenu(provider) { buffer ->
            buffer.writeUUID(session.id)
            buffer.writeUUID(player.uuid)
            buffer.writeUtf(session.name(player.uuid), 32)
            buffer.writeUtf(session.name(session.otherId(player.uuid)), 32)
            buffer.writeBoolean(session.debug)
        }
    }

    private fun seedDebugOffer(session: TradingSession) {
        session.secondOffer.setItem(0, ItemStack(net.minecraft.world.item.Items.DIAMOND, 3))
        session.secondOffer.setItem(1, ItemStack(net.minecraft.world.item.Items.BREAD, 16))
    }

    private fun setChowcoins(player: ServerPlayer, session: TradingSession, requestedAmount: Long) {
        val max = if (session.debug && player.uuid == session.secondId) Long.MAX_VALUE else ChowcoinStore.get(player)
        session.chowcoins[player.uuid] = requestedAmount.coerceIn(0L, max)
        session.resetConsent(player.uuid)
        sync(session)
    }

    private fun setReady(player: ServerPlayer, session: TradingSession) {
        session.ready += player.uuid
        session.confirmed -= player.uuid
        sync(session)
    }

    private fun setConfirmed(player: ServerPlayer, session: TradingSession) {
        session.ready += player.uuid
        session.confirmed += player.uuid
        sync(session)
        if (session.confirmed.contains(session.firstId) && session.confirmed.contains(session.secondId)) commit(session)
    }

    private fun commit(session: TradingSession) {
        val first = player(session.firstId) ?: return cancel(session, "Trade cancelled: ${session.firstName} left.")
        val second = if (session.debug) null else player(session.secondId) ?: return cancel(session, "Trade cancelled: ${session.secondName} left.")
        val firstCoins = session.chowcoins[session.firstId] ?: 0L
        val secondCoins = session.chowcoins[session.secondId] ?: 0L
        if (ChowcoinStore.get(first) < firstCoins) {
            first.sendSystemMessage(Component.literal("Trade blocked: not enough chowcoins.").withStyle(ChatFormatting.RED))
            session.resetConsent(first.uuid)
            sync(session)
            return
        }
        if (second != null && ChowcoinStore.get(second) < secondCoins) {
            second.sendSystemMessage(Component.literal("Trade blocked: not enough chowcoins.").withStyle(ChatFormatting.RED))
            session.resetConsent(second.uuid)
            sync(session)
            return
        }
        if (!canFit(first.inventory, session.offeredItems(session.secondId))) {
            first.sendSystemMessage(Component.literal("Trade blocked: your inventory cannot fit the offer.").withStyle(ChatFormatting.RED))
            session.resetConsent(first.uuid)
            sync(session)
            return
        }
        if (second != null && !canFit(second.inventory, session.offeredItems(session.firstId))) {
            second.sendSystemMessage(Component.literal("Trade blocked: ${second.gameProfile.name}'s inventory cannot fit your offer.").withStyle(ChatFormatting.RED))
            session.resetConsent(second.uuid)
            sync(session)
            return
        }

        session.completed = true
        session.suppressOfferChange = true
        moveOfferToInventory(session.secondOffer, first)
        if (second != null) {
            moveOfferToInventory(session.firstOffer, second)
            transferChowcoins(first, second, firstCoins)
            transferChowcoins(second, first, secondCoins)
        } else {
            returnOfferToPlayer(session.firstOffer, first)
            if (secondCoins > 0L) ChowcoinStore.add(first, secondCoins)
            first.sendSystemMessage(Component.literal("Debug trade completed; your offer was returned.").withStyle(ChatFormatting.YELLOW))
        }
        finishCompletedTrade(session)
        ChowcoinNetwork.syncTo(first)
        second?.let(ChowcoinNetwork::syncTo)
    }

    private fun transferChowcoins(from: ServerPlayer, to: ServerPlayer, amount: Long) {
        if (amount <= 0L) return
        ChowcoinStore.set(from, ChowcoinStore.get(from) - amount)
        ChowcoinStore.add(to, amount)
    }

    private fun moveOfferToInventory(container: SimpleContainer, player: ServerPlayer) {
        (0 until container.containerSize).forEach { index ->
            val stack = container.removeItemNoUpdate(index)
            if (!stack.isEmpty) player.inventory.placeItemBackInInventory(stack)
        }
    }

    private fun returnOfferToPlayer(container: SimpleContainer, player: ServerPlayer) = moveOfferToInventory(container, player)

    private fun cancel(session: TradingSession, reason: String) {
        if (session.completed) return
        session.completed = true
        session.suppressOfferChange = true
        player(session.firstId)?.let {
            returnOfferToPlayer(session.firstOffer, it)
            it.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.YELLOW))
            it.closeContainer()
        }
        if (!session.debug) {
            player(session.secondId)?.let {
                returnOfferToPlayer(session.secondOffer, it)
                it.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.YELLOW))
                it.closeContainer()
            }
        }
        cleanup(session)
    }

    private fun cleanup(session: TradingSession) {
        sessions.remove(session.id)
        sessionsByPlayer.remove(session.firstId)
        sessionsByPlayer.remove(session.secondId)
        session.menus.clear()
    }

    private fun canFit(inventory: Inventory, stacks: List<ItemStack>): Boolean {
        val slots = inventory.items.map { it.copy() }.toMutableList()
        stacks.forEach { source ->
            var remaining = source.count
            slots.forEach { target ->
                if (remaining <= 0 || target.isEmpty || !ItemStack.isSameItemSameComponents(target, source)) return@forEach
                val moved = min(remaining, target.maxStackSize - target.count)
                if (moved > 0) {
                    target.grow(moved)
                    remaining -= moved
                }
            }
            slots.indices.forEach { index ->
                if (remaining <= 0 || !slots[index].isEmpty) return@forEach
                val moved = min(remaining, source.maxStackSize)
                slots[index] = source.copyWithCount(moved)
                remaining -= moved
            }
            if (remaining > 0) return false
        }
        return true
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        expireRequests(event.server.tickCount)
        sessions.values.toList().forEach { session ->
            if (session.completed) return@forEach
            val first = player(session.firstId) ?: return@forEach cancel(session, "Trade cancelled: ${session.firstName} left.")
            val second = if (session.debug) null else player(session.secondId) ?: return@forEach cancel(session, "Trade cancelled: ${session.secondName} left.")
            if (second != null && (first.level() != second.level() || first.distanceToSqr(second) > MAX_DISTANCE_SQR)) {
                cancel(session, "Trade cancelled: players moved too far apart.")
            }
        }
    }

    private fun finishCompletedTrade(session: TradingSession) {
        val first = player(session.firstId)
        val second = if (session.debug) null else player(session.secondId)
        cleanup(session)
        first?.closeContainer()
        second?.closeContainer()
        val message = ChatGlyphs.chowKingdomPrefix().append(Component.literal("Trade completed!").withStyle(ChatFormatting.GREEN))
        first?.let { player ->
            playCompletionSounds(player)
            player.sendSystemMessage(message)
        }
        second?.let { player ->
            playCompletionSounds(player)
            player.sendSystemMessage(message)
        }
    }

    private fun playCompletionSounds(player: ServerPlayer) {
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.35f)
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.45f, 1.65f)
    }

    private fun expireRequests(tick: Int) {
        pendingRequests.values.filter { it.expiresAtTick <= tick }.forEach { request ->
            clearRequest(request)
            player(request.fromId)?.sendSystemMessage(Component.literal("Trade request to ${request.toName} expired.").withStyle(ChatFormatting.YELLOW))
            player(request.toId)?.sendSystemMessage(Component.literal("Trade request from ${request.fromName} expired.").withStyle(ChatFormatting.GRAY))
        }
    }

    private fun clearRequest(request: TradeRequest) {
        pendingRequests.remove(request.fromId to request.toId)
        player(request.fromId)?.let { TradingNetwork.setGlow(it, request.toId, false) }
        player(request.toId)?.let { TradingNetwork.setGlow(it, request.fromId, false) }
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        pendingRequests.values.filter { it.fromId == player.uuid || it.toId == player.uuid }.forEach(::clearRequest)
        sessionsByPlayer[player.uuid]?.let { sessionId -> sessions[sessionId]?.let { cancel(it, "Trade cancelled: ${player.gameProfile.name} left.") } }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("chowkingdom").then(tradeRoot())
        )
        event.dispatcher.register(
            Commands.literal("ck").then(tradeRoot())
        )
    }

    private fun tradeRoot() = Commands.literal("trade")
        .then(Commands.literal("decline").executes(::decline))
        .then(Commands.literal("cancel").executes(::decline))
        .then(Commands.literal("debug").requires { it.hasPermission(2) }.executes(::debugTrade))

    private fun decline(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val related = pendingRequests.values.filter { it.fromId == player.uuid || it.toId == player.uuid }
        related.forEach(::clearRequest)
        if (related.isEmpty()) {
            player.sendSystemMessage(Component.literal("No pending trade request.").withStyle(ChatFormatting.GRAY))
        } else {
            player.sendSystemMessage(Component.literal("Trade request declined.").withStyle(ChatFormatting.YELLOW))
        }
        return 1
    }

    private fun debugTrade(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        if (isBusy(player.uuid)) {
            player.sendSystemMessage(Component.literal("Finish your current trade first.").withStyle(ChatFormatting.RED))
            return 0
        }
        startSession(player, player, debug = true)
        return 1
    }

    private fun sessionStateFor(session: TradingSession, viewerId: UUID): TradeStatePayload {
        val otherId = session.otherId(viewerId)
        return TradeStatePayload(
            session.id,
            viewerId,
            otherId,
            session.name(viewerId),
            session.name(otherId),
            balanceFor(session, viewerId),
            balanceFor(session, otherId),
            session.chowcoins[viewerId] ?: 0L,
            session.chowcoins[otherId] ?: 0L,
            session.ready.contains(viewerId),
            session.ready.contains(otherId),
            session.confirmed.contains(viewerId),
            session.confirmed.contains(otherId),
            session.debug,
        )
    }

    private fun isBusy(playerId: UUID): Boolean =
        sessionsByPlayer.containsKey(playerId) || pendingRequests.values.any { it.fromId == playerId || it.toId == playerId }

    private fun player(id: UUID): ServerPlayer? =
        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(id)

    private fun balanceFor(session: TradingSession, playerId: UUID): Long {
        if (session.debug && playerId == session.secondId) return session.chowcoins[playerId] ?: 0L
        return player(playerId)?.let(ChowcoinStore::get) ?: 0L
    }

}

enum class TradeAction(val id: Int) {
    SET_CHOWCOINS(0),
    READY(1),
    CONFIRM(2),
    CANCEL(3),
}
