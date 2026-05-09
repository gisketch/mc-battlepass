package dev.gisketch.chowkingdom.npc

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowClockConfig
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordRelay
import dev.gisketch.chowkingdom.relicroulette.RelicRouletteFeature
import dev.gisketch.chowkingdom.roles.PerformerPerks
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
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
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
    private const val NPC_DIALOG_KEEPALIVE_TICKS = 80
    private val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(Registries.BLOCK, ChowKingdomMod.MOD_ID)
    private val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ChowKingdomMod.MOD_ID)
    private val ENTITIES: DeferredRegister<EntityType<*>> = DeferredRegister.create(Registries.ENTITY_TYPE, ChowKingdomMod.MOD_ID)
    private val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChowKingdomMod.MOD_ID)
    private val realtimeDebugTargets: MutableMap<UUID, UUID> = linkedMapOf()
    private val clockDebugPlayers: MutableSet<UUID> = linkedSetOf()
    private val greetingRadiusPlayers: MutableMap<UUID, MutableSet<String>> = linkedMapOf()
    private val outgoingGiftApproaches: MutableMap<UUID, NpcOutgoingGiftApproach> = linkedMapOf()
    private val camperBalloonRefreshAt: MutableMap<UUID, Long> = linkedMapOf()
    private val questClaimBalloonRefreshAt: MutableMap<UUID, Long> = linkedMapOf()
    private val npcMicroInteractions: MutableMap<UUID, ActiveNpcMicroInteraction> = linkedMapOf()
    private val npcMicroInteractionCooldownUntil: MutableMap<String, Long> = linkedMapOf()
    private val npcAutoTaskCooldownUntil: MutableMap<UUID, Long> = linkedMapOf()
    private val pendingShopNpcs: MutableMap<UUID, String> = linkedMapOf()
    private var debugTimeMultiplier: Int = 1

    val CAMPING_BLOCK: DeferredHolder<Block, CampingBlock> = BLOCKS.register("camping_block", Supplier { CampingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5f).noOcclusion()) })
    val CAMPING_BLOCK_ITEM: DeferredHolder<Item, BlockItem> = ITEMS.register("camping_block", Supplier { BlockItem(CAMPING_BLOCK.get(), Item.Properties()) })
    val TOWN_CENTER_BLOCK: DeferredHolder<Block, TownCenterBlock> = BLOCKS.register("town_center_block", Supplier { TownCenterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).strength(1.5f).noOcclusion()) })
    val TOWN_CENTER_BLOCK_ITEM: DeferredHolder<Item, BlockItem> = ITEMS.register("town_center_block", Supplier { BlockItem(TOWN_CENTER_BLOCK.get(), Item.Properties()) })
    val CAMPING_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<CampingBlockEntity>> = BLOCK_ENTITIES.register(
        "camping_block",
        Supplier { BlockEntityType.Builder.of(::CampingBlockEntity, CAMPING_BLOCK.get()).build(null) },
    )
    val RENT_CONTRACT: DeferredHolder<Item, NpcRentContractItem> = ITEMS.register("rent_contract", Supplier { NpcRentContractItem(Item.Properties().stacksTo(1)) })
    val JOB_APPLICATION: DeferredHolder<Item, NpcJobApplicationItem> = ITEMS.register("job_application", Supplier { NpcJobApplicationItem(Item.Properties().stacksTo(1)) })
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
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onServerChat)
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
        val activeTalk = NpcLlmService.talkSnapshot(definition.id)
        if (activeTalk != null) {
            val friendship = NpcStore.friendshipSnapshot(definition.id, player)
            val otherNames = activeTalk.participants.filterNot { participant -> participant.uuid == player.uuid }.joinToString(", ") { participant -> participant.name }
            val message = if (activeTalk.contains(player.uuid)) {
                if (otherNames.isBlank()) "You are already talking with ${definition.name}." else "You are in the conversation with $otherNames."
            } else {
                val names = activeTalk.participants.joinToString(", ") { participant -> participant.name }
                "${definition.name} is currently talking to $names."
            }
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, false, friendship.level, dialogMode = "join", startTalkMode = activeTalk.contains(player.uuid)))
            return
        }
        val pendingGift = NpcStore.pendingOutgoingGift(definition.id, player)
        if (pendingGift != null) {
            claimOutgoingGift(player, npc, definition, pendingGift)
            return
        }
        if (NpcQuestService.tryOpenQuest(player, npc, definition)) return
        val wasSleeping = npc.isSleeping
        val currentDay = NpcTime.day(player.level())
        val firstChatToday = NpcStore.markFirstChatIfNeeded(definition.id, player, currentDay)
        val friendship = if (firstChatToday) NpcStore.adjustFriendship(definition.id, player, FIRST_DAILY_CHAT_FRIENDSHIP_DELTA, "first_daily_chat") else NpcStore.friendshipSnapshot(definition.id, player)
        if (firstChatToday) showFriendshipDelta(npc.level() as? ServerLevel, npc, FIRST_DAILY_CHAT_FRIENDSHIP_DELTA)
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
        DiscordRelay.npcInteraction(player, definition.name)
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
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", contractGranted, friendship.level, closeOnly = contractGranted, closeLabel = if (contractGranted) "OKAY" else "BYE", responseToken = responseToken, friendshipDelta = if (firstChatToday) FIRST_DAILY_CHAT_FRIENDSHIP_DELTA else 0))
            NpcLlmService.event(player, npc, definition, message, llmInput, npcRecordType = "npc_llm_interact", responseToken = responseToken)
            return
        }
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_message")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, contractGranted, friendship.level, closeOnly = contractGranted, closeLabel = if (contractGranted) "OKAY" else "BYE", friendshipDelta = if (firstChatToday) FIRST_DAILY_CHAT_FRIENDSHIP_DELTA else 0))
        relayNpcDialog(player, npc, definition, message)
    }

    fun handleDialogAction(player: ServerPlayer, npcId: String, action: String) {
        val definition = NpcConfig.get(npcId) ?: return
        val npc = existingNpc(player.server, definition.id) ?: return
        if (npc.level() != player.level() || player.distanceToSqr(npc) > NPC_DIALOG_ACTION_DISTANCE_SQR) return
        when (action.lowercase()) {
            "dialog_keepalive" -> {
                npc.continueTalkingTo(player, NPC_DIALOG_KEEPALIVE_TICKS)
                return
            }
            "dialog_close" -> {
                npc.stopTalkingTo(player)
                return
            }
        }
        if (NpcQuestService.handleAction(player, npc, definition, action)) return
        when (action.lowercase()) {
            "cancel_llm", "leave_llm_dialog" -> NpcLlmService.leaveDialog(player, definition.id)
            "join_talk" -> NpcLlmService.joinConversation(player, definition.id)
            "buy" -> {
                NpcLlmService.leaveDialog(player, definition.id)
                openNpcShop(player, npc, definition)
            }
            "gift" -> {
                NpcLlmService.leaveDialog(player, definition.id)
                giftToNpc(player, npc, definition)
            }
            "work" -> {
                NpcLlmService.leaveDialog(player, definition.id)
                openWorkDialog(player, npc, definition)
            }
            "work_move" -> {
                NpcLlmService.leaveDialog(player, definition.id)
                giveWorkApplication(player, npc, definition, moving = true)
            }
            "work_fire" -> {
                NpcLlmService.leaveDialog(player, definition.id)
                fireNpcWorkplace(player, npc, definition)
            }
        }
    }

    private fun openWorkDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val workplace = NpcStore.workplacePos(definition.id)
        if (workplace == null) {
            giveWorkApplication(player, npc, definition, moving = false)
            return
        }
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        val fallback = "My workplace is at ${workplace.toShortString()}. Move it, or fire me from this post?"
        val responseToken = NpcDialogTokens.next()
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, if (workLlmEnabled { it.workManage }) "..." else fallback, false, friendship.level, responseToken = responseToken, dialogMode = "work"))
        if (workLlmEnabled { it.workManage }) {
            NpcLlmService.event(player, npc, definition, fallback, NpcConfig.settings().work.manageLlmPrompt.replace("{workplace}", workplace.toShortString()), npcRecordType = "npc_work_manage", responseToken = responseToken)
        }
    }

    private fun giveWorkApplication(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, moving: Boolean) {
        giveStack(player, createJobApplication(definition.id))
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val fallback = if (moving) {
            "Okay. Right-click the new work block with that application."
        } else {
            "Use this job application on my work block. I will work around it."
        }
        val llmEnabled = workLlmEnabled { usage -> if (moving) usage.workMove else usage.workApplication }
        val responseToken = NpcDialogTokens.next()
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, if (llmEnabled) "..." else fallback, false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
        if (llmEnabled) {
            val prompt = if (moving) NpcConfig.settings().work.moveLlmPrompt else NpcConfig.settings().work.applicationLlmPrompt
            NpcLlmService.event(player, npc, definition, fallback, prompt, npcRecordType = if (moving) "npc_work_move" else "npc_work_application", responseToken = responseToken)
        }
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "JOB APPLICATION RECEIVED", "Right-click a work block for ${definition.name}.", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
    }

    private fun fireNpcWorkplace(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        NpcStore.clearWorkplace(definition.id, fired = true)
        npc.navigation.stop()
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val fallback = "Okay. I am unemployed for now."
        val responseToken = NpcDialogTokens.next()
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, if (workLlmEnabled { it.workFire }) "..." else fallback, false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
        if (workLlmEnabled { it.workFire }) {
            NpcLlmService.event(player, npc, definition, fallback, NpcConfig.settings().work.fireLlmPrompt, npcRecordType = "npc_work_fire", responseToken = responseToken)
        }
        SnackbarNetwork.send(player, SnackbarNotification.npc(definition.id, "NPC FIRED", "${definition.name} has no workplace now.", SnackbarType.GENERIC, SnackbarSounds.GENERIC))
    }

    private fun workLlmEnabled(flag: (NpcLlmMessageUsageDefinition) -> Boolean): Boolean {
        val settings = NpcConfig.settings()
        return settings.llm.enabled && flag(settings.llmMessageUsage)
    }

    private fun openNpcShop(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val currentHour = NpcTime.hour(player.level())
        if (NpcStore.workplacePos(definition.id) == null) {
            val friendship = NpcStore.friendshipSnapshot(definition.id, player)
            val message = if (NpcStore.workFired(definition.id)) "I do not have a job right now." else "I need a workplace before I can open shop."
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
            return
        }
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
        val workplace = NpcStore.workplacePos(definition.id)
        if (workplace != null) {
            val workBlocks = workBlockStatus(player.level(), workplace, definition)
            if (!workBlocks.ready) {
                openMissingWorkBlocksDialog(player, npc, definition, workplace, workBlocks, assigning = false)
                return
            }
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

    private fun openMissingWorkBlocksDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, workplace: BlockPos, status: NpcWorkBlockStatus, assigning: Boolean) {
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val missing = formatMissingWorkBlocks(status)
        val requirements = formatRequiredWorkBlocks(status)
        val fallback = if (assigning) {
            "I need $missing near this workplace before I can start working."
        } else {
            "I cannot open shop yet. I am missing $missing near my workplace."
        }
        val llmEnabled = workLlmEnabled { usage -> usage.workMissingBlocks }
        val responseToken = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, if (llmEnabled) "..." else fallback, false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
        if (llmEnabled) {
            val prompt = NpcConfig.settings().work.missingWorkBlocksLlmPrompt
                .replace("{missing}", missing)
                .replace("{requirements}", requirements)
                .replace("{workplace}", workplace.toShortString())
                .replace("{action}", if (assigning) "assigning a new workplace" else "opening shop during work hours")
            NpcLlmService.event(player, npc, definition, fallback, prompt, npcRecordType = "npc_work_blocks_missing", responseToken = responseToken)
        } else {
            NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_work_blocks_missing")
        }
        npcSnackbar(player, definition.name, "Missing: $missing", SnackbarType.ERROR)
    }

    private fun workBlockStatus(level: Level, center: BlockPos, definition: NpcDefinition): NpcWorkBlockStatus {
        val counts = definition.workBlocks.map { requirement ->
            NpcWorkBlockCount(requirement, countWorkBlocks(level, center, definition, requirement) + countWorkEntities(level, center, definition, requirement))
        }
        return NpcWorkBlockStatus(counts)
    }

    private fun countWorkBlocks(level: Level, center: BlockPos, definition: NpcDefinition, requirement: NpcWorkBlockRequirementDefinition): Int {
        val radius = definition.jobDefinition.workScanRadius
        val yRadius = max(3, radius / 2)
        var count = 0
        val beds = linkedSetOf<BlockPos>()
        BlockPos.betweenClosed(center.offset(-radius, -yRadius, -radius), center.offset(radius, yRadius, radius)).forEach { mutablePos ->
            val pos = mutablePos.immutable()
            val state = level.getBlockState(pos)
            if (!matchesWorkBlock(state, requirement.id)) return@forEach
            if (state.`is`(BlockTags.BEDS)) beds += canonicalBedPos(level, pos) else count++
        }
        return count + beds.size
    }

    private fun countWorkEntities(level: Level, center: BlockPos, definition: NpcDefinition, requirement: NpcWorkBlockRequirementDefinition): Int {
        val radius = definition.jobDefinition.workScanRadius.toDouble()
        val yRadius = max(3, definition.jobDefinition.workScanRadius / 2).toDouble()
        val box = AABB(center.x - radius, center.y - yRadius, center.z - radius, center.x + radius + 1.0, center.y + yRadius + 1.0, center.z + radius + 1.0)
        return level.getEntitiesOfClass(Entity::class.java, box) { entity -> matchesWorkEntity(entity.type, requirement.id) }.size
    }

    private fun matchesWorkBlock(state: BlockState, raw: String): Boolean {
        if (raw.startsWith("#")) {
            val id = runCatching { ResourceLocation.parse(raw.removePrefix("#")) }.getOrNull() ?: return false
            return state.`is`(TagKey.create(Registries.BLOCK, id))
        }
        return BuiltInRegistries.BLOCK.getKey(state.block).toString() == raw
    }

    private fun matchesWorkEntity(type: EntityType<*>, raw: String): Boolean {
        if (raw.startsWith("#")) {
            val id = runCatching { ResourceLocation.parse(raw.removePrefix("#")) }.getOrNull() ?: return false
            return type.`is`(TagKey.create(Registries.ENTITY_TYPE, id))
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString() == raw
    }

    private fun formatMissingWorkBlocks(status: NpcWorkBlockStatus): String = status.counts
        .filter { count -> count.missing > 0 }
        .joinToString(", ") { count -> "${count.missing} ${workBlockLabel(count.requirement)}" }
        .ifBlank { "nothing" }

    private fun formatRequiredWorkBlocks(status: NpcWorkBlockStatus): String = status.counts
        .joinToString(", ") { count -> "${count.present}/${count.requirement.count} ${workBlockLabel(count.requirement)}" }
        .ifBlank { "none" }

    private fun workBlockLabel(requirement: NpcWorkBlockRequirementDefinition): String {
        if (requirement.displayName.isNotBlank()) return requirement.displayName
        return requirement.id.removePrefix("#").substringAfter(':').replace('_', ' ')
    }

    fun syncFriends(player: ServerPlayer) {
        val currentHour = NpcTime.hour(player.level())
        val entries = NpcConfig.all().sortedBy { definition -> definition.name }.map { definition ->
            val friendship = NpcStore.friendshipSnapshot(definition.id, player)
            val giftPeriod = NpcTime.periodForReset(player.level().dayTime, definition.gifts.resetHour)
            val giftCount = NpcStore.giftCount(definition.id, player, giftPeriod)
            val giftLimit = definition.gifts.dailyLimit.coerceAtLeast(0)
            val quest = NpcQuestService.friendSummary(player, definition)
            NpcFriendEntryPayload(
                npcId = definition.id,
                name = definition.name,
                title = definition.title,
                friendshipPoints = friendship.points,
                friendshipLevel = friendship.level,
                giftStatus = if (giftLimit <= 0) "Gifts unavailable" else if (giftCount >= giftLimit) "Gifted today ($giftCount/$giftLimit)" else "Gift available ($giftCount/$giftLimit)",
                shopStatus = friendShopStatus(definition, currentHour, player),
                missionStatus = quest.status,
                aliveStatus = friendAliveStatus(player.server, definition),
                missionProgress = quest.progress,
                missionGoal = quest.goal,
            )
        }
        NpcNetwork.syncFriends(player, NpcFriendsSyncPayload(entries))
    }

    private fun friendAliveStatus(server: MinecraftServer, definition: NpcDefinition): String = when {
        existingNpc(server, definition.id)?.isAlive == true -> "Alive"
        NpcStore.isDead(definition.id) -> "Dead"
        else -> "Away"
    }

    private fun friendShopStatus(definition: NpcDefinition, currentHour: Int, player: ServerPlayer): String {
        if (definition.storeId().isBlank()) return "No shop"
        return if (NpcTime.activityAt(definition.schedule, player.level()) == "work") {
            val workplace = NpcStore.workplacePos(definition.id) ?: return if (NpcStore.workFired(definition.id)) "Unemployed" else "No workplace"
            val workBlocks = workBlockStatus(player.level(), workplace, definition)
            if (!workBlocks.ready) return "Missing ${formatMissingWorkBlocks(workBlocks)}"
            val close = currentWorkClose(definition, currentHour)
            if (close == null) "Shop Open" else "Shop Open (closes at ${formatHour(close)})"
        } else {
            val open = nextWorkOpening(definition, currentHour)
            if (open == null) "Shop closed" else "Shop closed (opens at ${formatHour(open)})"
        }
    }

    private fun currentWorkClose(definition: NpcDefinition, currentHour: Int): Int? = definition.schedule.activities
        .firstOrNull { entry -> entry.activity == "work" && hourInRange(currentHour, entry.fromHour, entry.toHour) }
        ?.toHour

    private fun hourInRange(hour: Int, from: Int, to: Int): Boolean = if (from <= to) hour in from until to else hour >= from || hour < to

    private fun formatHour(hour: Int): String {
        val normalized = Math.floorMod(hour, 24)
        val displayHour = when (val value = normalized % 12) {
            0 -> 12
            else -> value
        }
        val suffix = if (normalized < 12) "AM" else "PM"
        return "$displayHour:00 $suffix"
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
        val configuredMood = configuredGiftMood(definition, stack)
        if (configuredMood == null && NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.gift) {
            if (!player.abilities.instabuild) stack.shrink(1)
            val responseToken = NpcDialogTokens.next()
            val friendship = NpcStore.friendshipSnapshot(definition.id, player)
            val fallbackMood = "neutral"
            val fallback = friendshipMessage(definition.friendshipMessages.gift, friendship, player, definition, itemName, fallbackMood)
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
            NpcLlmService.giftSentiment(
                player,
                npc,
                definition,
                fallback,
                giftSentimentPrompt(definition, player, itemName),
                responseToken,
            ) { result ->
                val mood = normalizeGiftMood(result.giftSentiment)
                finishGiftToNpc(player, npc, definition, itemName, mood, result.message, responseToken)
                if (result.memorable.isNotBlank()) NpcStore.recordPlayerMemory(player, "llm_memorable", result.memorable)
            }
            return
        }
        val mood = configuredMood ?: "neutral"
        val friendshipDelta = PerformerPerks.giftFriendshipDelta(player, mood, giftFriendshipDelta(mood))
        val friendship = NpcStore.adjustFriendship(definition.id, player, friendshipDelta, "gift_$mood")
        showFriendshipDelta(npc.level() as? ServerLevel, npc, friendshipDelta)
        val message = friendshipMessage(definition.friendshipMessages.gift, friendship, player, definition, itemName, mood)
        if (!player.abilities.instabuild) stack.shrink(1)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "gifts $itemName to ${definition.name}", "player_gift")
        NpcStore.recordPlayerMemory(player, "gift_to_npc", "${player.gameProfile.name} gave $itemName to ${definition.name}; reaction mood was $mood.")
        if (NpcConfig.settings().llm.enabled && NpcConfig.settings().llmMessageUsage.gift) {
            val responseToken = NpcDialogTokens.next()
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken, friendshipDelta = friendshipDelta))
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
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY", friendshipDelta = friendshipDelta))
        relayNpcDialog(player, npc, definition, message)
    }

    private fun finishGiftToNpc(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, itemName: String, mood: String, message: String, responseToken: Long) {
        val friendshipDelta = PerformerPerks.giftFriendshipDelta(player, mood, giftFriendshipDelta(mood))
        val friendship = NpcStore.adjustFriendship(definition.id, player, friendshipDelta, "gift_$mood")
        showFriendshipDelta(npc.level() as? ServerLevel, npc, friendshipDelta)
        val fallback = friendshipMessage(definition.friendshipMessages.gift, friendship, player, definition, itemName, mood)
        val reply = message.trim().ifBlank { fallback }
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "gifts $itemName to ${definition.name}", "player_gift")
        NpcStore.recordPlayerMemory(player, "gift_to_npc", "${player.gameProfile.name} gave $itemName to ${definition.name}; reaction mood was $mood.")
        NpcStore.recordConversation(definition.id, player, definition.name, reply, "npc_gift_$mood")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, reply, false, friendship.level, closeOnly = true, closeLabel = "OKAY", friendshipDelta = friendshipDelta))
        relayNpcDialog(player, npc, definition, reply)
    }

    private fun giftStack(player: ServerPlayer): ItemStack = if (!player.mainHandItem.isEmpty) player.mainHandItem else player.offhandItem

    private fun configuredGiftMood(definition: NpcDefinition, stack: ItemStack): String? = when {
        matchesGift(definition.gifts.loved, stack) -> "loved"
        matchesGift(definition.gifts.liked, stack) -> "liked"
        matchesGift(definition.gifts.disliked, stack) -> "disliked"
        else -> null
    }

    private fun normalizeGiftMood(raw: String): String = when (raw.trim().lowercase()) {
        "loved", "love" -> "loved"
        "liked", "like" -> "liked"
        "disliked", "dislike" -> "disliked"
        else -> "neutral"
    }

    private fun giftSentimentPrompt(definition: NpcDefinition, player: ServerPlayer, itemName: String): String = definition.gifts.llmSentimentPrompt
        .replace("{player}", player.gameProfile.name)
        .replace("{npc}", definition.name)
        .replace("{item}", itemName)

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

    fun dialogPayload(definition: NpcDefinition, npc: ChowNpcEntity, message: String, contractGranted: Boolean, friendshipLevel: Int, closeOnly: Boolean = false, closeLabel: String = "BYE", responseToken: Long = 0L, dialogMode: String = "normal", startTalkMode: Boolean = false, friendshipDelta: Int = 0): NpcDialogPayload = NpcDialogPayload(
        definition.id,
        definition.name,
        definition.title,
        message,
        contractGranted,
        closeOnly = closeOnly,
        closeLabel = closeLabel,
        friendshipLevel = friendshipLevel,
        friendshipDelta = friendshipDelta,
        npcEntityId = npc.id,
        animalesePitch = definition.voice.animalesePitch,
        animalesePitchMultiplier = definition.voice.pitch,
        animaleseVolume = definition.voice.volume,
        animaleseRadius = definition.voice.radius,
        talkEnabled = NpcConfig.settings().llm.enabled,
        responseToken = responseToken,
        dialogMode = dialogMode,
        startTalkMode = startTalkMode,
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

    fun showBalloonToNearbyExcept(level: ServerLevel, npc: ChowNpcEntity, message: String, durationTicks: Int = 90, excludePlayers: Set<UUID>): Int {
        var recipients = 0
        level.players().forEach { listener ->
            if (listener.uuid !in excludePlayers && listener.distanceToSqr(npc.x, npc.y, npc.z) <= NPC_DIALOG_HEAR_RADIUS * NPC_DIALOG_HEAR_RADIUS) {
                NpcNetwork.showBalloon(listener, npc.id, message, durationTicks)
                recipients++
            }
        }
        return recipients
    }

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

    private fun showFriendshipDelta(level: ServerLevel?, npc: ChowNpcEntity, delta: Int) {
        if (level == null || delta == 0) return
        val marker = if (delta > 0) "@heart.png" else "@angry.png"
        val sign = if (delta > 0) "+" else ""
        sendNpcBalloon(level, npc, "$marker $sign$delta", NPC_FRIENDSHIP_DELTA_BALLOON_TICKS)
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
        val currentOwner = homeOwnerAtBed(player.level(), homePos)
        if (currentOwner != null && currentOwner != npcId) {
            val ownerName = NpcConfig.get(currentOwner)?.name ?: currentOwner
            npcSnackbar(player, definition.name, "That bed already belongs to $ownerName.", SnackbarType.ERROR)
            return false
        }
        val npc = existingNpc(player.server, npcId)
        if (npc == null || npc.level() != player.level() || npc.distanceToSqr(homePos.x + 0.5, homePos.y.toDouble(), homePos.z + 0.5) > CONTRACT_BED_ASSIGN_RADIUS_SQR) {
            npcSnackbar(player, definition.name, "${definition.name} needs to be near the bed.", SnackbarType.ERROR)
            return false
        }
        NpcStore.setHome(npcId, homePos)
        NpcStore.clearActiveCamper(npcId)
        NpcStore.recordGlobalEvent("npc_new_resident", "${definition.name} became a resident on day ${NpcTime.day(player.level())}; ${player.gameProfile.name} gave them a home.")
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

    fun assignWorkplace(player: ServerPlayer, npcId: String, workplacePos: BlockPos, stack: ItemStack): Boolean {
        val definition = NpcConfig.get(npcId) ?: run {
            player.displayClientMessage(Component.literal("Unknown NPC '$npcId'."), true)
            return false
        }
        val npc = existingNpc(player.server, npcId)
        if (npc == null || npc.level() != player.level() || npc.distanceToSqr(workplacePos.x + 0.5, workplacePos.y.toDouble(), workplacePos.z + 0.5) > WORKPLACE_ASSIGN_RADIUS_SQR) {
            npcSnackbar(player, definition.name, "${definition.name} needs to be near the work block.", SnackbarType.ERROR)
            return false
        }
        val workBlocks = workBlockStatus(player.level(), workplacePos, definition)
        if (!workBlocks.ready) {
            openMissingWorkBlocksDialog(player, npc, definition, workplacePos, workBlocks, assigning = true)
            return false
        }
        NpcStore.setWorkplace(npcId, workplacePos)
        NpcStore.recordGlobalEvent("npc_workplace_assigned", "${definition.name} got a workplace at ${workplacePos.toShortString()} from ${player.gameProfile.name}.")
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val fallback = "Got it. I will work around this block."
        val responseToken = NpcDialogTokens.next()
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, if (workLlmEnabled { it.assignedWorkplace }) "..." else fallback, false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = responseToken))
        if (workLlmEnabled { it.assignedWorkplace }) {
            NpcLlmService.event(player, npc, definition, fallback, NpcConfig.settings().work.assignedWorkplaceLlmPrompt, npcRecordType = "npc_assigned_workplace", responseToken = responseToken)
        }
        if (!player.abilities.instabuild) stack.shrink(1)
        SnackbarNetwork.send(player, SnackbarNotification.item(BuiltInRegistries.ITEM.getKey(JOB_APPLICATION.get()).toString(), "WORKPLACE ASSIGNED", "${definition.name} now works here", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return true
    }

    fun tickNpc(entity: ChowNpcEntity) {
        val definition = NpcConfig.get(entity.npcId) ?: return
        entity.homePos = validHomePos(entity.level(), definition.id)
        entity.campPos = entity.campPos ?: NpcStore.campPos(definition.id)
        tryShowCamperHousingBalloon(entity, definition)
        runNpcPriorityStack(
            NpcTaskCandidate(NpcTaskPriority.Critical) { NpcBrainOverrides.tick(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.QuestClaim) { tryQuestClaimApproach(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.ContractFollow) { tryFollowRentContractHolder(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.JobApplicationFollow) { tryFollowJobApplicationHolder(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.NpcInteraction) { tryNpcMicroInteraction(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.OutgoingGift) { tryOutgoingGift(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.QuestOffer) { NpcQuestService.tryShowOfferBalloon(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.Greeting) { !needsCamperHousingBalloon(entity, definition) && tryGreetNearbyPlayer(entity, definition) },
            NpcTaskCandidate(NpcTaskPriority.TalkingPause) { entity.isTalking() },
            NpcTaskCandidate(NpcTaskPriority.Routine) {
                NpcBrain.tick(entity, definition)
                true
            },
        )
    }

    private fun runNpcPriorityStack(vararg candidates: NpcTaskCandidate): Boolean {
        candidates.withIndex()
            .sortedWith(compareByDescending<IndexedValue<NpcTaskCandidate>> { it.value.priority.weight }.thenBy { it.index })
            .forEach { candidate -> if (candidate.value.run()) return true }
        return false
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

    private fun tryFollowJobApplicationHolder(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val level = npc.level() as? ServerLevel ?: return false
        val holder = level.players()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc) <= CONTRACT_FOLLOW_SCAN_RADIUS_SQR }
            .firstOrNull { player -> playerHoldsJobApplication(player, definition.id) }
            ?: return false
        if (npc.isSleeping) npc.stopSleeping()
        npc.debugActivity = "work"
        npc.debugGoal = "follow_job_application"
        npc.debugTargetPos = holder.blockPosition().immutable()
        npc.lookControl.setLookAt(holder, 30.0f, 30.0f)
        if (npc.distanceToSqr(holder) > CONTRACT_FOLLOW_STOP_DISTANCE_SQR) {
            npc.navigation.moveTo(holder.x, holder.y, holder.z, 0.95)
        } else {
            npc.navigation.stop()
        }
        return true
    }

    private fun playerHoldsJobApplication(player: ServerPlayer, npcId: String): Boolean {
        return NpcJobApplicationData.readNpcId(player.mainHandItem) == npcId || NpcJobApplicationData.readNpcId(player.offhandItem) == npcId
    }

    private fun tryQuestClaimApproach(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        if (npc.isSleeping) return false
        val player = NpcQuestService.readyClaimPlayer(npc, definition, NPC_QUEST_CLAIM_SCAN_RADIUS) ?: return false
        npc.debugActivity = "quest"
        npc.debugGoal = "claim_reward"
        npc.debugTargetPos = player.blockPosition().immutable()
        npc.lookControl.setLookAt(player, 30.0f, 30.0f)
        if (npc.distanceToSqr(player) > NPC_QUEST_CLAIM_DISTANCE_SQR) {
            npc.navigation.moveTo(player.x, player.y, player.z, NPC_QUEST_CLAIM_FOLLOW_SPEED)
        } else {
            npc.navigation.stop()
            val now = npc.level().gameTime
            if ((questClaimBalloonRefreshAt[npc.uuid] ?: Long.MIN_VALUE) <= now) {
                NpcQuestService.showReadyClaimBalloon(player, npc, definition)
                questClaimBalloonRefreshAt[npc.uuid] = now + NPC_QUEST_CLAIM_BALLOON_COOLDOWN_TICKS
            }
        }
        return true
    }

    private fun tryShowCamperHousingBalloon(npc: ChowNpcEntity, definition: NpcDefinition) {
        if (!needsCamperHousingBalloon(npc, definition)) return
        val level = npc.level() as? ServerLevel ?: return
        val greeting = NpcConfig.settings().greetings
        val radiusSqr = greeting.radius * greeting.radius
        val player = level.players()
            .asSequence()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc.x, npc.y, npc.z) <= radiusSqr }
            .minByOrNull { player -> player.distanceToSqr(npc.x, npc.y, npc.z) }
            ?: return
        val nextAt = camperBalloonRefreshAt[npc.uuid] ?: 0L
        if (level.gameTime < nextAt) return
        val pool = if (NpcStore.camperReturnReason(definition.id) == "lost_house") definition.camperMessages.lostHouseBalloon else definition.camperMessages.needsHouseBalloon
        sendNpcBalloon(level, npc, camperMessage(pool, player, definition), NPC_CAMPER_HOUSING_BALLOON_TICKS)
        camperBalloonRefreshAt[npc.uuid] = level.gameTime + NPC_CAMPER_HOUSING_BALLOON_REFRESH_TICKS
    }

    private fun needsCamperHousingBalloon(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        if (!definition.housing.canMoveIn) return false
        return validHomePos(npc.level(), definition.id) == null && !NpcStore.contractGiven(definition.id)
    }

    private fun tryOutgoingGift(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val config = definition.gifts.outgoing
        if (!config.enabled || npc.isSleeping) return false
        val level = npc.level() as? ServerLevel ?: return false
        outgoingGiftApproaches[npc.uuid]?.let { approach ->
            val player = level.server.playerList.getPlayer(approach.playerId)
            if (player == null || player.level() != level || !player.isAlive || player.isSpectator || level.gameTime - approach.startedAtTick > config.followSeconds * 20L) {
                outgoingGiftApproaches.remove(npc.uuid)
                return false
            }
            npc.debugActivity = "gift"
            npc.debugGoal = "deliver_gift"
            npc.debugTargetPos = player.blockPosition().immutable()
            npc.lookControl.setLookAt(player, 30.0f, 30.0f)
            if (npc.distanceToSqr(player) > NPC_GIFT_DELIVERY_DISTANCE_SQR) {
                npc.navigation.moveTo(player.x, player.y, player.z, NPC_GIFT_FOLLOW_SPEED)
            } else {
                npc.navigation.stop()
            }
            return true
        }

        if (npc.isTalking()) return false
        if (isAutoTaskCoolingDown(npc)) return false

        val radiusSqr = config.radius * config.radius
        val day = NpcTime.day(level)
        val hour = NpcTime.hour(level)
        val pendingPlayer = level.players()
            .asSequence()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc.x, npc.y, npc.z) <= radiusSqr }
            .mapNotNull { player -> NpcStore.pendingOutgoingGiftReady(definition.id, player, day)?.let { gift -> player to gift } }
            .minByOrNull { (player, _) -> player.distanceToSqr(npc.x, npc.y, npc.z) }
        if (pendingPlayer != null) {
            NpcStore.markPendingOutgoingGiftReminder(definition.id, pendingPlayer.first, day)
            startOutgoingGiftApproach(npc, level, definition, config, pendingPlayer.first)
            return true
        }
        val player = level.players()
            .asSequence()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc.x, npc.y, npc.z) <= radiusSqr }
            .filter { player -> NpcStore.friendshipSnapshot(definition.id, player).level >= config.minFriendshipLevel }
            .filter { player -> NpcStore.outgoingGiftReady(definition.id, player, day, hour, randomOutgoingGiftHour(definition, level.random)) }
            .minByOrNull { player -> player.distanceToSqr(npc.x, npc.y, npc.z) }
            ?: return false
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val gift = selectOutgoingGift(config, friendship.level, level.random) ?: return false
        NpcStore.setPendingOutgoingGift(definition.id, player, day, gift.item, gift.qty)
        startOutgoingGiftApproach(npc, level, definition, config, player)
        return true
    }

    private fun startOutgoingGiftApproach(npc: ChowNpcEntity, level: ServerLevel, definition: NpcDefinition, config: NpcOutgoingGiftsDefinition, player: ServerPlayer) {
        outgoingGiftApproaches[npc.uuid] = NpcOutgoingGiftApproach(player.uuid, level.gameTime)
        val offer = outgoingGiftOfferMessage(config, player, definition)
        sendNpcBalloon(level, npc, offer, config.followSeconds * 20)
        npc.startTalkingTo(player, config.followSeconds * 20)
        markAutoTaskCooldown(npc, config.followSeconds * 20L)
    }

    private fun randomOutgoingGiftHour(definition: NpcDefinition, random: net.minecraft.util.RandomSource): Int {
        val awakeHours = (0..23).filter { hour -> definition.schedule.activityAtHour(hour) != "sleep" }
        if (awakeHours.isEmpty()) return random.nextInt(24)
        return awakeHours[random.nextInt(awakeHours.size)]
    }

    private fun selectOutgoingGift(config: NpcOutgoingGiftsDefinition, friendshipLevel: Int, random: net.minecraft.util.RandomSource): NpcOutgoingGiftEntryDefinition? {
        val pool = (if (friendshipLevel >= config.rareFriendshipLevel && config.rarePool.isNotEmpty()) config.rarePool else config.pool)
            .filter { gift -> outgoingGiftStack(gift) != null }
        val totalWeight = pool.sumOf { gift -> gift.weight.coerceAtLeast(0) }
        if (totalWeight <= 0) return null
        var roll = random.nextInt(totalWeight)
        pool.forEach { gift ->
            roll -= gift.weight.coerceAtLeast(0)
            if (roll < 0) return gift
        }
        return pool.lastOrNull()
    }

    private fun claimOutgoingGift(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, gift: NpcPendingOutgoingGift) {
        val stack = outgoingGiftStack(gift) ?: return
        val itemName = stack.hoverName.string
        val quantity = stack.count
        giveStack(player, stack)
        SnackbarNetwork.send(player, SnackbarNotification.item(BuiltInRegistries.ITEM.getKey(stack.item).toString(), "GIFT RECEIVED", "$quantity x $itemName from ${definition.name}", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        NpcStore.clearPendingOutgoingGift(definition.id, player, NpcTime.day(player.level()))
        outgoingGiftApproaches.remove(npc.uuid)
        NpcStore.recordConversation(definition.id, player, definition.name, "gives $quantity x $itemName to ${player.gameProfile.name}", "npc_outgoing_gift_item")
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "receives $quantity x $itemName from ${definition.name}", "player_received_npc_gift")
        NpcStore.recordPlayerMemory(player, "gift_from_npc", "${definition.name} gave ${player.gameProfile.name} $quantity x $itemName.")
        val fallback = outgoingGiftFallback(definition.gifts.outgoing, player, definition, itemName, quantity)
        if (NpcConfig.settings().llm.enabled && definition.gifts.outgoing.llmEnabled) {
            val responseToken = NpcDialogTokens.next()
            NpcNetwork.openDialog(player, dialogPayload(definition, npc, "...", false, friendship.level, closeOnly = true, closeLabel = "THANKS", responseToken = responseToken))
            NpcLlmService.event(
                player,
                npc,
                definition,
                fallback,
                outgoingGiftPrompt(definition.gifts.outgoing, player, definition, itemName, quantity),
                inputLabel = "Gift claim",
                sendTalkResponse = true,
                showBalloon = false,
                excludePlayerFromBalloon = false,
                npcRecordType = "npc_outgoing_gift",
                responseToken = responseToken,
            )
            return
        }
        npc.startTalkingTo(player, NPC_DIALOG_DURATION_TICKS)
        NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_outgoing_gift")
        NpcNetwork.openDialog(player, dialogPayload(definition, npc, fallback, false, friendship.level, closeOnly = true, closeLabel = "THANKS"))
        relayNpcDialog(player, npc, definition, fallback)
    }

    private fun outgoingGiftStack(gift: NpcOutgoingGiftEntryDefinition): ItemStack? {
        return outgoingGiftStack(gift.item, gift.qty)
    }

    private fun outgoingGiftStack(gift: NpcPendingOutgoingGift): ItemStack? {
        return outgoingGiftStack(gift.item, gift.qty)
    }

    private fun outgoingGiftStack(itemId: String, qty: Int): ItemStack? {
        val item = runCatching { ResourceLocation.parse(itemId) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
            ?: Items.AIR
        if (item == Items.AIR) return null
        return ItemStack(item, qty.coerceIn(1, item.defaultMaxStackSize))
    }

    private fun outgoingGiftOfferMessage(config: NpcOutgoingGiftsDefinition, player: ServerPlayer, definition: NpcDefinition): String = config.offerMessages.randomOrNull()
        ?.let { outgoingGiftText(it, player, definition, "", 0) }
        ?: "@gift.png Hey ${player.gameProfile.name}!"

    private fun outgoingGiftFallback(config: NpcOutgoingGiftsDefinition, player: ServerPlayer, definition: NpcDefinition, itemName: String, quantity: Int): String = config.fallbackMessages.randomOrNull()
        ?.let { outgoingGiftText(it, player, definition, itemName, quantity) }
        ?: "I brought you $quantity x $itemName, ${player.gameProfile.name}."

    private fun outgoingGiftPrompt(config: NpcOutgoingGiftsDefinition, player: ServerPlayer, definition: NpcDefinition, itemName: String, quantity: Int): String = outgoingGiftText(config.llmPrompt, player, definition, itemName, quantity)

    private fun outgoingGiftText(template: String, player: ServerPlayer, definition: NpcDefinition, itemName: String, quantity: Int): String = template
        .replace("{player}", player.gameProfile.name)
        .replace("{npc}", definition.name)
        .replace("{item}", itemName)
        .replace("{quantity}", quantity.toString())

    private fun tryGreetNearbyPlayer(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val level = npc.level() as? ServerLevel ?: return false
        val greeting = NpcConfig.settings().greetings
        val radiusSqr = greeting.radius * greeting.radius
        val playersInRadius = level.players()
            .filter { player -> player.isAlive && !player.isSpectator && player.distanceToSqr(npc.x, npc.y, npc.z) <= radiusSqr }
        updateGreetingRadiusState(npc, definition, playersInRadius)
        if (npc.isTalking() || npc.isSleeping) return false
        if (isAutoTaskCoolingDown(npc)) return false
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
            markAutoTaskCooldown(npc, durationTicks.toLong())
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
        markAutoTaskCooldown(npc, durationTicks.toLong())
        NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_greeting_balloon")
        return true
    }

    private fun tryNpcMicroInteraction(npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        val level = npc.level() as? ServerLevel ?: return false
        npcMicroInteractions[npc.uuid]?.let { active ->
            val other = findNpc(level.server, active.partnerId) ?: return finishNpcMicroInteraction(npc.uuid, active.partnerId)
            if (!npc.isAlive || !other.isAlive || npc.isSleeping || other.isSleeping || level.gameTime >= active.untilTick) return finishNpcMicroInteraction(npc.uuid, active.partnerId)
            npc.debugActivity = "interaction"
            npc.debugGoal = "talk_npc"
            npc.debugTargetPos = other.blockPosition().immutable()
            npc.lookControl.setLookAt(other, 30.0f, 30.0f)
            if (npc.distanceToSqr(other) > NPC_MICRO_INTERACTION_DISTANCE_SQR) {
                npc.navigation.moveTo(other.x, other.y, other.z, NPC_MICRO_INTERACTION_SPEED)
            } else {
                npc.navigation.stop()
            }
            showMicroInteractionBalloonToClosePlayers(level, npc, active)
            return true
        }

        val settings = NpcConfig.settings().npcInteractions
        val plazaMeetup = activityFor(npc, definition) == "meetup"
        if (!settings.enabled || npc.isSleeping || npc.isTalking() || NpcTime.activityAt(definition.schedule, level) == "sleep") return false
        if (isAutoTaskCoolingDown(npc)) return false
        if (!plazaMeetup && (npcMicroInteractionCooldownUntil[definition.id] ?: 0L) > level.dayTime) return false
        val radius = if (plazaMeetup) plazaMeetupRadius().toDouble() else settings.radius
        val radiusSqr = radius * radius
        val other = level.getEntities(NPC_ENTITY.get()) { other ->
            other.uuid != npc.uuid && other.isAlive && !other.isSleeping && !other.isTalking() && other.distanceToSqr(npc) <= radiusSqr && other.uuid !in npcMicroInteractions && other.uuid !in outgoingGiftApproaches
        }.asSequence()
            .mapNotNull { other -> NpcConfig.get(other.npcId)?.takeIf { otherDefinition -> NpcTime.activityAt(otherDefinition.schedule, level) != "sleep" && (plazaMeetup || (npcMicroInteractionCooldownUntil[otherDefinition.id] ?: 0L) <= level.dayTime) }?.let { other to it } }
            .minByOrNull { (other, _) -> other.distanceToSqr(npc) }
            ?: return false
        startNpcMicroInteraction(level, npc, definition, other.first, other.second, settings, plazaMeetup)
        return true
    }

    private fun finishNpcMicroInteraction(firstId: UUID, secondId: UUID): Boolean {
        npcMicroInteractions.remove(firstId)
        npcMicroInteractions.remove(secondId)
        return false
    }

    private fun startNpcMicroInteraction(level: ServerLevel, first: ChowNpcEntity, firstDefinition: NpcDefinition, second: ChowNpcEntity, secondDefinition: NpcDefinition, settings: NpcInteractionSettingsDefinition, plazaMeetup: Boolean = false) {
        val durationTicks = settings.durationSeconds * 20L
        val firstMessage = npcMicroInteractionMessage(firstDefinition, secondDefinition, settings)
        val secondMessage = npcMicroInteractionMessage(secondDefinition, firstDefinition, settings)
        val now = level.gameTime
        npcMicroInteractions[first.uuid] = ActiveNpcMicroInteraction(second.uuid, firstMessage, now + durationTicks)
        npcMicroInteractions[second.uuid] = ActiveNpcMicroInteraction(first.uuid, secondMessage, now + durationTicks)
        markAutoTaskCooldown(first, durationTicks)
        markAutoTaskCooldown(second, durationTicks)
        val cooldownUntil = if (plazaMeetup) level.dayTime + NPC_PLAZA_MICRO_COOLDOWN_TICKS else {
            val cooldownHours = if (settings.cooldownMinHours == settings.cooldownMaxHours) settings.cooldownMinHours else settings.cooldownMinHours + level.random.nextInt(settings.cooldownMaxHours - settings.cooldownMinHours + 1)
            NpcTime.addHours(level.dayTime, cooldownHours)
        }
        npcMicroInteractionCooldownUntil[firstDefinition.id] = cooldownUntil
        npcMicroInteractionCooldownUntil[secondDefinition.id] = cooldownUntil
        NpcStore.recordGlobalEvent("npc_micro_interaction", "${firstDefinition.name} chatted with ${secondDefinition.name} near ${first.blockPosition().toShortString()}.")
    }

    private fun showMicroInteractionBalloonToClosePlayers(level: ServerLevel, npc: ChowNpcEntity, active: ActiveNpcMicroInteraction) {
        level.players().forEach { listener ->
            if (listener.uuid in active.shownToPlayers) return@forEach
            if (listener.distanceToSqr(npc.x, npc.y, npc.z) > NPC_BALLOON_CLOSE_RADIUS_SQR) return@forEach
            NpcNetwork.showBalloon(listener, npc.id, active.message, NPC_MICRO_INTERACTION_BALLOON_TICKS)
            active.shownToPlayers += listener.uuid
        }
    }

    private fun npcMicroInteractionMessage(definition: NpcDefinition, otherDefinition: NpcDefinition, settings: NpcInteractionSettingsDefinition): String {
        val pool = (settings.messages + definition.npcInteractionMessages + definition.npcInteractionMessages).ifEmpty { listOf("Talking with {other}...") }
        return pool.randomOrNull()
            ?.replace("{npc}", definition.name)
            ?.replace("{other}", otherDefinition.name)
            ?: "Talking with ${otherDefinition.name}..."
    }

    private fun isAutoTaskCoolingDown(npc: ChowNpcEntity): Boolean {
        val level = npc.level()
        val untilTick = npcAutoTaskCooldownUntil[npc.uuid] ?: return false
        if (level.gameTime < untilTick) return true
        npcAutoTaskCooldownUntil.remove(npc.uuid)
        return false
    }

    private fun markAutoTaskCooldown(npc: ChowNpcEntity, taskTicks: Long = 0L) {
        val level = npc.level()
        val extraTicks = NPC_AUTO_TASK_COOLDOWN_MIN_TICKS + level.random.nextInt((NPC_AUTO_TASK_COOLDOWN_MAX_TICKS - NPC_AUTO_TASK_COOLDOWN_MIN_TICKS + 1).toInt())
        npcAutoTaskCooldownUntil[npc.uuid] = level.gameTime + taskTicks + extraTicks
    }

    private fun updateGreetingRadiusState(npc: ChowNpcEntity, definition: NpcDefinition, playersInRadius: List<ServerPlayer>) {
        val current = playersInRadius.map { player -> player.stringUUID }.toSet()
        val previous = greetingRadiusPlayers.getOrPut(npc.uuid) { linkedSetOf() }
        previous.filter { playerId -> playerId !in current }.forEach { playerId -> NpcStore.clearGreetingCooldown(definition.id, playerId) }
        previous.clear()
        previous.addAll(current)
    }

    fun moveToActivityTarget(entity: ChowNpcEntity, definition: NpcDefinition, activity: String) {
        if (activity == "meetup") {
            val target = randomPlazaTarget(entity) ?: return
            if (entity.isSleeping) entity.stopSleeping()
            entity.debugActivity = activity
            entity.debugGoal = "plaza"
            entity.debugTargetPos = target.immutable()
            entity.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 0.8)
            return
        }
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
        if (activity == "work") {
            val workplace = NpcStore.workplacePos(definition.id)
            if (workplace == null) {
                if (entity.isSleeping) entity.stopSleeping()
                entity.debugActivity = activity
                entity.debugGoal = if (NpcStore.workFired(definition.id)) "unemployed" else "no_workplace"
                entity.debugTargetPos = null
                entity.navigation.stop()
                return
            }
            val target = randomWorkplaceTarget(entity, workplace) ?: workplace.above()
            if (entity.isSleeping) entity.stopSleeping()
            entity.debugActivity = activity
            entity.debugGoal = "workplace"
            entity.debugTargetPos = target.immutable()
            entity.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 0.8)
            return
        }
        val target = randomRoamTarget(entity, definition) ?: return
        if (entity.isSleeping) entity.stopSleeping()
        entity.debugActivity = activity
        entity.debugGoal = "roam"
        entity.debugTargetPos = target.immutable()
        entity.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 0.8)
    }

    fun activityFor(entity: ChowNpcEntity, definition: NpcDefinition): String {
        val level = entity.level()
        val activity = NpcTime.activityAt(definition.schedule, level)
        if (activity == "sleep" || plazaMeetupTarget() == null) return activity
        val hour = NpcTime.hour(level)
        return if (hour in NPC_PLAZA_MEETUP_START_HOUR until NPC_PLAZA_MEETUP_END_HOUR) "meetup" else activity
    }

    private fun randomPlazaTarget(entity: ChowNpcEntity): BlockPos? {
        val center = plazaMeetupTarget() ?: return null
        val level = entity.level()
        val radius = plazaMeetupRadius()
        repeat(12) {
            val candidate = center.offset(level.random.nextInt(radius * 2 + 1) - radius, 0, level.random.nextInt(radius * 2 + 1) - radius)
            listOf(candidate, candidate.above(), candidate.below()).firstOrNull { pos ->
                level.getBlockState(pos.below()).isSolidRender(level, pos.below()) && level.getBlockState(pos).isAir && level.getBlockState(pos.above()).isAir
            }?.let { return it }
        }
        return center
    }

    private fun plazaMeetupTarget(): BlockPos? = NpcStore.townCenterPos() ?: NpcStore.campBlockPos()

    private fun plazaMeetupRadius(): Int = if (NpcStore.townCenterPos() != null) NpcStore.townCenterRadius() else NPC_PLAZA_CAMP_FALLBACK_RADIUS

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
        NpcQuestService.tick(event.server)
        NpcWorldChatService.tick(event.server)
    }

    fun plazaMeetupStartHour(): Int = NPC_PLAZA_MEETUP_START_HOUR

    fun isPlazaMeetupHour(level: Level): Boolean {
        val hour = NpcTime.hour(level)
        return hour in NPC_PLAZA_MEETUP_START_HOUR until NPC_PLAZA_MEETUP_END_HOUR
    }

    private fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
        val npc = event.entity as? ChowNpcEntity ?: return
        val player = event.source.entity as? ServerPlayer ?: return
        val definition = NpcConfig.get(npc.npcId) ?: return
        val hitCount = NpcStore.recordHurt(definition.id, player, System.currentTimeMillis())
        NpcStore.adjustFriendship(definition.id, player, FRIENDSHIP_HIT_DELTA, "hit")
        showFriendshipDelta(npc.level() as? ServerLevel, npc, FRIENDSHIP_HIT_DELTA)
        NpcStore.recordConversation(definition.id, player, player.gameProfile.name, "hurts ${definition.name}", "player_hurt")
        if (hitCount % HURT_MESSAGE_INTERVAL == 0) {
            relayNpcHurtMessage(player, npc, definition)
            NpcBrainOverrides.startHurtResponse(npc, player)
        }
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        NpcStore.recordGlobalEvent("player_join", "${player.gameProfile.name} joined the server")
        NpcQuestService.syncTo(player)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        NpcStore.recordGlobalEvent("player_leave", "${player.gameProfile.name} left the server")
        NpcConfig.all().forEach { definition -> NpcLlmService.cancel(player, definition.id) }
    }

    private fun onServerChat(event: ServerChatEvent) {
        NpcWorldChatService.onMinecraftChat(event.player, event.rawText)
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
                showFriendshipDelta(npc.level() as? ServerLevel, npc, FRIENDSHIP_KILL_DELTA)
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
        val player = event.entity as? ServerPlayer
        if (player != null) {
            val deathMessage = event.source.getLocalizedDeathMessage(player).string
            NpcStore.recordGlobalEvent("player_death", deathMessage)
            NpcStore.recordGlobalMemory("player_death", deathMessage)
            NpcStore.recordPlayerMemory(player, "death", deathMessage)
            return
        }

        val killer = event.source.entity as? ServerPlayer ?: return
        if (event.entity.maxHealth < NOTABLE_KILL_HEALTH_THRESHOLD) return
        val targetName = event.entity.displayName?.string ?: event.entity.type.description.string
        val killMessage = "${killer.gameProfile.name} defeated $targetName"
        NpcStore.recordGlobalEvent("notable_kill", killMessage)
        NpcStore.recordGlobalMemory("notable_kill", killMessage)
        NpcStore.recordPlayerMemory(killer, "notable_kill", killMessage)
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
        event.dispatcher.register(llmRoot())
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.hand != InteractionHand.MAIN_HAND) return
        val player = event.entity
        val stack = player.getItemInHand(event.hand)
        val jobNpcId = NpcJobApplicationData.readNpcId(stack)
        if (jobNpcId.isNotBlank()) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS
            if (player.level().isClientSide) return
            player as? ServerPlayer ?: return
            assignWorkplace(player, jobNpcId, event.pos, stack)
            return
        }
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
            Commands.literal("quest")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("finish")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .suggests(::suggestNpcIds)
                                .executes(::questFinishCommand),
                        ),
                ),
        )
        .then(
            Commands.literal("debug")
                .requires { source -> source.hasPermission(2) }
                .executes(::debugCommand)
                .then(
                    Commands.literal("clock")
                        .executes(::debugClockCommand),
                )
                .then(
                    Commands.literal("llm")
                        .executes(::debugLlmCommand),
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
            Commands.literal("plaza")
                .requires { source -> source.hasPermission(2) }
                .executes(::plazaInfoCommand)
                .then(Commands.literal("set").executes(::plazaSetCommand))
                .then(Commands.literal("clear").executes(::plazaClearCommand))
                .then(Commands.literal("radius").then(Commands.argument("blocks", IntegerArgumentType.integer(4, 64)).executes(::plazaRadiusCommand))),
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

    private fun suggestLlmPresets(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(NpcConfig.llmPresetNames(), builder)

    private fun llmRoot(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("llm")
        .requires { source -> source.hasPermission(2) }
        .executes(::llmStatusCommand)
        .then(
            Commands.literal("switch")
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests(::suggestLlmPresets)
                        .executes(::llmSwitchCommand),
                ),
        )

    private fun llmStatusCommand(context: CommandContext<CommandSourceStack>): Int {
        val settings = NpcConfig.settings().llm
        context.source.sendSuccess({ Component.literal("NPC LLM preset=${settings.activePreset} enabled=${settings.enabled} provider=${settings.provider} model=${settings.model} available=${NpcConfig.llmPresetNames().joinToString(", ")}").withStyle(ChatFormatting.GOLD) }, false)
        return 1
    }

    private fun llmSwitchCommand(context: CommandContext<CommandSourceStack>): Int {
        val result = NpcConfig.switchLlmPreset(StringArgumentType.getString(context, "name"))
        if (result.success) context.source.sendSuccess({ Component.literal(result.message).withStyle(ChatFormatting.GREEN) }, true) else context.source.sendFailure(Component.literal(result.message))
        return if (result.success) 1 else 0
    }

    private fun reloadCommand(context: CommandContext<CommandSourceStack>): Int {
        ChowClockConfig.load()
        NpcConfig.load()
        context.source.sendSuccess({ Component.literal("Reloaded ${NpcConfig.all().size} NPC definition(s). Clock source: ${ChowClockConfig.sourceName()}.") }, true)
        return NpcConfig.all().size
    }

    private fun questFinishCommand(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val npcId = StringArgumentType.getString(context, "id")
        val definition = NpcConfig.get(npcId)
        if (definition == null || !NpcQuestService.debugFinish(player, definition.id)) {
            context.source.sendFailure(Component.literal("No active NPC quest for $npcId."))
            return 0
        }
        context.source.sendSuccess({ Component.literal("Finished active NPC quest for ${definition.name}.").withStyle(ChatFormatting.GREEN) }, true)
        return 1
    }

    private fun plazaInfoCommand(context: CommandContext<CommandSourceStack>): Int {
        val center = NpcStore.townCenterPos()
        val fallback = NpcStore.campBlockPos()
        val active = center ?: fallback
        val label = when {
            center != null -> "town_center"
            fallback != null -> "camping_block_fallback"
            else -> "none"
        }
        context.source.sendSuccess({ Component.literal("NPC plaza center=$label pos=${active?.toShortString() ?: "unset"} radius=${plazaMeetupRadius()} meetup=${NPC_PLAZA_MEETUP_START_HOUR}:00-${NPC_PLAZA_MEETUP_END_HOUR}:00") }, false)
        return 1
    }

    private fun plazaSetCommand(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val pos = player.blockPosition()
        NpcStore.setTownCenter(pos)
        context.source.sendSuccess({ Component.literal("NPC town center set to ${pos.toShortString()} radius=${NpcStore.townCenterRadius()}.") }, true)
        return 1
    }

    private fun plazaClearCommand(context: CommandContext<CommandSourceStack>): Int {
        NpcStore.clearTownCenter()
        context.source.sendSuccess({ Component.literal("NPC town center cleared. Meetup falls back to camping block if one exists.") }, true)
        return 1
    }

    private fun plazaRadiusCommand(context: CommandContext<CommandSourceStack>): Int {
        val radius = IntegerArgumentType.getInteger(context, "blocks")
        NpcStore.setTownCenterRadius(radius)
        context.source.sendSuccess({ Component.literal("NPC town center radius set to ${NpcStore.townCenterRadius()} blocks.") }, true)
        return radius
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

    private fun debugLlmCommand(context: CommandContext<CommandSourceStack>): Int {
        val settings = NpcConfig.settings().llm
        context.source.sendSuccess({ Component.literal("NPC LLM DEBUG preset=${settings.activePreset} enabled=${settings.enabled} provider=${settings.provider} model=${settings.model}").withStyle(ChatFormatting.GOLD) }, false)
        val lines = NpcLlmService.debugErrorLines()
        if (lines.isEmpty()) {
            context.source.sendSuccess({ Component.literal("No recent LLM errors recorded.").withStyle(ChatFormatting.GRAY) }, false)
            return 1
        }
        lines.forEach { line -> context.source.sendSuccess({ Component.literal(line).withStyle(ChatFormatting.GRAY) }, false) }
        return lines.size
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
        NpcStore.recordGlobalEvent("npc_spawned", "${definition.name} appeared near the camping block on day ${NpcTime.day(level)}.")
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

    private fun createJobApplication(npcId: String): ItemStack = NpcJobApplicationData.forNpc(ItemStack(JOB_APPLICATION.get()), npcId)

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

    private fun randomWorkplaceTarget(entity: ChowNpcEntity, center: BlockPos): BlockPos? {
        val random = entity.random
        repeat(16) {
            val x = center.x + random.nextInt(WORKPLACE_ROAM_RADIUS * 2 + 1) - WORKPLACE_ROAM_RADIUS
            val z = center.z + random.nextInt(WORKPLACE_ROAM_RADIUS * 2 + 1) - WORKPLACE_ROAM_RADIUS
            val base = BlockPos(x, center.y, z)
            val localTarget = (-2..2).asSequence()
                .map { offset -> base.offset(0, offset, 0) }
                .firstOrNull { pos -> entity.navigation.createPath(pos, 0) != null }
            if (localTarget != null) return localTarget
        }
        repeat(8) {
            val x = center.x + random.nextInt(WORKPLACE_ROAM_RADIUS * 2 + 1) - WORKPLACE_ROAM_RADIUS
            val z = center.z + random.nextInt(WORKPLACE_ROAM_RADIUS * 2 + 1) - WORKPLACE_ROAM_RADIUS
            val surface = entity.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos(x, center.y, z))
            if (entity.navigation.createPath(surface, 0) != null) return surface
        }
        return null
    }

    private data class NpcLookHit(val npc: ChowNpcEntity, val along: Double, val distanceSqr: Double)

    private data class NpcOutgoingGiftApproach(val playerId: UUID, val startedAtTick: Long)

    private data class NpcTaskCandidate(val priority: NpcTaskPriority, val run: () -> Boolean)

    private data class NpcWorkBlockStatus(val counts: List<NpcWorkBlockCount>) {
        val ready: Boolean = counts.all { count -> count.present >= count.requirement.count }
    }

    private data class NpcWorkBlockCount(val requirement: NpcWorkBlockRequirementDefinition, val present: Int) {
        val missing: Int = (requirement.count - present).coerceAtLeast(0)
    }

    private enum class NpcTaskPriority(val weight: Int) {
        Critical(100),
        QuestClaim(95),
        ContractFollow(90),
        JobApplicationFollow(88),
        NpcInteraction(80),
        OutgoingGift(70),
        QuestOffer(65),
        Greeting(60),
        TalkingPause(20),
        Routine(0),
    }

    private data class ActiveNpcMicroInteraction(val partnerId: UUID, val message: String, val untilTick: Long, val shownToPlayers: MutableSet<UUID> = linkedSetOf())

    private const val DEBUG_REACH = 12.0
    private const val DEBUG_AIM_RADIUS = 1.5
    private const val REALTIME_DEBUG_INTERVAL_TICKS = 10
    private const val SLEEP_REACH_DISTANCE_SQR = 4.0
    private const val SLEEP_PILLOW_OFFSET = 0.5
    private const val NPC_DIALOG_HEAR_RADIUS = 30.0
    private const val NPC_BALLOON_CLOSE_RADIUS_SQR = 8.0 * 8.0
    private const val NPC_DIALOG_ACTION_DISTANCE_SQR = 64.0
    private const val HURT_MESSAGE_INTERVAL = 3
    private const val NPC_CAMP_SPAWN_RADIUS = 2
    private const val CONTRACT_FOLLOW_SCAN_RADIUS_SQR = 32.0 * 32.0
    private const val CONTRACT_FOLLOW_STOP_DISTANCE_SQR = 2.5 * 2.5
    private const val NPC_QUEST_CLAIM_SCAN_RADIUS = 32.0
    private const val NPC_QUEST_CLAIM_DISTANCE_SQR = 2.5 * 2.5
    private const val NPC_QUEST_CLAIM_FOLLOW_SPEED = 0.95
    private const val NPC_QUEST_CLAIM_BALLOON_COOLDOWN_TICKS = 100L
    private const val NPC_GIFT_DELIVERY_DISTANCE_SQR = 2.5 * 2.5
    private const val NPC_GIFT_FOLLOW_SPEED = 0.95
    private const val NPC_MICRO_INTERACTION_DISTANCE_SQR = 2.5 * 2.5
    private const val NPC_MICRO_INTERACTION_SPEED = 0.75
    private const val NPC_MICRO_INTERACTION_BALLOON_TICKS = 100
    private const val NPC_AUTO_TASK_COOLDOWN_MIN_TICKS = 200L
    private const val NPC_AUTO_TASK_COOLDOWN_MAX_TICKS = 300L
    private const val NPC_PLAZA_MEETUP_START_HOUR = 15
    private const val NPC_PLAZA_MEETUP_END_HOUR = 20
    private const val NPC_PLAZA_CAMP_FALLBACK_RADIUS = 10
    private const val NPC_PLAZA_MICRO_COOLDOWN_TICKS = 120L
    private const val CONTRACT_BED_ASSIGN_RADIUS_SQR = 7.0 * 7.0
    private const val WORKPLACE_ASSIGN_RADIUS_SQR = 8.0 * 8.0
    private const val WORKPLACE_ROAM_RADIUS = 8
    private const val NPC_CAMPER_HOUSING_BALLOON_TICKS = 120
    private const val NPC_CAMPER_HOUSING_BALLOON_REFRESH_TICKS = 80L
    private const val NPC_FRIENDSHIP_DELTA_BALLOON_TICKS = 50
    private const val FRIENDSHIP_HIT_DELTA = -10
    private const val FRIENDSHIP_KILL_DELTA = -300
    private const val FIRST_DAILY_CHAT_FRIENDSHIP_DELTA = 25
    private const val NOTABLE_KILL_HEALTH_THRESHOLD = 100.0f
    private const val RESPAWN_SCAN_INTERVAL_TICKS = 20
    private const val NPC_RESPAWN_HOUR = 5
}
