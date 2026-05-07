package dev.gisketch.chowkingdom.relicroulette

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordRelay
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.UUID
import java.util.function.Supplier
import kotlin.random.Random

object RelicRouletteFeature {
    private val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ChowKingdomMod.MOD_ID)
    private val activeRolls: MutableMap<Pair<UUID, String>, Long> = linkedMapOf()
    private val pendingRollNotifications: MutableList<PendingRollNotification> = mutableListOf()

    val COMMON_RELIC_TOKEN: DeferredHolder<Item, RelicTokenItem> = ITEMS.register("common_relic_token", Supplier { RelicTokenItem(Item.Properties().stacksTo(16)) })
    val RARE_RELIC_TOKEN: DeferredHolder<Item, RelicTokenItem> = ITEMS.register("rare_relic_token", Supplier { RelicTokenItem(Item.Properties().stacksTo(16)) })

    fun register(modBus: IEventBus) {
        ITEMS.register(modBus)
        RelicRouletteConfig.load()
        RelicRouletteStore.load()
        RelicRouletteNetwork.register(modBus)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickItem)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickBlock)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onEntityInteract)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onEntityInteractSpecific)
        NeoForge.EVENT_BUS.addListener(::onItemTooltip)
        NeoForge.EVENT_BUS.addListener(::onEquipmentChange)
        NeoForge.EVENT_BUS.addListener(::onPlayerTick)
    }

    fun isRelicTokenReward(type: String): Boolean = type.equals("relic_token", ignoreCase = true) || type.equals("relic-token", ignoreCase = true)

    fun tokenItemIdForReward(itemId: String, poolId: String?): String = RelicRouletteConfig.tokenItemIdForReward(itemId, poolId)

    fun createBattlepassTokenStack(player: ServerPlayer, rewardItemId: String, poolId: String?, quantity: Int): ItemStack? {
        RelicRouletteConfig.load()
        val itemId = tokenItemIdForReward(rewardItemId, poolId)
        val pool = RelicRouletteConfig.poolForTicket(itemId) ?: return null
        val item = itemById(pool.ticket)
        if (item == Items.AIR) return null
        return RelicLock.lockToken(ItemStack(item, quantity.coerceAtLeast(1)), player, pool)
    }

    fun prepareBattlepassItemReward(player: ServerPlayer, stack: ItemStack): ItemStack {
        if (stack.isEmpty) return stack
        val itemId = itemId(stack)
        val pool = RelicRouletteConfig.poolForTicket(itemId) ?: return stack
        return RelicLock.lockToken(stack, player, pool)
    }

    fun isTokenItem(item: Item): Boolean = item === COMMON_RELIC_TOKEN.get() || item === RARE_RELIC_TOKEN.get() || RelicRouletteConfig.poolForTicket(BuiltInRegistries.ITEM.getKey(item).toString()) != null

    fun isTransferBlocked(stack: ItemStack): Boolean = RelicLock.isTransferBlocked(stack)

    fun isLockedForOther(stack: ItemStack, player: ServerPlayer): Boolean {
        val lock = RelicLock.read(stack) ?: return false
        return lock.ownerId != player.uuid
    }

    fun openToken(player: ServerPlayer, stack: ItemStack) {
        val pool = poolForTokenStack(stack) ?: run {
            deny(player, "RELIC BLOCKED", "Unknown relic token")
            return
        }
        val lock = RelicLock.read(stack)
        if (lock == null) {
            deny(player, "RELIC BLOCKED", "Only battlepass-earned tokens can roll")
            return
        }
        if (lock.ownerId != player.uuid) {
            deny(player, "RELIC BLOCKED", "This token belongs to ${lock.ownerName.ifBlank { "another player" }}")
            return
        }
        RelicRouletteNetwork.open(player, openPayload(player, pool))
    }

    fun roll(player: ServerPlayer, poolId: String) {
        RelicRouletteConfig.load()
        RelicRouletteStore.load()
        val pool = RelicRouletteConfig.pool(poolId) ?: run {
            deny(player, "RELIC BLOCKED", "Unknown relic pool")
            return
        }
        val candidates = remainingCandidates(player, pool)
        if (candidates.isEmpty()) {
            deny(player, "RELIC COMPLETE", "No unclaimed relics remain")
            RelicRouletteNetwork.open(player, openPayload(player, pool))
            return
        }
        if (isRollActive(player, pool.id)) {
            deny(player, "RELIC ROLLING", "Wait for the current roll to finish")
            return
        }
        val consumedSlot = consumeToken(player, pool) ?: run {
            deny(player, "RELIC BLOCKED", "No locked ${pool.displayName} token found")
            return
        }
        val resultId = candidates.random(Random.Default)
        val item = itemById(resultId)
        if (item == Items.AIR) {
            deny(player, "RELIC FAILED", "Reward item is missing")
            return
        }
        RelicRouletteStore.markUnlocked(player.uuid, pool.id, resultId)
        activeRolls[player.uuid to pool.id] = System.currentTimeMillis() + ROLL_DURATION_MS
        val reward = RelicLock.lockReward(ItemStack(item, 1), player, pool.id, resultId)
        grantReward(player, reward, consumedSlot)
        val unlocked = RelicRouletteStore.unlocked(player.uuid, pool.id).size
        RelicRouletteNetwork.rollStarted(player, rollPayload(pool, resultId, unlocked))
        pendingRollNotifications += PendingRollNotification(player.uuid, resultId, item.description.string, relicLabel(pool), System.currentTimeMillis() + ROLL_DURATION_MS)
    }

    fun rejectTransfer(player: ServerPlayer, stack: ItemStack, surface: String): Boolean {
        if (!isTransferBlocked(stack)) return false
        deny(player, "RELIC LOCKED", "Locked relics cannot enter $surface")
        return true
    }

    fun giveLockedStack(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        val working = stack.copy()
        player.inventory.add(working)
        if (working.isEmpty) return
        for (slot in 0 until player.inventory.containerSize) {
            if (!player.inventory.getItem(slot).isEmpty) continue
            player.inventory.setItem(slot, working)
            player.inventory.setChanged()
            return
        }
        Containers.dropItemStack(player.level(), player.x, player.y + 0.5, player.z, working)
    }

    fun removeTransferBlockedFromContainer(playerId: UUID, container: Container): Int {
        var rejected = 0
        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
        val player = server?.playerList?.getPlayer(playerId)
        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (!isTransferBlocked(stack)) continue
            val copy = stack.copy()
            container.setItem(slot, ItemStack.EMPTY)
            rejected += copy.count
            if (player != null) {
                giveLockedStack(player, copy)
            }
        }
        if (rejected > 0 && player != null) deny(player, "RELIC LOCKED", "Locked relics cannot enter shipping bins")
        return rejected
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        RelicRouletteConfig.load()
        RelicRouletteStore.load()
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(root("relicroulette"))
        event.dispatcher.register(Commands.literal("chowkingdom").then(root("relicroulette")))
        event.dispatcher.register(Commands.literal("ck").then(root("relicroulette")))
    }

    private fun root(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .then(Commands.literal("reload").requires { source -> source.hasPermission(2) }.executes(::reloadCommand))
        .then(giveTokenNode("give-token"))
        .then(giveTokenNode("simulate-bp"))

    private fun giveTokenNode(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .requires { source -> source.hasPermission(2) }
        .then(
            Commands.argument("targets", EntityArgument.players())
                .then(
                    Commands.argument("pool", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(RelicRouletteConfig.pools().map { pool -> pool.id }, builder) }
                        .executes { context -> giveTokenCommand(context, 1) }
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes { context -> giveTokenCommand(context, IntegerArgumentType.getInteger(context, "count")) }),
                ),
        )

    private fun reloadCommand(context: CommandContext<CommandSourceStack>): Int {
        RelicRouletteConfig.load()
        context.source.sendSuccess({ Component.literal("Reloaded ${RelicRouletteConfig.pools().size} relic roulette pools.") }, true)
        return RelicRouletteConfig.pools().size
    }

    private fun giveTokenCommand(context: CommandContext<CommandSourceStack>, count: Int): Int {
        RelicRouletteConfig.load()
        val poolId = StringArgumentType.getString(context, "pool")
        val pool = RelicRouletteConfig.pool(poolId) ?: run {
            context.source.sendFailure(Component.literal("Unknown relic pool '$poolId'."))
            return 0
        }
        val targets = EntityArgument.getPlayers(context, "targets")
        var given = 0
        targets.forEach { target ->
            val stack = createBattlepassTokenStack(target, pool.ticket, pool.id, count) ?: return@forEach
            giveLockedStack(target, stack)
            SnackbarNetwork.send(target, SnackbarNotification.item(pool.ticket, "RELIC TOKEN GRANTED", "${pool.displayName} test token", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            given += count
        }
        context.source.sendSuccess({ Component.literal("Granted $given ${pool.displayName} locked token(s) to ${targets.size} player(s).") }, true)
        return given
    }

    private fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = player.getItemInHand(event.hand)
        blockNonOwnerInteraction(player, stack) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
        }
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = player.getItemInHand(event.hand)
        blockNonOwnerInteraction(player, stack) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
        }
    }

    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = player.getItemInHand(event.hand)
        blockNonOwnerInteraction(player, stack) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
        }
    }

    private fun onEntityInteractSpecific(event: PlayerInteractEvent.EntityInteractSpecific) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = player.getItemInHand(event.hand)
        blockNonOwnerInteraction(player, stack) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
        }
    }

    private fun blockNonOwnerInteraction(player: ServerPlayer, stack: ItemStack, cancel: () -> Unit) {
        if (!isLockedForOther(stack, player)) return
        cancel()
        deny(player, "RELIC LOCKED", "This item belongs to ${RelicLock.read(stack)?.ownerName ?: "another player"}")
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.level().isClientSide || player.tickCount % 20 != 0) return
        flushRollNotifications(player)
    }

    private fun onItemTooltip(event: ItemTooltipEvent) {
        val lock = RelicLock.read(event.itemStack) ?: return
        val owner = lock.ownerName.ifBlank { lock.ownerId.toString() }
        event.toolTip += Component.literal("Player locked: $owner").withStyle(ChatFormatting.GOLD)
        event.toolTip += Component.literal("Only the owner can use or equip this relic.").withStyle(ChatFormatting.GRAY)
    }

    private fun onEquipmentChange(event: LivingEquipmentChangeEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.to
        if (!isLockedForOther(stack, player)) return
        player.setItemSlot(event.slot, ItemStack.EMPTY)
        giveLockedStack(player, stack)
        deny(player, "RELIC LOCKED", "Only ${RelicLock.read(stack)?.ownerName ?: "the owner"} can equip this relic")
    }

    private fun openPayload(player: ServerPlayer, pool: RelicPoolDefinition): RelicRouletteOpenPayload {
        val unlocked = RelicRouletteStore.unlocked(player.uuid, pool.id)
        val validPool = pool.pool.filter { id -> itemById(id) != Items.AIR }.distinct()
        val remaining = validPool.filterNot(unlocked::contains)
        return RelicRouletteOpenPayload(pool.id, pool.displayName, pool.rarity, validPool, remaining, unlocked.size, validPool.size)
    }

    private fun rollPayload(pool: RelicPoolDefinition, resultId: String, unlocked: Int): RelicRouletteRollStartedPayload {
        val spinItems = pool.pool.filter { id -> itemById(id) != Items.AIR }.distinct().ifEmpty { listOf(resultId) }
        return RelicRouletteRollStartedPayload(pool.id, pool.displayName, pool.rarity, resultId, spinItems, ROLL_DURATION_MS, unlocked, spinItems.size)
    }

    private fun remainingCandidates(player: ServerPlayer, pool: RelicPoolDefinition): List<String> {
        val unlocked = RelicRouletteStore.unlocked(player.uuid, pool.id)
        return pool.pool.filter { id -> id !in unlocked && itemById(id) != Items.AIR }.distinct()
    }

    private fun consumeToken(player: ServerPlayer, pool: RelicPoolDefinition): Int? {
        for (slot in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(slot)
            if (!canUseToken(player, stack, pool)) continue
            stack.shrink(1)
            if (stack.isEmpty) player.inventory.setItem(slot, ItemStack.EMPTY)
            player.inventory.setChanged()
            return slot
        }
        return null
    }

    private fun grantReward(player: ServerPlayer, reward: ItemStack, consumedSlot: Int) {
        if (consumedSlot in 0 until player.inventory.containerSize && player.inventory.getItem(consumedSlot).isEmpty) {
            player.inventory.setItem(consumedSlot, reward)
            player.inventory.setChanged()
            return
        }
        giveLockedStack(player, reward)
    }

    private fun flushRollNotifications(player: ServerPlayer) {
        val now = System.currentTimeMillis()
        val ready = pendingRollNotifications.filter { notification -> notification.playerId == player.uuid && notification.showAtMs <= now }
        if (ready.isEmpty()) return
        pendingRollNotifications.removeAll(ready)
        ready.forEach { notification ->
            SnackbarNetwork.send(player, SnackbarNotification.item(notification.itemId, "RELIC UNSEALED", notification.itemName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            DiscordRelay.relicRolled(player, notification.relic, notification.itemName, notification.itemId)
        }
    }

    private fun relicLabel(pool: RelicPoolDefinition): String {
        val display = pool.displayName.trim()
        if (display.isNotBlank()) return display.uppercase()
        return "${pool.rarity.uppercase()} RELIC"
    }

    private fun isRollActive(player: ServerPlayer, poolId: String): Boolean {
        val now = System.currentTimeMillis()
        activeRolls.entries.removeIf { (_, expiresAt) -> expiresAt <= now }
        return (activeRolls[player.uuid to poolId] ?: 0L) > now
    }

    private fun canUseToken(player: ServerPlayer, stack: ItemStack, pool: RelicPoolDefinition): Boolean {
        if (stack.isEmpty || itemId(stack) != pool.ticket) return false
        val lock = RelicLock.read(stack) ?: return false
        return lock.ownerId == player.uuid && lock.poolId == pool.id
    }

    private fun poolForTokenStack(stack: ItemStack): RelicPoolDefinition? = RelicRouletteConfig.poolForTicket(itemId(stack))

    private fun itemById(itemId: String): Item = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
        ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
        ?: Items.AIR

    private fun itemId(stack: ItemStack): String = if (stack.isEmpty) "minecraft:air" else BuiltInRegistries.ITEM.getKey(stack.item).toString()

    private fun deny(player: ServerPlayer, title: String, content: String) {
        SnackbarNetwork.send(player, SnackbarNotification.item(SnackbarIcons.ERROR, title, content, SnackbarType.ERROR, SnackbarSounds.ERROR))
        player.displayClientMessage(Component.literal(content), true)
    }

    private const val ROLL_DURATION_MS = 5_000L

    private data class PendingRollNotification(val playerId: UUID, val itemId: String, val itemName: String, val relic: String, val showAtMs: Long)
}