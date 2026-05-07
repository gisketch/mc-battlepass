package dev.gisketch.chowkingdom.npc

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordRelay
import dev.gisketch.chowkingdom.relicroulette.RelicRouletteFeature
import dev.gisketch.chowkingdom.shops.StoreShopFeature
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
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.max

object NpcFeature {
    private const val NPC_DIALOG_DURATION_TICKS = 100
    private val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(Registries.BLOCK, ChowKingdomMod.MOD_ID)
    private val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ChowKingdomMod.MOD_ID)
    private val ENTITIES: DeferredRegister<EntityType<*>> = DeferredRegister.create(Registries.ENTITY_TYPE, ChowKingdomMod.MOD_ID)
    private val realtimeDebugTargets: MutableMap<UUID, UUID> = linkedMapOf()
    private var debugTimeMultiplier: Int = 1

    val CAMPING_BLOCK: DeferredHolder<Block, CampingBlock> = BLOCKS.register("camping_block", Supplier { CampingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5f)) })
    val CAMPING_BLOCK_ITEM: DeferredHolder<Item, BlockItem> = ITEMS.register("camping_block", Supplier { BlockItem(CAMPING_BLOCK.get(), Item.Properties()) })
    val RENT_CONTRACT: DeferredHolder<Item, NpcRentContractItem> = ITEMS.register("rent_contract", Supplier { NpcRentContractItem(Item.Properties().stacksTo(1)) })
    val NPC_ENTITY: DeferredHolder<EntityType<*>, EntityType<ChowNpcEntity>> = ENTITIES.register(
        "npc",
        Supplier {
            EntityType.Builder.of(::ChowNpcEntity, MobCategory.CREATURE)
                .sized(0.6f, 1.8f)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build("npc")
        },
    )

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        ENTITIES.register(modBus)
        NpcNetwork.register(modBus)
        NpcConfig.load()
        modBus.addListener(::registerAttributes)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onLivingDamagePre)
        NeoForge.EVENT_BUS.addListener(::onLivingDeath)
        NeoForge.EVENT_BUS.addListener(::onBlockBreak)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickBlock)
    }

    fun spawnFromCamp(level: Level, pos: BlockPos): Boolean {
        if (level.isClientSide || level !is ServerLevel) return false
        NpcConfig.load()
        NpcStore.load()
        val definition = NpcConfig.firstIntroducible() ?: return false
        if (existingNpc(level.server, definition.id) != null) return false
        return spawnNpc(level, definition, pos)
    }

    fun interact(player: ServerPlayer, npc: ChowNpcEntity) {
        val definition = NpcConfig.get(npc.npcId) ?: return
        val validHome = validHomePos(npc.level(), definition.id)
        npc.homePos = validHome
        val hasHome = validHome != null
        val wasSleeping = npc.isSleeping
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        if (wasSleeping) npc.stopSleeping()
        val contractGranted = definition.housing.canMoveIn && !hasHome && !hasRentContract(player, definition.id)
        if (contractGranted) {
            giveStack(player, createRentContract(definition.id))
            NpcStore.markContractGiven(definition.id)
        }
        val message = if (wasSleeping) {
            friendshipMessage(definition.friendshipMessages.wake, friendship, player, definition)
        } else if (hasHome) {
            friendshipMessage(definition.friendshipMessages.interact, friendship, player, definition)
        } else {
            "Hi, I'm ${definition.name}. I'm an ${definition.job} looking for a place to stay. If it's okay, use this rent contract on a bed and I'll call it home."
        }
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "interacts with ${definition.name}", "player_interact")
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_message")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, contractGranted, friendship.level))
        relayNpcDialog(player, npc, definition, message)
    }

    fun handleDialogAction(player: ServerPlayer, npcId: String, action: String) {
        val definition = NpcConfig.get(npcId) ?: return
        val npc = existingNpc(player.server, definition.id) ?: return
        if (npc.level() != player.level() || player.distanceToSqr(npc) > NPC_DIALOG_ACTION_DISTANCE_SQR) return
        when (action.lowercase()) {
            "buy" -> openNpcShop(player, definition)
            "gift" -> giftToNpc(player, npc, definition)
        }
    }

    private fun openNpcShop(player: ServerPlayer, definition: NpcDefinition) {
        val storeId = definition.store.trim().lowercase()
        if (storeId.isBlank() || !StoreShopFeature.openStore(player, storeId)) {
            npcSnackbar(player, definition.name, "No shop ready.", SnackbarType.ERROR)
        }
    }

    fun onStorePurchase(player: ServerPlayer, storeId: String, quantity: Int, itemName: String, totalCost: Long) {
        val definition = NpcConfig.all().firstOrNull { npc -> npc.store.equals(storeId, ignoreCase = true) } ?: return
        val npc = existingNpc(player.server, definition.id) ?: return
        if (npc.level() != player.level()) return
        if (player.distanceToSqr(npc) > NPC_DIALOG_ACTION_DISTANCE_SQR) return
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val message = friendshipMessage(definition.shopMessages.forQuantity(quantity), friendship, player, definition, itemName, quantity = quantity, totalCost = totalCost)
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "buys $quantity $itemName from ${definition.name}", "player_shop_buy")
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_shop_message")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
    }

    private fun giftToNpc(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val stack = giftStack(player)
        if (stack.isEmpty) {
            npcSnackbar(player, definition.name, "Hold an item to gift.", SnackbarType.ERROR)
            return
        }
        if (RelicRouletteFeature.rejectTransfer(player, stack, "gifts")) return
        val limit = definition.gifts.dailyLimit
        val period = giftPeriod(player.level().dayTime, definition.gifts.resetHour)
        if (!NpcStore.recordGiftIfAllowed(definition.id, player, period, limit)) {
            npcSnackbar(player, definition.name, "Can receive another gift at ${definition.gifts.resetHour.toString().padStart(2, '0')}:00.", SnackbarType.ERROR)
            return
        }
        val itemName = stack.hoverName.string
        val mood = giftMood(definition, stack)
        val friendship = NpcStore.adjustFriendship(definition.id, player, giftFriendshipDelta(mood), "gift_$mood")
        val message = friendshipMessage(definition.friendshipMessages.gift, friendship, player, definition, itemName, mood)
        if (!player.abilities.instabuild) stack.shrink(1)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "gifts $itemName to ${definition.name}", "player_gift")
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_gift_$mood")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
        relayNpcDialog(player, npc, definition, message)
    }

    private fun giftStack(player: ServerPlayer): ItemStack = if (!player.mainHandItem.isEmpty) player.mainHandItem else player.offhandItem

    private fun giftMood(definition: NpcDefinition, stack: ItemStack): String = when {
        matchesGift(definition.gifts.loved, stack) -> "loved"
        matchesGift(definition.gifts.liked, stack) -> "liked"
        matchesGift(definition.gifts.disliked, stack) -> "disliked"
        else -> "neutral"
    }

    private fun matchesGift(values: List<String>, stack: ItemStack): Boolean = values.any { raw ->
        if (raw.startsWith("#")) {
            val id = runCatching { ResourceLocation.parse(raw.removePrefix("#")) }.getOrNull() ?: return@any false
            stack.`is`(TagKey.create(Registries.ITEM, id))
        } else {
            BuiltInRegistries.ITEM.getKey(stack.item).toString() == raw
        }
    }

    private fun giftFriendshipDelta(mood: String): Int = when (mood) {
        "loved" -> 50
        "liked" -> 25
        "disliked" -> -50
        else -> 5
    }

    private fun npcSnackbar(player: ServerPlayer, title: String, content: String, type: SnackbarType) {
        SnackbarNetwork.send(player, SnackbarNotification.item(SnackbarIcons.ERROR, title, content, type, SnackbarSounds.forType(type)))
    }

    private fun dialogPayload(definition: NpcDefinition, npc: ChowNpcEntity, message: String, contractGranted: Boolean, friendshipLevel: Int, closeOnly: Boolean = false, closeLabel: String = "BYE"): NpcDialogPayload = NpcDialogPayload(
        definition.id,
        definition.name,
        definition.title,
        message,
        contractGranted,
        closeOnly = closeOnly,
        closeLabel = closeLabel,
        friendshipLevel = friendshipLevel,
        npcEntityId = npc.id,
        animalesePitch = definition.voice.animalesePitch,
        animalesePitchMultiplier = definition.voice.pitch,
        animaleseVolume = definition.voice.volume,
        animaleseRadius = definition.voice.radius,
    )

    private fun friendshipMessage(set: NpcFriendshipMessageSet, friendship: NpcFriendshipSnapshot, player: ServerPlayer, definition: NpcDefinition, itemName: String = "", mood: String = "", quantity: Int = 0, totalCost: Long = 0L): String {
        val pool = set.forCategory(friendship.category)
        return pool[player.random.nextInt(pool.size)]
            .replace("{player}", player.gameProfile.name)
            .replace("{npc}", definition.name)
            .replace("{item}", itemName)
            .replace("{mood}", mood)
            .replace("{quantity}", quantity.toString())
            .replace("{total}", totalCost.toString())
            .replace("{friendship_level}", friendship.level.toString())
            .replace("{friendship_points}", friendship.points.toString())
    }

    private fun giftPeriod(dayTime: Long, resetHour: Int): Long = Math.floorDiv(dayTime - (resetHour - 6) * TICKS_PER_HOUR, MINECRAFT_DAY_TICKS)

    private fun relayNpcDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, message: String) {
        val chatLine = Component.literal("${definition.name} > ${player.gameProfile.name} : $message").withStyle(ChatFormatting.GRAY)
        val level = npc.level() as? ServerLevel ?: return
        level.players().forEach { listener ->
            if (listener.uuid != player.uuid && listener.distanceToSqr(npc.x, npc.y, npc.z) <= NPC_DIALOG_HEAR_RADIUS * NPC_DIALOG_HEAR_RADIUS) {
                listener.sendSystemMessage(chatLine)
            }
        }
        DiscordRelay.npcDialog(player, definition.id, definition.name, message)
    }

    private fun relayNpcHurtMessage(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val message = friendshipMessage(definition.friendshipMessages.hurt, friendship, player, definition)
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_hurt_message")
        val chatLine = Component.literal("${definition.name} > ${player.gameProfile.name} : $message").withStyle(ChatFormatting.GRAY)
        val level = npc.level() as? ServerLevel ?: return
        level.players().forEach { listener ->
            if (listener.distanceToSqr(npc.x, npc.y, npc.z) <= NPC_DIALOG_HEAR_RADIUS * NPC_DIALOG_HEAR_RADIUS) listener.sendSystemMessage(chatLine)
        }
    }

    fun assignHome(player: ServerPlayer, npcId: String, bedPos: BlockPos, stack: ItemStack): Boolean {
        val definition = NpcConfig.get(npcId) ?: run {
            player.displayClientMessage(Component.literal("Unknown NPC '$npcId'."), true)
            return false
        }
        if (!definition.housing.canMoveIn) {
            player.displayClientMessage(Component.literal("${definition.name} cannot move in."), true)
            return false
        }
        val homePos = canonicalBedPos(player.level(), bedPos)
        NpcStore.setHome(npcId, homePos)
        existingNpc(player.server, npcId)?.let { npc ->
            npc.homePos = homePos.immutable()
            npc.campPos = npc.campPos ?: NpcStore.campPos(npcId)
        }
        if (!player.abilities.instabuild) stack.shrink(1)
        SnackbarNetwork.send(player, SnackbarNotification.item(BuiltInRegistries.ITEM.getKey(RENT_CONTRACT.get()).toString(), "HOME ASSIGNED", "${definition.name} now lives here", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        player.sendSystemMessage(Component.literal("${definition.name}'s home is now set to this bed.").withStyle(ChatFormatting.GREEN))
        return true
    }

    fun tickNpc(entity: ChowNpcEntity) {
        val definition = NpcConfig.get(entity.npcId) ?: return
        entity.homePos = validHomePos(entity.level(), definition.id)
        entity.campPos = entity.campPos ?: NpcStore.campPos(definition.id)
        if (NpcBrainOverrides.tick(entity, definition)) return
        if (entity.isTalking()) return
        NpcBrain.tick(entity, definition)
    }

    fun moveToActivityTarget(entity: ChowNpcEntity, definition: NpcDefinition, activity: String) {
        if (activity == "sleep" && entity.homePos != null) {
            val home = entity.homePos ?: return
            val target = sleepingBedPos(entity.level(), home)
            entity.debugActivity = activity
            entity.debugGoal = if (entity.distanceToSqr(target.x + 0.5, target.y.toDouble(), target.z + 0.5) <= SLEEP_REACH_DISTANCE_SQR) "sleep" else "go_sleep"
            entity.debugTargetPos = target.immutable()
            if (entity.debugGoal == "sleep") {
                entity.navigation.stop()
                if (!entity.isSleeping) {
                    entity.startSleeping(target)
                    alignSleepingNpc(entity, target)
                }
            } else {
                if (entity.isSleeping) entity.stopSleeping()
                entity.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 0.8)
            }
            return
        }
        if (activity == "home" && entity.homePos != null) {
            if (entity.isSleeping) entity.stopSleeping()
            val target = entity.homePos ?: return
            entity.debugActivity = activity
            entity.debugGoal = "home"
            entity.debugTargetPos = target.immutable()
            entity.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 0.8)
            return
        }
        val workTarget = findWorkTarget(entity, definition)
        val target = workTarget ?: randomRoamTarget(entity, definition) ?: return
        if (entity.isSleeping) entity.stopSleeping()
        entity.debugActivity = activity
        entity.debugGoal = if (workTarget != null) "work_target" else "roam"
        entity.debugTargetPos = target.immutable()
        entity.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 0.8)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        NpcConfig.load()
        NpcStore.load()
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        tickDebugTime(event.server)
        tickNpcRespawns(event.server)
        tickRealtimeDebug(event.server)
    }

    private fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
        val npc = event.entity as? ChowNpcEntity ?: return
        val player = event.source.entity as? ServerPlayer ?: return
        val definition = NpcConfig.get(npc.npcId) ?: return
        val hitCount = NpcStore.recordHurt(definition.id, player, System.currentTimeMillis())
        NpcStore.adjustFriendship(definition.id, player, FRIENDSHIP_HIT_DELTA, "hit")
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "hurts ${definition.name}", "player_hurt")
        if (hitCount % HURT_MESSAGE_INTERVAL == 0) {
            relayNpcHurtMessage(player, npc, definition)
            NpcBrainOverrides.startHurtResponse(npc, player)
        }
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        val npc = event.entity as? ChowNpcEntity
        if (npc != null) {
            NpcBrainOverrides.clear(npc)
            val definition = NpcConfig.get(npc.npcId) ?: return
            val killer = event.source.entity as? ServerPlayer
            val deathText = killer?.let { "${definition.name} died, killed by ${it.gameProfile.name}" } ?: "${definition.name} died"
            NpcStore.recordGlobalEvent("npc_death", deathText)
            killer?.let { player ->
                NpcStore.adjustFriendship(definition.id, player, FRIENDSHIP_KILL_DELTA, "kill")
                NpcStore.recordConversation(definition.id, player, definition.name, deathText, "npc_death")
            }
            NpcStore.markDead(definition.id, nextRespawnDay(npc.level().dayTime))
            return
        }
        val player = event.entity as? ServerPlayer ?: return
        NpcStore.recordGlobalEvent("player_death", event.source.getLocalizedDeathMessage(player).string)
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!event.state.`is`(BlockTags.BEDS)) return
        val npcId = homeOwnerAtBed(level, event.pos) ?: return
        NpcStore.clearHome(npcId)
        existingNpc(level.server, npcId)?.homePos = null
        NpcStore.recordGlobalEvent("npc_home_lost", "$npcId lost assigned bed")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(npcRoot("npc"))
        event.dispatcher.register(Commands.literal("ck").then(npcRoot("npc")))
        event.dispatcher.register(Commands.literal("chowkingdom").then(npcRoot("npc")))
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.hand != InteractionHand.MAIN_HAND) return
        val player = event.entity
        val stack = player.getItemInHand(event.hand)
        val npcId = NpcRentContractData.readNpcId(stack)
        if (npcId.isBlank()) return
        if (!player.level().getBlockState(event.pos).`is`(BlockTags.BEDS)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        if (player.level().isClientSide) return
        player as? ServerPlayer ?: return
        assignHome(player, npcId, event.pos, stack)
    }

    fun homeOwnerAtBed(level: Level, pos: BlockPos): String? {
        val canonical = canonicalBedPos(level, pos)
        val paired = pairedBedHalfPos(level, pos)
        return NpcStore.homeOwnerAt(canonical) ?: NpcStore.homeOwnerAt(pos) ?: paired?.let(NpcStore::homeOwnerAt)
    }

    private fun validHomePos(level: Level, npcId: String): BlockPos? {
        val home = NpcStore.homePos(npcId) ?: return null
        if (isBedPairPresent(level, home)) return canonicalBedPos(level, home)
        NpcStore.clearHome(npcId)
        NpcStore.recordGlobalEvent("npc_home_missing", "$npcId assigned bed is missing")
        return null
    }

    private fun isBedPairPresent(level: Level, pos: BlockPos): Boolean {
        if (level.getBlockState(pos).`is`(BlockTags.BEDS)) return true
        val paired = pairedBedHalfPos(level, pos) ?: return false
        return level.getBlockState(paired).`is`(BlockTags.BEDS)
    }

    private fun canonicalBedPos(level: Level, pos: BlockPos): BlockPos {
        val state = level.getBlockState(pos)
        if (!state.`is`(BlockTags.BEDS)) return pos
        return if (state.getValue(BedBlock.PART) == BedPart.FOOT) pos.relative(state.getValue(BedBlock.FACING)) else pos
    }

    private fun pairedBedHalfPos(level: Level, pos: BlockPos): BlockPos? {
        val state = level.getBlockState(pos)
        if (!state.`is`(BlockTags.BEDS)) return null
        val facing = state.getValue(BedBlock.FACING)
        return if (state.getValue(BedBlock.PART) == BedPart.FOOT) pos.relative(facing) else pos.relative(facing.opposite)
    }

    private fun sleepingBedPos(level: Level, pos: BlockPos): BlockPos {
        val state = level.getBlockState(pos)
        if (!state.`is`(BlockTags.BEDS)) return pos
        return if (state.getValue(BedBlock.PART) == BedPart.HEAD) pos.relative(state.getValue(BedBlock.FACING).opposite) else pos
    }

    private fun alignSleepingNpc(entity: ChowNpcEntity, bedPos: BlockPos) {
        val state = entity.level().getBlockState(bedPos)
        if (!state.`is`(BlockTags.BEDS)) return
        val pillowDirection = if (state.getValue(BedBlock.PART) == BedPart.FOOT) state.getValue(BedBlock.FACING) else state.getValue(BedBlock.FACING).opposite
        entity.setPos(
            entity.x + pillowDirection.stepX * SLEEP_PILLOW_OFFSET,
            entity.y,
            entity.z + pillowDirection.stepZ * SLEEP_PILLOW_OFFSET,
        )
    }

    private fun npcRoot(name: String): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .then(Commands.literal("reload").requires { source -> source.hasPermission(2) }.executes(::reloadCommand))
        .then(
            Commands.literal("debug")
                .requires { source -> source.hasPermission(2) }
                .executes(::debugCommand)
                .then(
                    Commands.literal("time")
                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 240)).executes(::debugTimeCommand)),
                ),
        )
        .then(
            Commands.literal("spawn")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.argument("id", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(NpcConfig.all().map { definition -> definition.id }, builder) }
                        .executes(::spawnCommand),
                ),
        )
        .then(
            Commands.literal("respawn")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("status")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .suggests(::suggestNpcIds)
                                .executes(::respawnStatusCommand),
                        ),
                )
                .then(
                    Commands.argument("id", StringArgumentType.word())
                        .suggests(::suggestNpcIds)
                        .executes(::respawnCommand),
                ),
        )
        .then(
            Commands.literal("friendship")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("get")
                        .then(friendshipTargetArgument().executes(::friendshipGetCommand)),
                )
                .then(
                    Commands.literal("set")
                        .then(friendshipTargetArgument().then(Commands.argument("points", IntegerArgumentType.integer(NpcFriendshipLevels.MIN_POINTS, NpcFriendshipLevels.MAX_POINTS)).executes(::friendshipSetCommand))),
                )
                .then(
                    Commands.literal("add")
                        .then(friendshipTargetArgument().then(Commands.argument("delta", IntegerArgumentType.integer(-2000, 2000)).executes(::friendshipAddCommand))),
                )
        )

    private fun suggestNpcIds(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(NpcConfig.all().map { definition -> definition.id }, builder)

    private fun friendshipTargetArgument() = Commands.argument("id", StringArgumentType.word())
        .suggests(::suggestNpcIds)
        .then(Commands.argument("player", EntityArgument.player()))

    private fun reloadCommand(context: CommandContext<CommandSourceStack>): Int {
        NpcConfig.load()
        context.source.sendSuccess({ Component.literal("Reloaded ${NpcConfig.all().size} NPC definition(s).") }, true)
        return NpcConfig.all().size
    }

    private fun debugCommand(context: CommandContext<CommandSourceStack>): Int {
        NpcConfig.load()
        val player = context.source.playerOrException
        val npc = lookedAtNpc(player) ?: run {
            realtimeDebugTargets.remove(player.uuid)
            context.source.sendFailure(Component.literal("No Chow Kingdom NPC under crosshair."))
            return 0
        }
        if (realtimeDebugTargets[player.uuid] == npc.uuid) {
            realtimeDebugTargets.remove(player.uuid)
            player.sendSystemMessage(Component.literal("NPC realtime debug disabled.").withStyle(ChatFormatting.GRAY))
            return 1
        }
        realtimeDebugTargets[player.uuid] = npc.uuid
        val definition = NpcConfig.get(npc.npcId)
        val camp = npc.campPos?.toShortString() ?: "unset"
        val home = npc.homePos?.toShortString() ?: "unset"
        val target = npc.debugTargetPos?.toShortString() ?: "none"
        val navigation = if (npc.navigation.isDone) "idle" else "moving"
        val routine = "every ${definition?.jobDefinition?.scanIntervalTicks ?: 60} ticks: schedule -> target -> path"
        listOf(
            Component.literal("NPC REALTIME DEBUG: ${definition?.displayName() ?: npc.npcId}").withStyle(ChatFormatting.GOLD),
            Component.literal("id=${npc.npcId} job=${definition?.job ?: "unknown"} body=${npc.bodyType} nav=$navigation"),
            Component.literal("activity=${npc.debugActivity} task=${npc.debugGoal} target=$target routine=$routine"),
            Component.literal("camp=$camp home=$home store=${definition?.store.orEmpty().ifBlank { "none" }}"),
            Component.literal("Actionbar will update live. Run /npc debug on the same NPC again to stop.").withStyle(ChatFormatting.DARK_GRAY),
        ).forEach(player::sendSystemMessage)
        return 1
    }

    private fun debugTimeCommand(context: CommandContext<CommandSourceStack>): Int {
        debugTimeMultiplier = IntegerArgumentType.getInteger(context, "multiplier")
        val message = if (debugTimeMultiplier == 1) "NPC debug time speed reset." else "NPC debug time speed set to ${debugTimeMultiplier}x."
        context.source.sendSuccess({ Component.literal(message) }, true)
        return debugTimeMultiplier
    }

    private fun respawnStatusCommand(context: CommandContext<CommandSourceStack>): Int {
        NpcConfig.load()
        val id = StringArgumentType.getString(context, "id")
        val definition = NpcConfig.get(id) ?: run {
            context.source.sendFailure(Component.literal("Unknown NPC '$id'."))
            return 0
        }
        val server = context.source.server
        val dayTime = server.overworld().dayTime
        val currentDay = dayTime / MINECRAFT_DAY_TICKS
        val currentHour = NpcScheduleDefinition.hourAt(dayTime)
        val respawnDay = NpcStore.respawnDay(id)
        val liveNpc = existingNpc(server, id)
        val home = NpcStore.homePos(id)
        val validHome = validHomePos(server.overworld(), id)
        val ready = NpcStore.isDead(id) && liveNpc == null && respawnReady(dayTime, respawnDay) && validHome != null
        listOf(
            Component.literal("NPC RESPAWN: ${definition.displayName()}").withStyle(ChatFormatting.GOLD),
            Component.literal("live=${liveNpc != null} dead=${NpcStore.isDead(id)} currentDay=$currentDay currentHour=$currentHour respawnDay=$respawnDay ready=$ready"),
            Component.literal("home=${home?.toShortString() ?: "unset"} validHome=${validHome?.toShortString() ?: "no"}"),
            Component.literal("Rule: respawns at valid home bed when current day is due and hour is $NPC_RESPAWN_HOUR or later."),
        ).forEach(context.source::sendSystemMessage)
        return if (ready) 1 else 0
    }

    private fun respawnCommand(context: CommandContext<CommandSourceStack>): Int {
        NpcConfig.load()
        val id = StringArgumentType.getString(context, "id")
        val definition = NpcConfig.get(id) ?: run {
            context.source.sendFailure(Component.literal("Unknown NPC '$id'."))
            return 0
        }
        val server = context.source.server
        if (existingNpc(server, id) != null) {
            context.source.sendFailure(Component.literal("${definition.displayName()} already exists."))
            return 0
        }
        val home = validHomePos(server.overworld(), id) ?: run {
            context.source.sendFailure(Component.literal("${definition.displayName()} has no valid home bed."))
            return 0
        }
        return if (respawnNpc(server.overworld(), definition, home)) {
            context.source.sendSuccess({ Component.literal("Respawned ${definition.displayName()}.") }, true)
            1
        } else {
            context.source.sendFailure(Component.literal("Could not respawn ${definition.displayName()}."))
            0
        }
    }

    private fun friendshipGetCommand(context: CommandContext<CommandSourceStack>): Int {
        val definition = npcDefinitionArgument(context) ?: return 0
        val player = EntityArgument.getPlayer(context, "player")
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        context.source.sendSuccess({ Component.literal("${definition.displayName()} / ${player.gameProfile.name}: ${friendship.points} points, Lv.${friendship.level}, ${friendship.category.id}.") }, false)
        return friendship.points
    }

    private fun friendshipSetCommand(context: CommandContext<CommandSourceStack>): Int {
        val definition = npcDefinitionArgument(context) ?: return 0
        val player = EntityArgument.getPlayer(context, "player")
        val points = IntegerArgumentType.getInteger(context, "points")
        val friendship = NpcStore.setFriendship(definition.id, player, points, "command_set")
        context.source.sendSuccess({ Component.literal("Set ${definition.displayName()} / ${player.gameProfile.name} to ${friendship.points} points, Lv.${friendship.level}.") }, true)
        return friendship.points
    }

    private fun friendshipAddCommand(context: CommandContext<CommandSourceStack>): Int {
        val definition = npcDefinitionArgument(context) ?: return 0
        val player = EntityArgument.getPlayer(context, "player")
        val delta = IntegerArgumentType.getInteger(context, "delta")
        val friendship = NpcStore.adjustFriendship(definition.id, player, delta, "command_add")
        context.source.sendSuccess({ Component.literal("Adjusted ${definition.displayName()} / ${player.gameProfile.name} by $delta to ${friendship.points} points, Lv.${friendship.level}.") }, true)
        return friendship.points
    }

    private fun npcDefinitionArgument(context: CommandContext<CommandSourceStack>): NpcDefinition? {
        val id = StringArgumentType.getString(context, "id")
        val definition = NpcConfig.get(id)
        if (definition == null) context.source.sendFailure(Component.literal("Unknown NPC '$id'."))
        return definition
    }

    private fun tickDebugTime(server: MinecraftServer) {
        if (debugTimeMultiplier <= 1) return
        server.allLevels.forEach { level -> level.setDayTime(level.dayTime + debugTimeMultiplier - 1L) }
    }

    private fun tickNpcRespawns(server: MinecraftServer) {
        if (server.tickCount % RESPAWN_SCAN_INTERVAL_TICKS != 0) return
        val dayTime = server.overworld().dayTime
        val currentDay = dayTime / MINECRAFT_DAY_TICKS
        NpcStore.deadNpcIds().forEach { npcId ->
            if (!respawnReady(dayTime, NpcStore.respawnDay(npcId))) return@forEach
            val definition = NpcConfig.get(npcId) ?: return@forEach
            if (existingNpc(server, npcId) != null) {
                NpcStore.clearDead(npcId)
                return@forEach
            }
            val home = validHomePos(server.overworld(), npcId) ?: return@forEach
            respawnNpc(server.overworld(), definition, home)
        }
    }

    private fun respawnReady(dayTime: Long, respawnDay: Long): Boolean {
        val currentDay = dayTime / MINECRAFT_DAY_TICKS
        return currentDay > respawnDay || currentDay == respawnDay && NpcScheduleDefinition.hourAt(dayTime) >= NPC_RESPAWN_HOUR
    }

    private fun nextRespawnDay(dayTime: Long): Long {
        val currentDay = dayTime / MINECRAFT_DAY_TICKS
        return if (NpcScheduleDefinition.hourAt(dayTime) < NPC_RESPAWN_HOUR) currentDay else currentDay + 1
    }

    private fun tickRealtimeDebug(server: MinecraftServer) {
        if (server.tickCount % REALTIME_DEBUG_INTERVAL_TICKS != 0) return
        val iterator = realtimeDebugTargets.iterator()
        while (iterator.hasNext()) {
            val (playerId, npcId) = iterator.next()
            val player = server.playerList.getPlayer(playerId) ?: run {
                iterator.remove()
                continue
            }
            val npc = findNpc(server, npcId) ?: run {
                player.displayClientMessage(Component.literal("NPC debug target missing.").withStyle(ChatFormatting.RED), true)
                iterator.remove()
                continue
            }
            if (!npc.isAlive) {
                iterator.remove()
                continue
            }
            player.displayClientMessage(Component.literal(realtimeDebugLine(npc)).withStyle(ChatFormatting.AQUA), true)
        }
    }

    private fun realtimeDebugLine(npc: ChowNpcEntity): String {
        val nav = if (npc.navigation.isDone) "idle" else "moving"
        val target = npc.debugTargetPos?.toShortString() ?: "none"
        val hour = NpcScheduleDefinition.hourAt(npc.level().dayTime).toString().padStart(2, '0')
        val time = if (debugTimeMultiplier > 1) " time=${debugTimeMultiplier}x" else ""
        return "${npc.npcId} | hour=$hour activity=${npc.debugActivity} task=${npc.debugGoal} nav=$nav target=$target$time"
    }

    private fun spawnCommand(context: CommandContext<CommandSourceStack>): Int {
        NpcConfig.load()
        val player = context.source.playerOrException
        val id = StringArgumentType.getString(context, "id")
        val definition = NpcConfig.get(id) ?: run {
            context.source.sendFailure(Component.literal("Unknown NPC '$id'."))
            return 0
        }
        val camp = player.blockPosition()
        return if (spawnNpc(player.level() as ServerLevel, definition, camp)) {
            context.source.sendSuccess({ Component.literal("Spawned ${definition.displayName()}.") }, true)
            1
        } else {
            context.source.sendFailure(Component.literal("${definition.displayName()} already exists."))
            0
        }
    }

    private fun registerAttributes(event: EntityAttributeCreationEvent) {
        event.put(
            NPC_ENTITY.get(),
            Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .build(),
        )
    }

    private fun spawnNpc(level: ServerLevel, definition: NpcDefinition, campPos: BlockPos): Boolean {
        if (existingNpc(level.server, definition.id) != null) return false
        val npc = NPC_ENTITY.get().create(level) ?: return false
        npc.configure(definition, campPos)
        npc.moveTo(campPos.x + 0.5, campPos.y + 1.0, campPos.z + 0.5, level.random.nextFloat() * 360.0f, 0.0f)
        level.addFreshEntity(npc)
        NpcStore.setEntity(definition.id, npc.uuid, campPos)
        NpcStore.clearDead(definition.id)
        return true
    }

    private fun respawnNpc(level: ServerLevel, definition: NpcDefinition, homePos: BlockPos): Boolean {
        val npc = NPC_ENTITY.get().create(level) ?: return false
        val camp = NpcStore.campPos(definition.id) ?: homePos
        npc.configure(definition, camp)
        npc.homePos = homePos.immutable()
        npc.moveTo(homePos.x + 0.5, homePos.y + 1.0, homePos.z + 0.5, level.random.nextFloat() * 360.0f, 0.0f)
        level.addFreshEntity(npc)
        NpcStore.setEntity(definition.id, npc.uuid, camp)
        NpcStore.clearDead(definition.id)
        NpcStore.recordGlobalEvent("npc_respawn", "${definition.name} respawned at home bed")
        return true
    }

    private fun createRentContract(npcId: String): ItemStack = NpcRentContractData.forNpc(ItemStack(RENT_CONTRACT.get()), npcId)

    private fun hasRentContract(player: ServerPlayer, npcId: String): Boolean {
        for (slot in 0 until player.inventory.containerSize) {
            if (NpcRentContractData.readNpcId(player.inventory.getItem(slot)) == npcId) return true
        }
        return false
    }

    private fun giveStack(player: ServerPlayer, stack: ItemStack) {
        val working = stack.copy()
        player.inventory.add(working)
        if (!working.isEmpty) player.drop(working, false)
    }

    private fun existingNpc(server: MinecraftServer, npcId: String): ChowNpcEntity? {
        val storedId = NpcStore.entityUuid(npcId)
        val byStore = storedId?.let { uuid -> findNpc(server, uuid) }
        if (byStore != null && byStore.isAlive) return byStore
        return server.allLevels.asSequence()
            .flatMap { level -> level.getEntities(NPC_ENTITY.get()) { npc -> npc.npcId == npcId && npc.isAlive }.asSequence() }
            .firstOrNull()
    }

    private fun findNpc(server: MinecraftServer, entityId: UUID): ChowNpcEntity? = server.allLevels.asSequence()
        .mapNotNull { level -> level.getEntity(entityId) as? ChowNpcEntity }
        .firstOrNull()

    private fun lookedAtNpc(player: ServerPlayer): ChowNpcEntity? {
        val level = player.level() as? ServerLevel ?: return null
        val start = player.getEyePosition(0.0f)
        val look = player.lookAngle.normalize()
        val end = start.add(look.scale(DEBUG_REACH))
        val scanBox = AABB(start, end).inflate(1.5)
        return level.getEntities(NPC_ENTITY.get(), scanBox) { npc -> npc.isAlive }.asSequence()
            .mapNotNull { npc -> npcLookHit(start, look, npc) }
            .minWithOrNull(compareBy<NpcLookHit> { it.distanceSqr }.thenBy { it.along })
            ?.npc
    }

    private fun npcLookHit(start: Vec3, look: Vec3, npc: ChowNpcEntity): NpcLookHit? {
        val center = npc.position().add(0.0, npc.bbHeight.toDouble() * 0.5, 0.0)
        val offset = center.subtract(start)
        val along = offset.dot(look)
        if (along < 0.0 || along > DEBUG_REACH) return null
        val closest = start.add(look.scale(along))
        val distanceSqr = center.distanceToSqr(closest)
        return if (distanceSqr <= DEBUG_AIM_RADIUS * DEBUG_AIM_RADIUS) NpcLookHit(npc, along, distanceSqr) else null
    }

    private fun randomRoamTarget(entity: ChowNpcEntity, definition: NpcDefinition): BlockPos? {
        val base = entity.homePos ?: entity.campPos ?: entity.blockPosition()
        val random = entity.random
        val radius = definition.jobDefinition.roamRadius
        repeat(16) {
            val x = base.x + random.nextInt(radius * 2 + 1) - radius
            val z = base.z + random.nextInt(radius * 2 + 1) - radius
            val y = entity.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos(x, base.y, z)).y
            val pos = BlockPos(x, y, z)
            if (entity.navigation.createPath(pos, 0) != null) return pos
        }
        return null
    }

    private fun findWorkTarget(entity: ChowNpcEntity, definition: NpcDefinition): BlockPos? {
        if (definition.workTargetBlocks.isEmpty()) return null
        val base = entity.homePos ?: entity.campPos ?: entity.blockPosition()
        val random = entity.random
        val radius = definition.jobDefinition.workScanRadius
        val yRadius = max(3, radius / 2)
        repeat(32) {
            val pos = BlockPos(
                base.x + random.nextInt(radius * 2 + 1) - radius,
                base.y + random.nextInt(yRadius * 2 + 1) - yRadius,
                base.z + random.nextInt(radius * 2 + 1) - radius,
            )
            if (matchesAny(entity.level().getBlockState(pos), definition.workTargetBlocks)) return pos.above()
        }
        return null
    }

    private fun matchesAny(state: BlockState, targets: List<String>): Boolean = targets.any { raw ->
        if (raw.startsWith("#")) {
            val id = runCatching { ResourceLocation.parse(raw.removePrefix("#")) }.getOrNull() ?: return@any false
            state.`is`(TagKey.create(Registries.BLOCK, id))
        } else {
            BuiltInRegistries.BLOCK.getKey(state.block).toString() == raw
        }
    }

    private data class NpcLookHit(val npc: ChowNpcEntity, val along: Double, val distanceSqr: Double)

    private const val DEBUG_REACH = 12.0
    private const val DEBUG_AIM_RADIUS = 1.5
    private const val REALTIME_DEBUG_INTERVAL_TICKS = 10
    private const val SLEEP_REACH_DISTANCE_SQR = 4.0
    private const val SLEEP_PILLOW_OFFSET = 0.5
    private const val NPC_DIALOG_HEAR_RADIUS = 30.0
    private const val NPC_DIALOG_ACTION_DISTANCE_SQR = 64.0
    private const val HURT_MESSAGE_INTERVAL = 3
    private const val FRIENDSHIP_HIT_DELTA = -10
    private const val FRIENDSHIP_KILL_DELTA = -300
    private const val RESPAWN_SCAN_INTERVAL_TICKS = 20
    private const val NPC_RESPAWN_HOUR = 5
    private const val TICKS_PER_HOUR = 1000L
    private const val MINECRAFT_DAY_TICKS = 24000L
}
