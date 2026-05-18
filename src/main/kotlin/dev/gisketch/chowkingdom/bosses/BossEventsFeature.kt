package dev.gisketch.chowkingdom.bosses

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
import java.util.concurrent.ConcurrentHashMap

object BossEventsFeature {
    private val fights: MutableMap<UUID, BossFightRecord> = linkedMapOf()
    private val lockedDeathEntities: MutableSet<UUID> = linkedSetOf()
    private val nextLockedWarningAt: MutableMap<String, Long> = linkedMapOf()
    private val bossTalkFocus = ConcurrentHashMap<String, BossTalkFocus>()

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
        claimableEntries(player).takeIf { it.isNotEmpty() }?.let { return it }
        val entry = activeContractEntry() ?: return emptyList()
        val fight = fights.values.firstOrNull { it.bossId == entry.id }
        val progress = maxOf(fight?.damagedBy?.size ?: 0, BossEventsStore.creditCount(entry.id))
        return listOf(hudEntry(entry, progress.coerceAtMost(entry.requiredPlayers), entry.requiredPlayers))
    }

    fun contractsAvailable(definition: NpcDefinition): Boolean =
        BossEventsConfig.settings().enabled && definition.id.lowercase(Locale.ROOT) == BossEventsConfig.settings().finnNpcId

    fun tryOpenFinnDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition): Boolean {
        if (definition.id.lowercase(Locale.ROOT) != BossEventsConfig.settings().finnNpcId) return false
        val entry = BossEventsStore.claimableBosses(player).firstOrNull() ?: return false
        showClaimBalloon(player, npc, entry)
        openClaimDialog(player, npc, definition, entry)
        return true
    }

    fun handleFinnAction(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition, action: String): Boolean {
        if (definition.id.lowercase(Locale.ROOT) != BossEventsConfig.settings().finnNpcId) return false
        return when (action.lowercase(Locale.ROOT)) {
            "boss_contracts" -> {
                openContractDialog(player, npc, definition)
                true
            }
            "boss_contract_talk" -> {
                focusBossTalk(player, definition.id)
                true
            }
            "boss_claim" -> {
                val entry = BossEventsStore.claimableBosses(player).firstOrNull()
                if (entry == null) {
                    openClaimUnavailableDialog(player, npc, definition)
                    true
                } else {
                    claim(player, npc, definition, entry)
                    true
                }
            }
            else -> false
        }
    }

    fun bossTalkContextFor(player: ServerPlayer, npcId: String): String {
        val focusKey = focusKey(player.uuid, npcId)
        val focus = bossTalkFocus[focusKey] ?: return lightBossContext(player, npcId)
        if (System.currentTimeMillis() > focus.expiresAtMs) {
            bossTalkFocus.remove(focusKey)
            return lightBossContext(player, npcId)
        }
        val entry = BossEventsConfig.entry(focus.bossId) ?: return lightBossContext(player, npcId)
        if (BossEventsStore.hasClaimed(player, entry.id) && BossEventsStore.clearedOrCreditedEnough(entry)) {
            bossTalkFocus.remove(focusKey)
            return lightBossContext(player, npcId)
        }
        return contractTalkContext(player, entry, primary = true)
    }

    private fun openContractDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val view = contractView(player)
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val entry = view.entry
        val fallback = contractDialogText(player, view)
        if (entry != null) focusBossTalk(player, definition.id, entry)
        val llmEnabled = NpcConfig.settings().llm.enabled && entry != null
        val token = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(
            player,
            NpcFeature.dialogPayload(
                definition,
                npc,
                if (llmEnabled) "..." else fallback,
                false,
                friendship.level,
                closeLabel = "BYE",
                responseToken = token,
                dialogMode = "boss_contract",
                bossClaimAvailable = view.claimAvailable,
            ),
        )
        if (llmEnabled) {
            val focusedEntry = entry
            val context = contractTalkContext(player, focusedEntry, primary = true)
            val instruction = if (view.state == "locked") {
                "Open the boss contract screen. Finn has no clean contract yet. Reply in character that he is scouting for strange trouble and needs more adventure before he pins another fight. Do not mention shipping, Chowcoins, thresholds, hidden boss ids, required players, or the next boss."
            } else {
                "Open the boss contract screen. Explain the contract in character and invite the player to ask questions or claim if they have credit."
            }
            NpcLlmService.event(
                player,
                npc,
                definition,
                fallback,
                "$context\n\n$instruction",
                inputLabel = "Finn boss contract screen",
                npcRecordType = "npc_boss_contract",
                responseToken = token,
            )
        } else {
            NpcStore.recordConversation(definition.id, player, definition.name, fallback, "npc_boss_contract")
        }
    }

    private fun openClaimUnavailableDialog(player: ServerPlayer, npc: ChowNpcEntity, definition: NpcDefinition) {
        val view = contractView(player)
        val entry = view.entry
        val message = if (entry != null) {
            render(entry.finnClaimUnavailableDialog, entry, player = player, total = ShippingBinStore.totalChowcoinsSold())
        } else {
            "No contract reward is ready yet, ${player.gameProfile.name}. I am still watching the horizon."
        }
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        NpcNetwork.openDialog(
            player,
            NpcFeature.dialogPayload(
                definition,
                npc,
                message,
                false,
                friendship.level,
                closeLabel = "BYE",
                dialogMode = "boss_contract",
                bossClaimAvailable = false,
            ),
        )
    }

    private fun contractView(player: ServerPlayer): BossContractView {
        BossEventsStore.claimableBosses(player).firstOrNull()?.let { entry -> return BossContractView("claimable", entry, claimAvailable = true) }
        activeContractEntry()?.let { entry -> return BossContractView("active", entry) }
        BossEventsConfig.entries().firstOrNull { entry -> !BossEventsStore.unlocked(entry.id) }?.let { entry -> return BossContractView("locked", entry) }
        BossEventsConfig.entries().lastOrNull { entry -> BossEventsStore.clearedOrCreditedEnough(entry) }?.let { entry -> return BossContractView("complete", entry) }
        return BossContractView("empty", null)
    }

    private fun activeContractEntry(): BossEventEntry? =
        BossEventsConfig.entries().firstOrNull { entry -> BossEventsStore.unlocked(entry.id) && !BossEventsStore.clearedOrCreditedEnough(entry) }

    private fun contractDialogText(player: ServerPlayer, view: BossContractView): String {
        val entry = view.entry ?: return "No contracts on the board yet. I am watching the roads, the ruins, and the weird noises under the map."
        return when (view.state) {
            "claimable" -> render(entry.finnClaimDialog, entry, player = player, total = ShippingBinStore.totalChowcoinsSold())
            "active" -> render(entry.finnContractDialog, entry, player = player, total = ShippingBinStore.totalChowcoinsSold())
            "locked" -> "I do not have a clean contract yet. The roads are quiet in that suspicious way. Go have an adventure; I will shout when I find something ugly enough for the board."
            "complete" -> "The ${entry.displayName} contract is closed. I logged the crew, checked the scratches, and started watching for the next shadow."
            else -> entry.description.ifBlank { entry.displayName }
        }
    }

    private fun focusBossTalk(player: ServerPlayer, npcId: String, entry: BossEventEntry? = null) {
        if (npcId.lowercase(Locale.ROOT) != BossEventsConfig.settings().finnNpcId) return
        val target = entry ?: contractView(player).entry ?: return
        bossTalkFocus[focusKey(player.uuid, npcId)] = BossTalkFocus(target.id, System.currentTimeMillis() + BOSS_TALK_FOCUS_MS)
    }

    private fun lightBossContext(player: ServerPlayer, npcId: String): String {
        if (npcId.lowercase(Locale.ROOT) != BossEventsConfig.settings().finnNpcId) return ""
        val view = contractView(player)
        val entry = view.entry ?: return ""
        if (view.state == "locked") {
            return """
                Light Finn boss context:
                - Current contract state: locked
                - Finn has not found a clean new boss contract yet.
                If this comes up, say he is scouting for weird trouble and the town needs more adventure before he pins another fight.
                Do not mention shipping, Chowcoins, thresholds, hidden boss ids, required players, or the next boss.
            """.trimIndent()
        }
        return """
            Light Finn boss context:
            - Current contract state: ${view.state}
            - Boss: ${entry.displayName}
            Mention this only if it naturally fits. Do not force the whole conversation onto the boss contract unless the player asks.
        """.trimIndent()
    }

    private fun contractTalkContext(player: ServerPlayer, entry: BossEventEntry, primary: Boolean): String {
        val status = when {
            BossEventsStore.hasCredit(player, entry.id) && !BossEventsStore.hasClaimed(player, entry.id) -> "claimable"
            !BossEventsStore.unlocked(entry.id) -> "locked"
            BossEventsStore.clearedOrCreditedEnough(entry) -> "cleared"
            else -> "active"
        }
        if (status == "locked") {
            return """
                ${if (primary) "Primary" else "Light"} Finn boss contract context:
                - Status for ${player.gameProfile.name}: locked
                - Finn has not found a clean new boss contract yet.
                - Finn's lore: he runs into threats while scouting, exploring, and chasing weird reports. When he finds one, he pins the contract and announces it.
                - Current guidance: tell the player to keep adventuring, exploring, and checking back.
                Do not mention shipping, Chowcoins, thresholds, hidden boss ids, required players, or the next boss.
                Do not name the locked boss.
            """.trimIndent()
        }
        val next = BossEventsConfig.nextAfter(entry)
        return """
            ${if (primary) "Primary" else "Light"} Finn boss contract context:
            - Boss: ${entry.displayName}
            - Boss id: ${entry.id}
            - Boss order: ${entry.order}
            - Status for ${player.gameProfile.name}: $status
            - Required credited players: ${entry.requiredPlayers}
            - Reward XP: ${entry.firstClearXp}
            - Reward Chowcoins: ${entry.firstClearChowcoins}
            - Lore: ${entry.lore}
            - Location hint: ${entry.locationHint}
            - Access hint: ${entry.accessHint}
            - Fight tips: ${entry.fightTips}
            - Next boss: ${next?.displayName ?: "none"}
            If this is primary context, keep the conversation focused on the contract, scouting, how to find the boss, and how to prepare.
        """.trimIndent()
    }

    private fun focusKey(playerId: UUID, npcId: String): String = "$playerId:${npcId.lowercase(Locale.ROOT)}"

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
        val crewNames = contributors.joinToString(", ") { it.gameProfile.name }
        NpcStore.recordGlobalEvent("boss_cleared", "${entry.displayName} was cleared by $crewNames.")
        NpcStore.recordGlobalMemory("boss_cleared", "Finn's ${entry.displayName} contract was cleared by $crewNames.")
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
        val fallback = claimOfferFallback(player, entry)
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
                closeLabel = "BYE",
                responseToken = token,
                dialogMode = "boss_contract",
                bossClaimAvailable = true,
            ),
        )
        if (llmEnabled) {
            NpcLlmService.event(
                player,
                npc,
                definition,
                fallback,
                bossClaimOfferPrompt(player, entry),
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
        bossTalkFocus.remove(focusKey(player.uuid, definition.id))
        val message = claimPaidFallback(entry)
        SnackbarNetwork.send(player, SnackbarNotification.item(entry.iconItem, "BOSS REWARD CLAIMED", stripDialogTags(message), SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        val friendship = NpcStore.friendshipSnapshot(definition.id, player)
        val llmEnabled = NpcConfig.settings().llm.enabled
        val token = if (llmEnabled) NpcDialogTokens.next() else 0L
        NpcNetwork.openDialog(player, NpcFeature.dialogPayload(definition, npc, if (llmEnabled) "..." else message, false, friendship.level, closeOnly = true, closeLabel = "OKAY", responseToken = token))
        if (llmEnabled) {
            NpcLlmService.event(
                player,
                npc,
                definition,
                message,
                bossClaimPaidPrompt(player, entry),
                inputLabel = "Finn boss reward paid",
                npcRecordType = "npc_boss_claim_paid",
                responseToken = token,
            )
        } else {
            NpcStore.recordConversation(definition.id, player, definition.name, message, "npc_boss_claim_paid")
        }
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
            description = if (claim) "Talk to Finn to claim your rewards" else "Finn: Defeat ${entry.displayName}",
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

    private fun claimOfferFallback(player: ServerPlayer, entry: BossEventEntry): String =
        render(entry.finnClaimDialog, entry, player = player, total = ShippingBinStore.totalChowcoinsSold()) +
            " Reward: <xp>+${entry.firstClearXp} Combat XP</xp> and <coin>+${entry.firstClearChowcoins} Chowcoins</coin>."

    private fun claimPaidFallback(entry: BossEventEntry): String =
        "Contract paid for <b>${entry.displayName}</b>: <xp>+${entry.firstClearXp} Combat XP</xp> and <coin>+${entry.firstClearChowcoins} Chowcoins</coin>."

    private fun bossClaimOfferPrompt(player: ServerPlayer, entry: BossEventEntry): String {
        val next = BossEventsConfig.nextAfter(entry)
        return """
            Boss contract claim-ready context for Finn.
            Player: ${player.gameProfile.name}
            Boss: ${entry.displayName}
            Boss id: ${entry.id}
            Boss order: ${entry.order}
            Threshold: ${entry.thresholdChowcoins}
            Total shipped Chowcoins: ${ShippingBinStore.totalChowcoinsSold()}
            Reward XP: ${entry.firstClearXp}
            Reward Chowcoins: ${entry.firstClearChowcoins}
            Next boss: ${next?.displayName ?: "none"}
            Reply as Finn. Keep it short, excited, and in character.
            Tell the player their reward is ready and point them to the CLAIM button.
            Wrap the XP reward with <xp>...</xp> and the Chowcoin reward with <coin>...</coin>.
            You may wrap the boss name or player name with <b>...</b>.
        """.trimIndent()
    }

    private fun bossClaimPaidPrompt(player: ServerPlayer, entry: BossEventEntry): String {
        val next = BossEventsConfig.nextAfter(entry)
        return """
            Boss contract reward-paid context for Finn.
            Player: ${player.gameProfile.name}
            Boss: ${entry.displayName}
            Reward XP paid: ${entry.firstClearXp}
            Reward Chowcoins paid: ${entry.firstClearChowcoins}
            Next boss: ${next?.displayName ?: "none"}
            Reply as Finn with a short congratulations line.
            Mention that payment is complete.
            Wrap the XP reward with <xp>...</xp> and the Chowcoin reward with <coin>...</coin>.
            You may wrap the boss name or player name with <b>...</b>.
        """.trimIndent()
    }

    private fun showClaimBalloon(player: ServerPlayer, npc: ChowNpcEntity, entry: BossEventEntry) {
        NpcNetwork.showBalloon(player, npc.id, "@quest_log.png I have your ${entry.displayName} contract reward ready.", 120)
    }

    private fun stripDialogTags(message: String): String = message.replace(Regex("(?i)</?(mission|coin|xp|player|b)>"), "")

    private fun render(template: String, entry: BossEventEntry, player: ServerPlayer? = null, total: Long): String = template
        .replace("{boss}", entry.displayName)
        .replace("{boss_id}", entry.id)
        .replace("{threshold}", String.format(Locale.US, "%,d", entry.thresholdChowcoins))
        .replace("{total_shipped_chowcoins}", String.format(Locale.US, "%,d", total))
        .replace("{player}", player?.gameProfile?.name.orEmpty())
        .replace("{reward_xp}", entry.firstClearXp.toString())
        .replace("{reward_chowcoins}", entry.firstClearChowcoins.toString())
        .replace("{boss_order}", entry.order.toString())
        .replace("{required_players}", entry.requiredPlayers.toString())
        .replace("{lore}", entry.lore)
        .replace("{location_hint}", entry.locationHint)
        .replace("{access_hint}", entry.accessHint)
        .replace("{fight_tips}", entry.fightTips)
        .replace("{next_boss}", BossEventsConfig.nextAfter(entry)?.displayName ?: "none")

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("ck").then(root()))
        event.dispatcher.register(Commands.literal("chowkingdom").then(root()))
    }

    private fun root() = Commands.literal("bosses")
        .then(Commands.literal("status").requires { it.hasPermission(2) }.executes(::status))
        .then(Commands.literal("reload").requires { it.hasPermission(2) }.executes(::reload))
        .then(Commands.literal("unlock").requires { it.hasPermission(2) }.then(bossTailArgument().executes(::unlock)))
        .then(Commands.literal("reset").requires { it.hasPermission(2) }.then(commandTailArgument().suggests { _, builder -> suggestBossesWithSuffix(builder, " confirm") }.executes(::reset)))
        .then(Commands.literal("required").requires { it.hasPermission(2) }.then(commandTailArgument().suggests { _, builder -> suggestRequiredTail(builder) }.executes(::requiredPlayers)))
        .then(
            Commands.literal("credit").requires { it.hasPermission(2) }
                .then(Commands.literal("get").then(commandTailArgument().suggests { context, builder -> suggestCreditTail(context, builder, includeValue = false) }.executes(::creditGet)))
                .then(Commands.literal("set").then(commandTailArgument().suggests { context, builder -> suggestCreditTail(context, builder, includeValue = true) }.executes(::creditSet))),
        )
        .then(Commands.argument("legacy_args", StringArgumentType.greedyString()).requires { it.hasPermission(2) }.executes(::legacyBossCommand))

    private fun bossTailArgument() = Commands.argument("boss_id", StringArgumentType.greedyString())
        .suggests { _, builder -> SharedSuggestionProvider.suggest(BossEventsConfig.entries().map { it.id }, builder) }

    private fun commandTailArgument() = Commands.argument("args", StringArgumentType.greedyString())

    private fun bossFrom(context: CommandContext<CommandSourceStack>, raw: String = StringArgumentType.getString(context, "boss_id")): BossEventEntry? {
        return BossEventsConfig.entry(raw).also {
            if (it == null) context.source.sendFailure(Component.literal("Unknown boss: $raw"))
        }
    }

    private fun splitLast(context: CommandContext<CommandSourceStack>, raw: String, usage: String): Pair<String, String>? {
        val parts = raw.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (parts.size < 2) {
            context.source.sendFailure(Component.literal("Usage: $usage"))
            return null
        }
        return parts.dropLast(1).joinToString(" ") to parts.last()
    }

    private fun splitLastTwo(context: CommandContext<CommandSourceStack>, raw: String, usage: String): Triple<String, String, String>? {
        val parts = raw.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (parts.size < 3) {
            context.source.sendFailure(Component.literal("Usage: $usage"))
            return null
        }
        return Triple(parts.dropLast(2).joinToString(" "), parts[parts.lastIndex - 1], parts.last())
    }

    private fun playerFrom(context: CommandContext<CommandSourceStack>, raw: String): ServerPlayer? {
        val value = raw.trim()
        val player = when (value) {
            "@s" -> runCatching { context.source.playerOrException }.getOrNull()
            else -> context.source.server.playerList.getPlayerByName(value)
                ?: runCatching { context.source.server.playerList.getPlayer(UUID.fromString(value)) }.getOrNull()
        }
        if (player == null) context.source.sendFailure(Component.literal("Unknown online player: $value"))
        return player
    }

    private fun boolFrom(context: CommandContext<CommandSourceStack>, raw: String): Boolean? {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> {
                context.source.sendFailure(Component.literal("Expected true or false: $raw"))
                null
            }
        }
    }

    private fun suggestBossesWithSuffix(builder: com.mojang.brigadier.suggestion.SuggestionsBuilder, suffix: String) =
        SharedSuggestionProvider.suggest(BossEventsConfig.entries().map { "${it.id}$suffix" }, builder)

    private fun suggestRequiredTail(builder: com.mojang.brigadier.suggestion.SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(listOf("all 1") + BossEventsConfig.entries().map { "${it.id} 1" }, builder)

    private fun suggestCreditTail(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder, includeValue: Boolean): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val players = context.source.server.playerList.players.map { it.gameProfile.name }
        val values = if (includeValue) listOf(" true", " false") else listOf("")
        val suggestions = BossEventsConfig.entries().flatMap { entry -> players.flatMap { player -> values.map { value -> "${entry.id} $player$value" } } }
        return SharedSuggestionProvider.suggest(suggestions, builder)
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
        val raw = StringArgumentType.getString(context, "args")
        val (bossId, confirm) = splitLast(context, raw, "/ck bosses reset <boss_id> confirm") ?: return 0
        if (!confirm.equals("confirm", ignoreCase = true)) {
            context.source.sendFailure(Component.literal("Usage: /ck bosses reset <boss_id> confirm"))
            return 0
        }
        val entry = bossFrom(context, bossId) ?: return 0
        BossEventsStore.resetBoss(entry.id)
        syncAll(context.source.server)
        context.source.sendSuccess({ Component.literal("Reset boss event ${entry.id}.") }, true)
        return 1
    }

    private fun creditGet(context: CommandContext<CommandSourceStack>): Int {
        val raw = StringArgumentType.getString(context, "args")
        val (bossId, playerName) = splitLast(context, raw, "/ck bosses credit get <boss_id> <player>") ?: return 0
        val entry = bossFrom(context, bossId) ?: return 0
        val player = playerFrom(context, playerName) ?: return 0
        context.source.sendSuccess({ Component.literal("${player.gameProfile.name} ${entry.id}: credit=${BossEventsStore.hasCredit(player, entry.id)} claimed=${BossEventsStore.hasClaimed(player, entry.id)}") }, false)
        return 1
    }

    private fun creditSet(context: CommandContext<CommandSourceStack>): Int {
        val raw = StringArgumentType.getString(context, "args")
        val (bossId, playerName, rawValue) = splitLastTwo(context, raw, "/ck bosses credit set <boss_id> <player> <true|false>") ?: return 0
        val entry = bossFrom(context, bossId) ?: return 0
        val player = playerFrom(context, playerName) ?: return 0
        val value = boolFrom(context, rawValue) ?: return 0
        BossEventsStore.setCredit(player, entry.id, value)
        syncAll(context.source.server)
        context.source.sendSuccess({ Component.literal("Set ${player.gameProfile.name} ${entry.id} credit=$value.") }, true)
        return 1
    }

    private fun legacyBossCommand(context: CommandContext<CommandSourceStack>): Int {
        val raw = StringArgumentType.getString(context, "legacy_args").trim()
        val match = Regex("(.+)\\s+credit\\s+(\\S+)\\s+(\\S+)", RegexOption.IGNORE_CASE).matchEntire(raw)
        if (match == null) {
            context.source.sendFailure(Component.literal("Usage: /ck bosses <boss_id> credit <player> <true|false>"))
            return 0
        }
        val entry = bossFrom(context, match.groupValues[1]) ?: return 0
        val player = playerFrom(context, match.groupValues[2]) ?: return 0
        val value = boolFrom(context, match.groupValues[3]) ?: return 0
        BossEventsStore.setCredit(player, entry.id, value)
        syncAll(context.source.server)
        context.source.sendSuccess({ Component.literal("Set ${player.gameProfile.name} ${entry.id} credit=$value.") }, true)
        return 1
    }

    private fun requiredPlayers(context: CommandContext<CommandSourceStack>): Int {
        val raw = StringArgumentType.getString(context, "args")
        val (target, rawCount) = splitLast(context, raw, "/ck bosses required <boss_id|all> <players>") ?: return 0
        val count = rawCount.toIntOrNull()
        if (count == null) {
            context.source.sendFailure(Component.literal("Expected player count 1-50: $rawCount"))
            return 0
        }
        val updated = BossEventsConfig.setRequiredPlayers(target, count)
        if (updated.isEmpty()) {
            context.source.sendFailure(Component.literal("Unknown boss: $target"))
            return 0
        }
        syncAll(context.source.server)
        val label = if (target.equals("all", ignoreCase = true)) "all bosses" else updated.first().id
        context.source.sendSuccess({ Component.literal("Set required players for $label to ${count.coerceIn(1, 50)}.") }, true)
        return 1
    }

    private data class BossContractView(val state: String, val entry: BossEventEntry?, val claimAvailable: Boolean = false)

    private data class BossTalkFocus(val bossId: String, val expiresAtMs: Long)

    private const val BOSS_TALK_FOCUS_MS = 5L * 60L * 1000L
}
