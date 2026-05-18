package dev.gisketch.chowkingdom.bosses

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.battlepass.BattlepassXpStore
import dev.gisketch.chowkingdom.discord.DiscordWebhookClient
import dev.gisketch.chowkingdom.npc.ChowNpcEntity
import dev.gisketch.chowkingdom.npc.NpcConfig
import dev.gisketch.chowkingdom.npc.NpcDefinition
import dev.gisketch.chowkingdom.npc.NpcDialogTokens
import dev.gisketch.chowkingdom.npc.NpcFeature
import dev.gisketch.chowkingdom.npc.NpcLlmService
import dev.gisketch.chowkingdom.npc.NpcNetwork
import dev.gisketch.chowkingdom.npc.NpcQuestHudEntryPayload
import dev.gisketch.chowkingdom.npc.NpcQuestService
import dev.gisketch.chowkingdom.npc.NpcStore
import dev.gisketch.chowkingdom.npc.NpcWorldChatPayload
import dev.gisketch.chowkingdom.shipping.ShippingBinStore
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.Locale
import java.util.UUID

object BossEventsFeature {
    private val fights: MutableMap<UUID, BossFightRecord> = linkedMapOf()
    private val lockedDeathEntities: MutableSet<UUID> = linkedSetOf()
    private val nextLockedWarningAt: MutableMap<String, Long> = linkedMapOf()

    fun register() {
        BossEventsConfig.load()
        BossEventsStore.load()
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onEntityJoinLevel)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onLivingDamage)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onLivingDeath)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ::onLivingDrops)
    }

    fun checkShippingUnlocks(server: MinecraftServer) {
        if (!BossEventsConfig.settings().enabled) return
        val total = ShippingBinStore.totalChowcoinsSold()
        BossEventsConfig.entries()
            .filter { entry -> total >= entry.thresholdChowcoins && !BossEventsStore.unlocked(entry.id) }
            .forEach { entry ->
                BossEventsStore.unlock(entry.id)
                announceUnlock(server, entry, total)
            }
        syncAll(server)
    }

    fun hudEntriesFor(player: ServerPlayer): List<NpcQuestHudEntryPayload> {
        if (!BossEventsConfig.settings().enabled) return emptyList()
        val entry = BossEventsConfig.firstUnlockedUncleared(
            cleared = BossEventsStore::clearedByAny,
            unlocked = BossEventsStore::unlocked,
        ) ?: return claimableEntries(player)
        val fight = fights.values.firstOrNull { it.bossId == entry.id }
        val progress = fight?.damagedBy?.size ?: 0
        return listOf(hudEntry(entry, progress.coerceAtMost(entry.requiredPlayers), entry.requiredPlayers))
    }

    fun tryOpenFinnDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        if (definition.id.lowercase(Locale.ROOT) != BossEventsConfig.settings().finnNpcId) return false
        val entry = BossEventsStore.claimableBosses(player).firstOrNull() ?: return false
        openClaimDialog(player, npc, definition, entry)
        return true
    }

    fun handleFinnAction(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, action: String): Boolean {
        if (definition.id.lowercase(Locale.ROOT) != BossEventsConfig.settings().finnNpcId) return false
        if (action.lowercase(Locale.ROOT) != "boss_claim") return false
        val entry = BossEventsStore.claimableBosses(player).firstOrNull()
        if (entry == null) {
            val friendship = NpcStore.friendshipSnapshot(definition.id, player)
            NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, "No boss contract reward is ready yet, ${player.gameProfile.name}.", false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
            return true
        }
        claim(player, npc, definition, entry)
        return true
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        BossEventsConfig.load()
        BossEventsStore.load()
        checkShippingUnlocks(event.server)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val oldest = event.server.overworld().gameTime - BossEventsConfig.settings().participationWindowTicks
        fights.entries.removeIf { (_, fight) -> fight.startedAtTick < oldest && fight.damagedBy.values.all { it < oldest } }
        if (event.server.overworld().gameTime % 200L == 0L) syncAll(event.server)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        checkShippingUnlocks(player.server)
        NpcQuestService.syncTo(player)
    }

    private fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        if (event.level.isClientSide) return
        val entity = event.entity as? LivingEntity ?: return
        val entry = bossEntry(entity) ?: return
        if (BossEventsStore.unlocked(entry.id)) return
        warnNearbyLocked(entity, entry)
    }

    private fun onLivingDamage(event: LivingDamageEvent.Pre) {
        val entity = event.entity
        val entry = bossEntry(entity) ?: return
        val attacker = event.source.entity as? ServerPlayer
        if (!BossEventsStore.unlocked(entry.id)) {
            if (attacker != null) warnLocked(attacker, entry)
            return
        }
        if (attacker != null) recordDamage(entity, entry, attacker)
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        val entity = event.entity
        val entry = bossEntry(entity) ?: return
        if (!BossEventsStore.unlocked(entry.id)) {
            lockedDeathEntities.add(entity.uuid)
            (event.source.entity as? ServerPlayer)?.let { warnLocked(it, entry) }
            return
        }
        val contributors = contributors(entity, entry, event.source.entity as? ServerPlayer)
        fights.remove(entity.uuid)
        if (contributors.size < entry.requiredPlayers) {
            contributors.forEach { player ->
                SnackbarNetwork.send(player, SnackbarNotification.item(entry.iconItem, "BOSS CREW TOO SMALL", "Finn needs ${entry.requiredPlayers} players credited for ${entry.displayName}. Current: ${contributors.size}.", SnackbarType.ERROR, SnackbarSounds.ERROR))
            }
            return
        }
        BossEventsStore.recordClear(entry, contributors)
        announceClear((entity.level() as? ServerLevel)?.server ?: return, entry, contributors)
        contributors.forEach { player ->
            SnackbarNetwork.send(player, SnackbarNotification.item(entry.iconItem, "BOSS CONTRACT READY", "Talk to Finn to claim ${entry.displayName}.", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            NpcQuestService.syncTo(player)
        }
    }

    private fun onLivingDrops(event: LivingDropsEvent) {
        val entity = event.entity
        val entry = bossEntry(entity)
        if (entity.uuid in lockedDeathEntities || (entry != null && !BossEventsStore.unlocked(entry.id))) {
            event.drops.clear()
            lockedDeathEntities.remove(entity.uuid)
        }
    }

    private fun bossEntry(entity: LivingEntity): BossEventEntry? {
        val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
        return BossEventsConfig.entryForEntity(entityId)
    }

    private fun recordDamage(entity: LivingEntity, entry: BossEventEntry, attacker: ServerPlayer) {
        val now = entity.level().gameTime
        val fight = fights.getOrPut(entity.uuid) { BossFightRecord(entry.id, entity.uuid, now) }
        fight.damagedBy[attacker.uuid] = now
        NpcQuestService.syncTo(attacker)
    }

    private fun contributors(entity: LivingEntity, entry: BossEventEntry, killer: ServerPlayer?): List<ServerPlayer> {
        val level = entity.level() as? ServerLevel ?: return killer?.let(::listOf).orEmpty()
        val now = level.gameTime
        val window = BossEventsConfig.settings().participationWindowTicks
        val radius = BossEventsConfig.settings().participationRadius
        val damageIds = fights[entity.uuid]?.damagedBy.orEmpty()
            .filterValues { tick -> now - tick <= window }
            .keys
        return level.players().asSequence()
            .filter { player -> player.isAlive && !player.isSpectator }
            .filter { player -> player.uuid == killer?.uuid || player.uuid in damageIds || player.distanceToSqr(entity) <= radius * radius }
            .distinctBy { it.uuid }
            .sortedBy { it.gameProfile.name.lowercase(Locale.ROOT) }
            .toList()
    }

    private fun warnNearbyLocked(entity: LivingEntity, entry: BossEventEntry) {
        val level = entity.level() as? ServerLevel ?: return
        val radius = BossEventsConfig.settings().participationRadius
        level.players()
            .filter { player -> player.distanceToSqr(entity) <= radius * radius }
            .forEach { player -> warnLocked(player, entry) }
    }

    private fun warnLocked(player: ServerPlayer, entry: BossEventEntry) {
        val now = player.level().gameTime
        val key = "${player.stringUUID}:${entry.id}"
        if ((nextLockedWarningAt[key] ?: 0L) > now) return
        nextLockedWarningAt[key] = now + BossEventsConfig.settings().lockedWarningCooldownTicks
        SnackbarNetwork.send(
            player,
            SnackbarNotification.item(
                entry.iconItem,
                "BOSS LOCKED",
                render(entry.finnLockedWarning, entry, player = player, total = ShippingBinStore.totalChowcoinsSold()),
                SnackbarType.ERROR,
                SnackbarSounds.ERROR,
            ),
        )
    }

    private fun openClaimDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, entry: BossEventEntry) {
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val fallback = render(entry.finnClaimDialog, entry, player = player, total = ShippingBinStore.totalChowcoinsSold())
        val llmEnabled = NpcConfig.settings().llm.enabled
        val token = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(
            player,
            NpcFeature.dialogPayload(
                definition,
                npc,
                if (llmEnabled) "..." else fallback,
                false,
                friendship.level,
                closeLabel = "LATER",
                responseToken = token,
                dialogMode = "boss_claim",
            ),
        )
        if (llmEnabled) {
            NpcLlmService.event(
                player,
                npc,
                definition,
                fallback,
                bossPrompt(player, entry),
                inputLabel = "Finn boss claim",
                npcRecordType = "npc_boss_claim_offer",
                responseToken = token,
            )
        } else {
            NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_boss_claim_offer")
        }
    }

    private fun claim(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, entry: BossEventEntry) {
        if (!BossEventsStore.claim(player, entry.id)) return
        val xp = entry.firstClearXp
        val coins = entry.firstClearChowcoins
        if (xp > 0) BattlepassXpStore.addXp(player, "combat", xp)
        if (coins > 0L) {
            ChowcoinStore.add(player, coins)
            ChowcoinNetwork.syncTo(player)
        }
        BattlepassNetwork.syncAllPlayers()
        NpcQuestService.syncTo(player)
        val message = "Contract paid: +$xp Combat XP, +$coins Chowcoins for ${entry.displayName}."
        SnackbarNetwork.send(player, SnackbarNotification.item(entry.iconItem, "BOSS REWARD CLAIMED", message, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, message, false, friendship.level, closeOnly = true, closeLabel = "OKAY"))
    }

    private fun announceUnlock(server: MinecraftServer, entry: BossEventEntry, total: Long) {
        val message = render(entry.finnUnlockAnnouncement, entry, total = total)
        broadcastFinn(server, message)
        SnackbarNetwork.sendToAllKnown(server, SnackbarNotification.item(entry.iconItem, "FINN BOSS CONTRACT", "${entry.displayName} unlocked.", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        sendDiscord(message)
    }

    private fun announceClear(server: MinecraftServer, entry: BossEventEntry, contributors: List<ServerPlayer>) {
        val names = contributors.joinToString(", ") { it.gameProfile.name }
        val message = render(entry.finnClearBroadcast, entry, total = ShippingBinStore.totalChowcoinsSold()) + " Crew: $names."
        broadcastFinn(server, message)
        sendDiscord(message)
        syncAll(server)
    }

    private fun broadcastFinn(server: MinecraftServer, message: String) {
        val finn = NpcConfig.get(BossEventsConfig.settings().finnNpcId)
        val npcName = finn?.name ?: "Finn"
        NpcNetwork.broadcastWorldChat(server, NpcWorldChatPayload(BossEventsConfig.settings().finnNpcId, npcName, "", null, "thinking", message))
        server.playerList.broadcastSystemMessage(Component.literal("$npcName: $message"), false)
    }

    private fun sendDiscord(message: String) {
        val url = BossEventsConfig.settings().eventsWebhookUrl
        if (url.isBlank()) return
        DiscordWebhookClient.sendTo(url, message, username = "Finn")
    }

    private fun claimableEntries(player: ServerPlayer): List<NpcQuestHudEntryPayload> =
        BossEventsStore.claimableBosses(player).take(1).map { entry -> hudEntry(entry, entry.requiredPlayers, entry.requiredPlayers, claim = true) }

    private fun hudEntry(entry: BossEventEntry, progress: Int, goal: Int, claim: Boolean = false): NpcQuestHudEntryPayload =
        NpcQuestHudEntryPayload(
            npcId = BossEventsConfig.settings().finnNpcId,
            npcName = "Finn",
            description = if (claim) "Talk to Finn to claim ${entry.displayName}" else "Finn: Defeat ${entry.displayName}",
            passId = "combat",
            xp = entry.firstClearXp,
            chowcoins = entry.firstClearChowcoins,
            progress = progress,
            goal = goal,
            acceptedAtTick = 0L,
        )

    private fun syncAll(server: MinecraftServer) {
        server.playerList.players.forEach(NpcQuestService::syncTo)
    }

    private fun bossPrompt(player: ServerPlayer, entry: BossEventEntry): String {
        val next = BossEventsConfig.nextAfter(entry)
        return """
            Boss contract claim context for Finn.
            Player: ${player.gameProfile.name}
            Boss: ${entry.displayName}
            Boss id: ${entry.id}
            Boss order: ${entry.order}
            Threshold: ${entry.thresholdChowcoins}
            Total shipped Chowcoins: ${ShippingBinStore.totalChowcoinsSold()}
            Reward XP: ${entry.firstClearXp}
            Reward Chowcoins: ${entry.firstClearChowcoins}
            Next boss: ${next?.displayName ?: "none"}
            Reply as Finn. Keep it short, excited, and in character. Tell the player to claim the reward.
        """.trimIndent()
    }

    private fun render(template: String, entry: BossEventEntry, player: ServerPlayer? = null, total: Long): String = template
        .replace("{boss}", entry.displayName)
        .replace("{boss_id}", entry.id)
        .replace("{threshold}", String.format(Locale.US, "%,d", entry.thresholdChowcoins))
        .replace("{total_shipped_chowcoins}", String.format(Locale.US, "%,d", total))
        .replace("{player}", player?.gameProfile?.name.orEmpty())
        .replace("{reward_xp}", entry.firstClearXp.toString())
        .replace("{reward_chowcoins}", entry.firstClearChowcoins.toString())
        .replace("{boss_order}", entry.order.toString())
        .replace("{next_boss}", BossEventsConfig.nextAfter(entry)?.displayName ?: "none")

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("ck").then(root()))
        event.dispatcher.register(Commands.literal("chowkingdom").then(root()))
    }

    private fun root() = Commands.literal("bosses")
        .then(Commands.literal("status").requires { it.hasPermission(2) }.executes(::status))
        .then(Commands.literal("reload").requires { it.hasPermission(2) }.executes(::reload))
        .then(Commands.literal("unlock").requires { it.hasPermission(2) }.then(bossArgument().executes(::unlock)))
        .then(Commands.literal("reset").requires { it.hasPermission(2) }.then(bossArgument().then(Commands.literal("confirm").executes(::reset))))
        .then(
            Commands.literal("credit").requires { it.hasPermission(2) }
                .then(Commands.literal("get").then(bossArgument().then(Commands.argument("player", EntityArgument.player()).executes(::creditGet))))
                .then(Commands.literal("set").then(bossArgument().then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("value", BoolArgumentType.bool()).executes(::creditSet))))),
        )

    private fun bossArgument() = Commands.argument("boss_id", StringArgumentType.string())
        .suggests { _, builder -> SharedSuggestionProvider.suggest(BossEventsConfig.entries().map { it.id }, builder) }

    private fun bossFrom(context: CommandContext<CommandSourceStack>): BossEventEntry? {
        val raw = StringArgumentType.getString(context, "boss_id")
        return BossEventsConfig.entry(raw).also {
            if (it == null) context.source.sendFailure(Component.literal("Unknown boss: $raw"))
        }
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        BossEventsStore.statusLines().forEach { line -> context.source.sendSuccess({ Component.literal(line) }, false) }
        context.source.sendSuccess({ Component.literal("Total shipped Chowcoins: ${ShippingBinStore.totalChowcoinsSold()}") }, false)
        return 1
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        BossEventsConfig.load()
        BossEventsStore.load()
        checkShippingUnlocks(context.source.server)
        context.source.sendSuccess({ Component.literal("Reloaded ${BossEventsConfig.entries().size} boss event(s).") }, true)
        return 1
    }

    private fun unlock(context: CommandContext<CommandSourceStack>): Int {
        val entry = bossFrom(context) ?: return 0
        val changed = BossEventsStore.unlock(entry.id)
        if (changed) announceUnlock(context.source.server, entry, ShippingBinStore.totalChowcoinsSold())
        context.source.sendSuccess({ Component.literal("${entry.id} unlocked=${BossEventsStore.unlocked(entry.id)}") }, true)
        syncAll(context.source.server)
        return 1
    }

    private fun reset(context: CommandContext<CommandSourceStack>): Int {
        val entry = bossFrom(context) ?: return 0
        BossEventsStore.resetBoss(entry.id)
        syncAll(context.source.server)
        context.source.sendSuccess({ Component.literal("Reset boss event ${entry.id}.") }, true)
        return 1
    }

    private fun creditGet(context: CommandContext<CommandSourceStack>): Int {
        val entry = bossFrom(context) ?: return 0
        val player = EntityArgument.getPlayer(context, "player")
        context.source.sendSuccess({ Component.literal("${player.gameProfile.name} ${entry.id}: credit=${BossEventsStore.hasCredit(player, entry.id)} claimed=${BossEventsStore.hasClaimed(player, entry.id)}") }, false)
        return 1
    }

    private fun creditSet(context: CommandContext<CommandSourceStack>): Int {
        val entry = bossFrom(context) ?: return 0
        val player = EntityArgument.getPlayer(context, "player")
        val value = BoolArgumentType.getBool(context, "value")
        BossEventsStore.setCredit(player, entry.id, value)
        NpcQuestService.syncTo(player)
        context.source.sendSuccess({ Component.literal("Set ${player.gameProfile.name} ${entry.id} credit=$value.") }, true)
        return 1
    }
}
