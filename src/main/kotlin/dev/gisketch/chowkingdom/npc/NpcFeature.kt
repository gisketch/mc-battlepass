package dev.gisketch.chowkingdom.npc

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowClockConfig
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
import net.minecraft.core.Direction
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
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
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
    private val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChowKingdomMod.MOD_ID)
    private val realtimeDebugTargets: MutableMap<UUID, UUID> = linkedMapOf()
    private val clockDebugPlayers: MutableSet<UUID> = linkedSetOf()
    private val greetingRadiusPlayers: MutableMap<UUID, MutableSet<String>> = linkedMapOf()
    private val pendingShopNpcs: MutableMap<UUID, String> = linkedMapOf()
    private var debugTimeMultiplier: Int = 1

    val CAMPING_BLOCK: DeferredHolder<Block, CampingBlock> = BLOCKS.register("camping_block", Supplier { CampingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5f).noOcclusion()) })
    val CAMPING_BLOCK_ITEM: DeferredHolder<Item, BlockItem> = ITEMS.register("camping_block", Supplier { BlockItem(CAMPING_BLOCK.get(), Item.Properties()) })
    val CAMPING_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<CampingBlockEntity>> = BLOCK_ENTITIES.register(
        "camping_block",
        Supplier { BlockEntityType.Builder.of(::CampingBlockEntity, CAMPING_BLOCK.get()).build(null) },
    )
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
        BLOCK_ENTITIES.register(modBus)
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
        NpcStore.setCampBlock(pos)
        val active = activeUnhousedCamper(level.server) ?: migrateLiveUnhousedCamper(level.server)
        if (active != null) {
            val npc = existingNpc(level.server, active.id)
            if (npc == null && NpcStore.isDead(active.id)) return spawnNpc(level, active, pos, markActiveCamper = true)
            return false
        }
        val cooldownUntil = NpcStore.camperCooldownUntilTick()
        if (cooldownUntil > level.dayTime) return false
        val definition = randomEligibleCamper(level) ?: return false
        return spawnNpc(level, definition, pos, markActiveCamper = true, announceCamperArrival = true)
    }

    fun interact(player: ServerPlayer, npc: ChowNpcEntity) {
        val definition = NpcConfig.get(npc.npcId) ?: return
        val validHome = validHomePos(npc.level(), definition.id)
        npc.homePos = validHome
        val hasHome = validHome != null
        val wasSleeping = npc.isSleeping
        val currentDay = NpcTime.day(player.level())
        val firstChatToday = NpcStore.markFirstChatIfNeeded(definition.id, player, currentDay)
        val friendship = if (firstChatToday) NpcStore.adjustFriendship(definition.id, player, FIRST_DAILY_CHAT_FRIENDSHIP_DELTA, "first_daily_chat") else NpcStore.friendshipSnapshot(definition.id, player)
        if (wasSleeping) npc.stopSleeping()
        val contractGranted = definition.housing.canMoveIn && !hasHome && !hasRentContract(player, definition.id)
        if (contractGranted) {
            giveStack(player, createRentContract(definition.id))
            NpcStore.markContractGiven(definition.id)
            SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "RENT CONTRACT RECEIVED", "Use it on a bed to give ${definition.name} a home.", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        }
        val camperReason = if (!hasHome) NpcStore.camperReturnReason(definition.id) else ""
        val message = if (wasSleeping) {
            friendshipMessage(definition.friendshipMessages.wake, friendship, player, definition)
        } else if (hasHome && firstChatToday) {
            friendshipMessage(definition.friendshipMessages.firstDailyChat, friendship, player, definition)
        } else if (hasHome) {
            friendshipMessage(definition.friendshipMessages.interact, friendship, player, definition)
        } else if (camperReason == "lost_house") {
            camperMessage(definition.camperMessages.lostHouseDialog, player, definition)
        } else {
            camperMessage(definition.camperMessages.needsHouseDialog, player, definition)
        }
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "interacts with ${definition.name}", "player_interact")
        val settings = NpcConfig.settings()
        val llmInput = when {
            wasSleeping && settings.llmMessageUsage.wake -> "${player.gameProfile.name} woke you up. Reply naturally as ${definition.name}, with the context that you were just sleeping."
            hasHome && firstChatToday && settings.llmMessageUsage.firstDailyChat -> "${player.gameProfile.name} is talking to you for the first time today. Reply like a natural first daily greeting."
            hasHome && settings.llmMessageUsage.interact && !contractGranted -> "${player.gameProfile.name} interacted with you. Reply like a natural short NPC greeting or acknowledgement for this moment."
            !hasHome && camperReason == "lost_house" && settings.llmMessageUsage.camperLostHouse -> settings.campers.lostHouseLlmPrompt
            !hasHome && settings.llmMessageUsage.camperNeedsHouse -> settings.campers.needsHouseLlmPrompt
            else -> null
        }
        if (settings.llm.enabled && llmInput != null) {
            val responseToken = NpcDialogTokens.next()
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", contractGranted, friendship.level, closeOnly = contractGranted, closeLabel = if (contractGranted) "OKAY" else "BYE", responseToken = responseToken))
            NpcLlmService.event(player, npc, definition, message, llmInput, npcRecordType = "npc_llm_interact", responseToken = responseToken)
            return
        }
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_message")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, contractGranted, friendship.level, closeOnly = contractGranted, closeLabel = if (contractGranted) "OKAY" else "BYE"))
        relayNpcDialog(player, npc, definition, message)
    }

    fun handleDialogAction(player: ServerPlayer, npcId: String, action: String) {
        val definition = NpcConfig.get(npcId) ?: return
        val npc = existingNpc(player.server, definition.id) ?: return
        if (npc.level() != player.level() || player.distanceToSqr(npc) > NPC_DIALOG_ACTION_DISTANCE_SQR) return
        when (action.lowercase()) {
            "cancel_llm" -> NpcLlmService.cancel(player, definition.id)
            "buy" -> {
                NpcLlmService.cancel(player, definition.id)
                openNpcShop(player, npc, definition)
            }
            "gift" -> {
                NpcLlmService.cancel(player, definition.id)
                giftToNpc(player, npc, definition)
            }
        }
    }

    private fun openNpcShop(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val currentHour = NpcTime.hour(player.level())
        if (NpcTime.activityAt(definition.schedule, player.level()) != "work") {
            val friendship = NpcStore.friendshipSnapshot(definition.id, player)
            val nextOpen = nextWorkOpening(definition, currentHour)
            val fallback = if (nextOpen == null) "My shop is closed right now." else "My shop is closed right now. Come back around ${nextOpen.toString().padStart(2, '0')}:00."
            val responseToken = NpcDialogTokens.next()
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
            if (NpcConfig.settings().llm.enabled) {
                NpcLlmService.event(
                    player,
                    npc,
                    definition,
                    fallback,
                    "${player.gameProfile.name} tried to open your shop, but you are not working right now. Current hour is ${currentHour.toString().padStart(2, '0')}:00. Your next work opening is ${nextOpen?.toString()?.padStart(2, '0') ?: "unknown"}:00. Reply in-character and tell them when to expect the shop to open.",
                    npcRecordType = "npc_shop_closed",
                    responseToken = responseToken,
                )
            } else {
                NpcNetwork.sendTalkResponse(player, definition.id, fallback, responseToken)
                NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_shop_closed")
            }
            return
        }
        val storeId = definition.storeId().lowercase()
        if (storeId.isBlank() || !StoreShopFeature.openStore(player, storeId, definition.storeStockKey(), "${definition.name}'s stock")) {
            npcSnackbar(player, definition.name, "No shop ready.", SnackbarType.ERROR)
            pendingShopNpcs.remove(player.uuid)
            return
        }
        pendingShopNpcs[player.uuid] = definition.id
    }

    private fun nextWorkOpening(definition: NpcDefinition, currentHour: Int): Int? {
        val workStarts = definition.schedule.activities.filter { entry -> entry.activity == "work" }.map { entry -> entry.fromHour }
        if (workStarts.isEmpty()) return null
        return workStarts.filter { hour -> hour > currentHour }.minOrNull() ?: workStarts.minOrNull()
    }

    fun onStorePurchase(player: ServerPlayer, storeId: String, stockKey: String, quantity: Int, itemName: String, totalCost: Long) {
        val normalizedStoreId = storeId.lowercase()
        val normalizedStockKey = stockKey.lowercase()
        val definition = pendingShopNpcs.remove(player.uuid)
            ?.let(NpcConfig::get)
            ?.takeIf { npc -> npc.storeId().equals(normalizedStoreId, ignoreCase = true) }
            ?: NpcConfig.all().firstOrNull { npc -> npc.storeId().equals(normalizedStoreId, ignoreCase = true) && npc.storeStockKey().equals(normalizedStockKey, ignoreCase = true) }
            ?: return
        val npc = existingNpc(player.server, definition.id) ?: return
        if (npc.level() != player.level()) return
        if (player.distanceToSqr(npc) > NPC_DIALOG_ACTION_DISTANCE_SQR) return
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val message = friendshipMessage(definition.shopMessages.forQuantity(quantity), friendship, player, definition, itemName, quantity = quantity, totalCost = totalCost)
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "buys $quantity $itemName from ${definition.name}", "player_shop_buy")
        if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.shop) {
            val responseToken = NpcDialogTokens.next()
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
            NpcLlmService.event(
                player,
                npc,
                definition,
                message,
                "${player.gameProfile.name} bought $quantity x $itemName from your shop for $totalCost chowcoins. Reply with a short in-character shop follow-up.",
                npcRecordType = "npc_shop_message",
                responseToken = responseToken,
            )
            return
        }
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_shop_message")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
    }

    private fun giftToNpc(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val stack = giftStack(player)
        if (stack.isEmpty) {
            if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.gift) {
                val friendship = NpcStore.friendshipSnapshot(definition.id, player)
                val fallback = "Hold an item if you want to give me something."
                val responseToken = NpcDialogTokens.next()
                NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
                NpcLlmService.event(
                    player,
                    npc,
                    definition,
                    fallback,
                    "${player.gameProfile.name} tried to gift you, but they are not holding an item. Reply naturally and tell them they need to hold the gift item.",
                    npcRecordType = "npc_gift_unavailable",
                    responseToken = responseToken,
                )
                return
            }
            npcSnackbar(player, definition.name, "Hold an item to gift.", SnackbarType.ERROR)
            return
        }
        if (RelicRouletteFeature.rejectTransfer(player, stack, "gifts")) return
        val limit = definition.gifts.dailyLimit
        val period = NpcTime.periodForReset(player.level().dayTime, definition.gifts.resetHour)
        if (!NpcStore.recordGiftIfAllowed(definition.id, player, period, limit)) {
            if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.gift) {
                val friendship = NpcStore.friendshipSnapshot(definition.id, player)
                val fallback = "I can't receive another gift today. Come back after ${definition.gifts.resetHour.toString().padStart(2, '0')}:00."
                val responseToken = NpcDialogTokens.next()
                NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
                NpcLlmService.event(
                    player,
                    npc,
                    definition,
                    fallback,
                    "${player.gameProfile.name} tried to gift you ${stack.hoverName.string}, but your gift limit for today is already reached. Your next gift reset is ${definition.gifts.resetHour.toString().padStart(2, '0')}:00. Reply with a good in-character reason that you cannot receive another gift today.",
                    npcRecordType = "npc_gift_unavailable",
                    responseToken = responseToken,
                )
                return
            }
            npcSnackbar(player, definition.name, "Can receive another gift at ${definition.gifts.resetHour.toString().padStart(2, '0')}:00.", SnackbarType.ERROR)
            return
        }
        val itemName = stack.hoverName.string
        val mood = giftMood(definition, stack)
        val friendship = NpcStore.adjustFriendship(definition.id, player, giftFriendshipDelta(mood), "gift_$mood")
        val message = friendshipMessage(definition.friendshipMessages.gift, friendship, player, definition, itemName, mood)
        if (!player.abilities.instabuild) stack.shrink(1)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "gifts $itemName to ${definition.name}", "player_gift")
        if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.gift) {
            val responseToken = NpcDialogTokens.next()
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
            NpcLlmService.event(
                player,
                npc,
                definition,
                message,
                "${player.gameProfile.name} gifted you $itemName. Gift mood is $mood. Reply with a short in-character gift reaction.",
                npcRecordType = "npc_gift_$mood",
                responseToken = responseToken,
            )
            return
        }
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

    private fun dialogPayload(definition: NpcDefinition, npc: ChowNpcEntity, message: String, contractGranted: Boolean, friendshipLevel: Int, closeOnly: Boolean = false, closeLabel: String = "BYE", responseToken: Long = 0L): NpcDialogPayload = NpcDialogPayload(
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
        talkEnabled = NpcConfig.settings().llm.enabled,
        responseToken = responseToken,
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

    private fun camperMessage(pool: List<String>, player: ServerPlayer, definition: NpcDefinition): String {
        val messages = pool.ifEmpty { listOf("I need a bed before I can settle in.") }
        val selected = messages[player.random.nextInt(messages.size)]
        return selected
            .replace("{player}", player.gameProfile.name)
            .replace("{npc}", definition.name)
    }

    private fun relayNpcDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, message: String) {
        val level = npc.level() as? ServerLevel ?: return
        sendNpcBalloon(level, npc, message, excludePlayer = player.uuid)
        relayNpcDialogToDiscord(player, definition, message)
    }

    fun relayNpcDialogToDiscord(player: ServerPlayer, definition: NpcDefinition, message: String) {
        DiscordRelay.npcDialog(player, definition.id, definition.name, message)
    }

    private fun relayNpcHurtMessage(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val message = friendshipMessage(definition.friendshipMessages.hurt, friendship, player, definition)
        if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.hurt) {
            NpcLlmService.event(
                player,
                npc,
                definition,
                message,
                "${player.gameProfile.name} hurt you. Reply with a short in-character hurt reaction that matches your current health and relationship.",
                sendTalkResponse = false,
                excludePlayerFromBalloon = false,
                npcRecordType = "npc_hurt_message",
            )
            return
        }
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_hurt_message")
        val level = npc.level() as? ServerLevel ?: return
        sendNpcBalloon(level, npc, message)
    }

    fun showBalloonToNearby(level: ServerLevel, npc: ChowNpcEntity, message: String, durationTicks: Int = 90, excludePlayer: UUID? = null): Int = sendNpcBalloon(level, npc, message, durationTicks, excludePlayer)

    private fun sendNpcBalloon(level: ServerLevel, npc: ChowNpcEntity, message: String, durationTicks: Int = 90, excludePlayer: UUID? = null): Int {
        var recipients = 0
        level.players().forEach { listener ->
            if (listener.uuid != excludePlayer && listener.distanceToSqr(npc.x, npc.y, npc.z) <= NPC_DIALOG_HEAR_RADIUS * NPC_DIALOG_HEAR_RADIUS) {
                NpcNetwork.showBalloon(listener, npc.id, message, durationTicks)
                recipients++
            }
        }
        return recipients
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
        val npc = existingNpc(player.server, npcId)
        if (npc == null || npc.level() != player.level() || npc.distanceToSqr(homePos.x + 0.5, homePos.y.toDouble(), homePos.z + 0.5) > CONTRACT_BED_ASSIGN_RADIUS_SQR) {
            npcSnackbar(player, definition.name, "${definition.name} needs to be near the bed.", SnackbarType.ERROR)
            return false
        }
        NpcStore.setHome(npcId, homePos)
        NpcStore.clearActiveCamper(npcId)
        scheduleNextCamper(player.level())
        npc.let { npc ->
            npc.homePos = homePos.immutable()
            npc.campPos = npc.campPos ?: NpcStore.campPos(npcId)
            npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
            val friendship = NpcStore.friendshipSnapshot(definition.id, player)
            val fallback = "Thank you, {player}. This bed feels like home already.".replace("{player}", player.gameProfile.name)
            val responseToken = NpcDialogTokens.next()
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
            if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.assignedHouse) {
                NpcLlmService.event(
                    player,
                    npc,
                    definition,
                    fallback,
                    NpcConfig.settings().campers.assignedHouseLlmPrompt,
                    npcRecordType = "npc_assigned_house",
                    responseToken = responseToken,
                )
            } else {
                NpcNetwork.sendTalkResponse(player, definition.id, fallback, responseToken)
                NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_assigned_house")
                relayNpcDialog(player, npc, definition, fallback)
            }
        }
        if (!player.abilities.instabuild) stack.shrink(1)
        SnackbarNetwork.send(player, SnackbarNotification.item(BuiltInRegistries.ITEM.getKey(RENT_CONTRACT.get()).toString(), "HOME ASSIGNED", "${definition.name} now lives here", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return true
    }

    fun tickNpc(entity: ChowNpcEntity) {
        val definition = NpcConfig.get(entity.npcId) ?: return
        entity.homePos = validHomePos(entity.level(), definition.id)
        entity.campPos = entity.campPos ?: NpcStore.campPos(definition.id)
        if (NpcBrainOverrides.tick(entity, definition)) return
        if (tryFollowRentContractHolder(entity, definition)) return
        if (tryGreetNearbyPlayer(entity, definition)) return
        if (entity.isTalking()) return
        NpcBrain.tick(entity, definition)
    }

    private fun tryFollowRentContractHolder(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        if (npc.homePos != null) return false
        val level = npc.level() as? ServerLevel ?: return false
        val holder = level.players()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc) <= CONTRACT_FOLLOW_SCAN_RADIUS_SQR }
            .firstOrNull { player -> playerHoldsRentContract(player, definition.id) }
            ?: return false
        if (npc.isSleeping) npc.stopSleeping()
        npc.debugActivity = "camp"
        npc.debugGoal = "follow_contract"
        npc.debugTargetPos = holder.blockPosition().immutable()
        npc.lookControl.setLookAt(holder, 30.0f, 30.0f)
        if (npc.distanceToSqr(holder) > CONTRACT_FOLLOW_STOP_DISTANCE_SQR) {
            npc.navigation.moveTo(holder.x, holder.y, holder.z, 0.95)
        } else {
            npc.navigation.stop()
        }
        return true
    }

    private fun playerHoldsRentContract(player: ServerPlayer, npcId: String): Boolean {
        return NpcRentContractData.readNpcId(player.mainHandItem) == npcId || NpcRentContractData.readNpcId(player.offhandItem) == npcId
    }

    private fun tryGreetNearbyPlayer(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val level = npc.level() as? ServerLevel ?: return false
        val greeting = NpcConfig.settings().greetings
        val radiusSqr = greeting.radius * greeting.radius
        val playersInRadius = level.players()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc.x, npc.y, npc.z) <= radiusSqr }
        updateGreetingRadiusState(npc, definition, playersInRadius)
        if (npc.isTalking() || npc.isSleeping) return false
        val player = playersInRadius
            .asSequence()
            .minByOrNull { player -> player.distanceToSqr(npc.x, npc.y, npc.z) }
            ?: return false
        val day = NpcTime.day(level)
        val nowMs = System.currentTimeMillis()
        if (!NpcStore.canShowGreeting(definition.id, player, day, nowMs)) return false
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val message = friendshipMessage(definition.friendshipMessages.greeting, friendship, player, definition)
        val durationTicks = greeting.balloonDurationSeconds * 20
        NpcStore.markGreetingShown(definition.id, player, day, nowMs + greeting.cooldownSeconds * 1000L)
        if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.greeting) {
            NpcLlmService.event(
                player,
                npc,
                definition,
                message,
                "${player.gameProfile.name} walked near you. Reply with a very short ambient greeting balloon.",
                sendTalkResponse = false,
                excludePlayerFromBalloon = false,
                npcRecordType = "npc_greeting_balloon",
            )
            return true
        }
        sendNpcBalloon(level, npc, message, durationTicks)
        npc.startTalkingTo(player, durationTicks)
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_greeting_balloon")
        return true
    }

    private fun updateGreetingRadiusState(npc: ChowNpcEntity, definition: NpcDefinition, playersInRadius: List<ServerPlayer>) {
        val current = playersInRadius.map { player -> player.stringUUID }.toSet()
        val previous = greetingRadiusPlayers.getOrPut(npc.uuid) { linkedSetOf() }
        previous.filter { playerId -> playerId !in current }.forEach { playerId -> NpcStore.clearGreetingCooldown(definition.id, playerId) }
        previous.clear()
        previous.addAll(current)
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
        tickCamperSpawner(event.server)
        tickClockDebug(event.server)
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
            if (validHomePos(npc.level(), definition.id) == null) {
                val camp = npc.campPos ?: NpcStore.campBlockPos()
                if (camp != null) {
                    NpcStore.setActiveCamper(definition.id, camp)
                    NpcStore.markCamperReturnReason(definition.id, "")
                }
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
        val camp = NpcStore.campBlockPos()
        if (camp != null) {
            NpcStore.setActiveCamper(npcId, camp)
            NpcStore.markCamperReturnReason(npcId, "lost_house")
        }
        existingNpc(level.server, npcId)?.let { npc ->
            npc.homePos = null
            if (camp != null) {
                val definition = NpcConfig.get(npcId) ?: return@let
                val listener = level.players().firstOrNull()
                npc.campPos = camp.immutable()
                npc.navigation.stop()
                npc.moveTo(camp.x + 0.5, camp.y + 1.0, camp.z + 0.5, level.random.nextFloat() * 360.0f, 0.0f)
                if (listener != null) sendNpcBalloon(level, npc, camperMessage(definition.camperMessages.lostHouseBalloon, listener, definition), 120)
            }
        }
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
                    Commands.literal("clock")
                        .executes(::debugClockCommand),
                )
                .then(
                    Commands.literal("time")
                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 240)).executes(::debugTimeCommand)),
                )
                .then(
                    Commands.literal("balloon")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .suggests(::suggestNpcIds)
                                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(::debugBalloonCommand)),
                        ),
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
            Commands.literal("clear")
                .requires { source -> source.hasPermission(4) }
                .then(
                    Commands.literal("all")
                        .then(Commands.literal("confirm").executes(::clearAllNpcsCommand)),
                )
                .then(
                    Commands.argument("id", StringArgumentType.word())
                        .suggests(::suggestNpcIds)
                        .then(Commands.literal("confirm").executes(::clearNpcCommand)),
                ),
        )
        .then(
            Commands.literal("friendship")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("get")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .suggests(::suggestNpcIds)
                                .then(Commands.argument("player", EntityArgument.player()).executes(::friendshipGetCommand)),
                        ),
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .suggests(::suggestNpcIds)
                                .then(
                                    Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("points", IntegerArgumentType.integer(NpcFriendshipLevels.MIN_POINTS, NpcFriendshipLevels.MAX_POINTS)).executes(::friendshipSetCommand)),
                                ),
                        ),
                )
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .suggests(::suggestNpcIds)
                                .then(
                                    Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("delta", IntegerArgumentType.integer(-2000, 2000)).executes(::friendshipAddCommand)),
                                ),
                        ),
                )
        )

    private fun suggestNpcIds(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(NpcConfig.all().map { definition -> definition.id }, builder)

    private fun reloadCommand(context: CommandContext<CommandSourceStack>): Int {
        ChowClockConfig.load()
        NpcConfig.load()
        context.source.sendSuccess({ Component.literal("Reloaded ${NpcConfig.all().size} NPC definition(s). Clock source: ${ChowClockConfig.sourceName()}.") }, true)
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
            Component.literal("camp=$camp home=$home store=${definition?.storeId().orEmpty().ifBlank { "none" }}"),
            Component.literal("Actionbar will update live. Run /npc debug on the same NPC again to stop.").withStyle(ChatFormatting.DARK_GRAY),
        ).forEach(player::sendSystemMessage)
        return 1
    }

    private fun debugClockCommand(context: CommandContext<CommandSourceStack>): Int {
        ChowClockConfig.load()
        val player = context.source.playerOrException
        if (!clockDebugPlayers.add(player.uuid)) {
            clockDebugPlayers.remove(player.uuid)
            player.sendSystemMessage(Component.literal("CK clock debug disabled.").withStyle(ChatFormatting.GRAY))
            return 1
        }
        player.sendSystemMessage(Component.literal("CK CLOCK DEBUG: ${clockDebugLine(player.level())}").withStyle(ChatFormatting.GOLD))
        player.sendSystemMessage(Component.literal("Actionbar will update live. Run /npc debug clock again to stop.").withStyle(ChatFormatting.DARK_GRAY))
        return 1
    }

    private fun debugTimeCommand(context: CommandContext<CommandSourceStack>): Int {
        debugTimeMultiplier = IntegerArgumentType.getInteger(context, "multiplier")
        val message = if (debugTimeMultiplier == 1) "NPC debug time speed reset." else "NPC debug time speed set to ${debugTimeMultiplier}x."
        context.source.sendSuccess({ Component.literal(message) }, true)
        return debugTimeMultiplier
    }

    private fun debugBalloonCommand(context: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(context, "id")
        val message = StringArgumentType.getString(context, "message")
        val definition = NpcConfig.get(id) ?: run {
            context.source.sendFailure(Component.literal("Unknown NPC '$id'."))
            return 0
        }
        val npc = existingNpc(context.source.server, id) ?: run {
            context.source.sendFailure(Component.literal("${definition.displayName()} is not currently spawned."))
            return 0
        }
        val level = npc.level() as? ServerLevel ?: return 0
        val recipients = sendNpcBalloon(level, npc, message, 120)
        if (recipients == 0) {
            context.source.sendFailure(Component.literal("No players are within ${NPC_DIALOG_HEAR_RADIUS.toInt()} blocks of ${definition.displayName()}."))
            return 0
        }
        context.source.sendSuccess({ Component.literal("Sent ${definition.displayName()} balloon to $recipients nearby player(s).") }, true)
        return recipients
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
        val now = NpcTime.at(dayTime)
        val currentDay = now.day
        val currentHour = now.hour
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

    private fun clearAllNpcsCommand(context: CommandContext<CommandSourceStack>): Int {
        val server = context.source.server
        val removedEntities = removeLiveNpcs(server, null)
        val removedContracts = removeRentContracts(server, null)
        val backup = NpcStore.backupAndClearAll()
        realtimeDebugTargets.clear()
        greetingRadiusPlayers.clear()
        context.source.sendSuccess({
            Component.literal("DANGER: cleared all NPC state, $removedEntities live NPC(s), and $removedContracts rent contract(s). Backup: $backup").withStyle(ChatFormatting.RED)
        }, true)
        return removedEntities
    }

    private fun clearNpcCommand(context: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(context, "id")
        val definition = NpcConfig.get(id)
        if (definition == null) {
            context.source.sendFailure(Component.literal("Unknown NPC '$id'."))
            return 0
        }
        val server = context.source.server
        val removedEntities = removeLiveNpcs(server, id)
        val removedContracts = removeRentContracts(server, id)
        val backup = NpcStore.backupAndClearNpc(id)
        realtimeDebugTargets.clear()
        greetingRadiusPlayers.clear()
        context.source.sendSuccess({
            Component.literal("DANGER: cleared ${definition.displayName()} state, $removedEntities live NPC(s), and $removedContracts rent contract(s). Backup: $backup").withStyle(ChatFormatting.RED)
        }, true)
        return removedEntities
    }

    private fun removeLiveNpcs(server: MinecraftServer, npcId: String?): Int {
        var removed = 0
        server.allLevels.forEach { level ->
            level.getEntities(NPC_ENTITY.get()) { npc -> npcId == null || npc.npcId == npcId }.forEach { entity ->
                NpcBrainOverrides.clear(entity)
                entity.discard()
                removed++
            }
        }
        return removed
    }

    private fun removeRentContracts(server: MinecraftServer, npcId: String?): Int {
        var removed = 0
        server.playerList.players.forEach { player ->
            for (slot in 0 until player.inventory.containerSize) {
                val stack = player.inventory.getItem(slot)
                val contractNpcId = NpcRentContractData.readNpcId(stack)
                if (contractNpcId.isNotBlank() && (npcId == null || contractNpcId == npcId)) {
                    removed += stack.count
                    player.inventory.setItem(slot, ItemStack.EMPTY)
                }
            }
        }
        return removed
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
        NpcStore.deadNpcIds().forEach { npcId ->
            if (!respawnReady(dayTime, NpcStore.respawnDay(npcId))) return@forEach
            val definition = NpcConfig.get(npcId) ?: return@forEach
            if (existingNpc(server, npcId) != null) {
                NpcStore.clearDead(npcId)
                return@forEach
            }
            if (NpcStore.homePos(npcId) == null) {
                val camp = NpcStore.campBlockPos() ?: NpcStore.campPos(npcId) ?: return@forEach
                spawnNpc(server.overworld(), definition, camp, markActiveCamper = true)
                return@forEach
            }
            val home = validHomePos(server.overworld(), npcId) ?: return@forEach
            respawnNpc(server.overworld(), definition, home)
        }
    }

    private fun tickCamperSpawner(server: MinecraftServer) {
        if (server.tickCount % RESPAWN_SCAN_INTERVAL_TICKS != 0) return
        val camp = NpcStore.campBlockPos() ?: return
        val level = server.overworld()
        if (!level.getBlockState(camp).`is`(CAMPING_BLOCK.get())) return
        if (activeUnhousedCamper(server) ?: migrateLiveUnhousedCamper(server) != null) return
        val cooldownUntil = NpcStore.camperCooldownUntilTick()
        if (cooldownUntil > level.dayTime) return
        val definition = randomEligibleCamper(level) ?: return
        spawnNpc(level, definition, camp, markActiveCamper = true, announceCamperArrival = true)
    }

    private fun activeUnhousedCamper(server: MinecraftServer): NpcDefinition? {
        val activeId = NpcStore.activeCamperId().ifBlank { return null }
        val definition = NpcConfig.get(activeId) ?: return null
        if (validHomePos(server.overworld(), activeId) != null) {
            NpcStore.clearActiveCamper(activeId)
            return null
        }
        return definition
    }

    private fun migrateLiveUnhousedCamper(server: MinecraftServer): NpcDefinition? {
        NpcConfig.all().forEach { definition ->
            val npc = existingNpc(server, definition.id) ?: return@forEach
            if (validHomePos(npc.level(), definition.id) != null) return@forEach
            val camp = npc.campPos ?: NpcStore.campBlockPos() ?: npc.blockPosition()
            NpcStore.setActiveCamper(definition.id, camp)
            npc.campPos = camp.immutable()
            return definition
        }
        return null
    }

    private fun randomEligibleCamper(level: ServerLevel): NpcDefinition? {
        val candidates = NpcConfig.all()
            .filter { definition -> definition.housing.canMoveIn }
            .filter { definition -> NpcStore.homePos(definition.id) == null }
            .filter { definition -> existingNpc(level.server, definition.id) == null || NpcStore.isDead(definition.id) }
        if (candidates.isEmpty()) return null
        return candidates[level.random.nextInt(candidates.size)]
    }

    private fun scheduleNextCamper(level: Level) {
        val settings = NpcConfig.settings().campers
        val hours = if (settings.cooldownMinHours == settings.cooldownMaxHours) {
            settings.cooldownMinHours
        } else {
            settings.cooldownMinHours + level.random.nextInt(settings.cooldownMaxHours - settings.cooldownMinHours + 1)
        }
        NpcStore.setCamperCooldown(NpcTime.addHours(level.dayTime, hours))
    }

    private fun respawnReady(dayTime: Long, respawnDay: Long): Boolean {
        return NpcTime.readyAtOrAfterHour(dayTime, respawnDay, NPC_RESPAWN_HOUR)
    }

    private fun nextRespawnDay(dayTime: Long): Long {
        return NpcTime.nextDayAtHour(dayTime, NPC_RESPAWN_HOUR)
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

    private fun tickClockDebug(server: MinecraftServer) {
        if (server.tickCount % REALTIME_DEBUG_INTERVAL_TICKS != 0) return
        val iterator = clockDebugPlayers.iterator()
        while (iterator.hasNext()) {
            val playerId = iterator.next()
            val player = server.playerList.getPlayer(playerId) ?: run {
                iterator.remove()
                continue
            }
            player.displayClientMessage(Component.literal(clockDebugLine(player.level())).withStyle(ChatFormatting.YELLOW), true)
        }
    }

    private fun clockDebugLine(level: Level): String {
        val clock = NpcTime.at(level.dayTime)
        val daylight = level.gameRules.getBoolean(GameRules.RULE_DAYLIGHT)
        val speed = if (debugTimeMultiplier > 1) " debug=${debugTimeMultiplier}x" else ""
        return "time=${clock.displayTime()} hour=${clock.hour.toString().padStart(2, '0')} day=${clock.day} raw=${clock.rawDayTime} tick=${clock.tickOfCycle} source=${ChowClockConfig.sourceName()} daylight=$daylight$speed"
    }

    private fun realtimeDebugLine(npc: ChowNpcEntity): String {
        val nav = if (npc.navigation.isDone) "idle" else "moving"
        val target = npc.debugTargetPos?.toShortString() ?: "none"
        val clock = NpcTime.at(npc.level().dayTime)
        val time = if (debugTimeMultiplier > 1) " time=${debugTimeMultiplier}x" else ""
        return "${npc.npcId} | ${clock.displayTime()} tick=${clock.tickOfCycle} activity=${npc.debugActivity} task=${npc.debugGoal} nav=$nav target=$target$time"
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

    private fun spawnNpc(level: ServerLevel, definition: NpcDefinition, campPos: BlockPos, markActiveCamper: Boolean = false, announceCamperArrival: Boolean = false): Boolean {
        if (existingNpc(level.server, definition.id) != null) return false
        val npc = NPC_ENTITY.get().create(level) ?: return false
        val spawnPos = npcSpawnAroundCamp(level, campPos) ?: return false
        npc.configure(definition, campPos)
        npc.moveTo(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5, level.random.nextFloat() * 360.0f, 0.0f)
        level.addFreshEntity(npc)
        NpcStore.setEntity(definition.id, npc.uuid, campPos)
        NpcStore.clearDead(definition.id)
        if (markActiveCamper) {
            NpcStore.setActiveCamper(definition.id, campPos)
            level.players().firstOrNull()?.let { player ->
                val message = camperMessage(definition.camperMessages.needsHouseBalloon, player, definition)
                sendNpcBalloon(level, npc, message, 120)
            }
            if (announceCamperArrival) announceCamperArrival(level, definition)
        }
        return true
    }

    private fun announceCamperArrival(level: ServerLevel, definition: NpcDefinition) {
        val notification = SnackbarNotification.npc(
            definition.id,
            "NEW CAMPER AT THE CAMPING BLOCK",
            "${definition.name} is looking for a place to stay. Talk to them to welcome them and offer a rent contract.",
            SnackbarType.SUCCESS,
            SnackbarSounds.REWARD,
        )
        SnackbarNetwork.sendToAllKnown(level.server, notification)
        DiscordRelay.npcCamperArrived(level.server, definition.id, definition.name)
    }

    private fun npcSpawnAroundCamp(level: ServerLevel, campPos: BlockPos): BlockPos? {
        val offsets = (-NPC_CAMP_SPAWN_RADIUS..NPC_CAMP_SPAWN_RADIUS).flatMap { dx ->
            (-NPC_CAMP_SPAWN_RADIUS..NPC_CAMP_SPAWN_RADIUS).map { dz -> dx to dz }
        }.filter { (dx, dz) -> dx != 0 || dz != 0 }
            .sortedWith(compareBy<Pair<Int, Int>> { (dx, dz) -> dx * dx + dz * dz }.thenBy { it.first }.thenBy { it.second })
        offsets.forEach { (dx, dz) ->
            val base = campPos.offset(dx, 0, dz)
            listOf(base, base.above(), base.below()).forEach { candidate ->
                if (canSpawnNpcAt(level, candidate)) return candidate
            }
        }
        return null
    }

    private fun canSpawnNpcAt(level: ServerLevel, pos: BlockPos): Boolean {
        val below = pos.below()
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty
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

    fun existingNpc(server: MinecraftServer, npcId: String): ChowNpcEntity? {
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
    private const val NPC_CAMP_SPAWN_RADIUS = 2
    private const val CONTRACT_FOLLOW_SCAN_RADIUS_SQR = 32.0 * 32.0
    private const val CONTRACT_FOLLOW_STOP_DISTANCE_SQR = 2.5 * 2.5
    private const val CONTRACT_BED_ASSIGN_RADIUS_SQR = 7.0 * 7.0
    private const val FRIENDSHIP_HIT_DELTA = -10
    private const val FRIENDSHIP_KILL_DELTA = -300
    private const val FIRST_DAILY_CHAT_FRIENDSHIP_DELTA = 25
    private const val RESPAWN_SCAN_INTERVAL_TICKS = 20
    private const val NPC_RESPAWN_HOUR = 5
}
