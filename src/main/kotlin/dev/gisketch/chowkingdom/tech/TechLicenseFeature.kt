package dev.gisketch.chowkingdom.tech

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.battlepass.BattlepassNetwork
import dev.gisketch.chowkingdom.npc.NpcConfig
import dev.gisketch.chowkingdom.npc.NpcDefinition
import dev.gisketch.chowkingdom.npc.NpcFeature
import dev.gisketch.chowkingdom.npc.NpcStore
import dev.gisketch.chowkingdom.shipping.ShippingBinStore
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
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.Locale

object TechLicenseFeature {
    const val TECH_LICENSE_UNLOCKED_EVENT = "gisketchs_chowkingdom_mod:tech_license_unlocked"
    private const val DENIAL_COOLDOWN_TICKS = 40L
    private const val EQUIPMENT_CHECK_INTERVAL = 20
    private val recentDenials: MutableMap<String, Long> = linkedMapOf()

    fun register() {
        TechLicenseConfig.load()
        TechLicenseStore.load()
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickItem)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickBlock)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onUseItemStart)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onBlockPlace)
        NeoForge.EVENT_BUS.addListener(::onEquipmentChange)
        NeoForge.EVENT_BUS.addListener(::onPlayerTickPost)
        NeoForge.EVENT_BUS.addListener(::onItemTooltip)
    }

    fun currentShippingTotal(): Long = ShippingBinStore.totalChowcoinsSold()

    fun thresholdReached(license: TechLicenseDefinition): Boolean =
        ShippingBinStore.totalChowcoinsSold() >= license.thresholdChowcoins || TechLicenseStore.pending(license.id) || TechLicenseStore.spawned(license.id)

    fun checkShippingUnlocks(server: MinecraftServer) {
        if (!TechLicenseConfig.enabled()) return
        val total = ShippingBinStore.totalChowcoinsSold()
        TechLicenseConfig.all()
            .filter { license -> total >= license.thresholdChowcoins && !TechLicenseStore.spawned(license.id) }
            .forEach { license -> TechLicenseStore.markPending(license.id) }
        spawnPending(server)
    }

    fun dialogOption(player: ServerPlayer, definition: NpcDefinition, workplaceReady: Boolean): TechLicenseDialogOption? {
        if (!TechLicenseConfig.enabled() || !workplaceReady) return null
        val license = TechLicenseConfig.forNpc(definition.id) ?: return null
        if (!thresholdReached(license) || TechLicenseStore.has(player, license.id)) return null
        return TechLicenseDialogOption(
            licenseId = license.id,
            label = license.buttonLabel,
            iconItem = license.iconItem,
            cost = license.feeChowcoins,
        )
    }

    fun handleDialogAction(player: ServerPlayer, npcId: String, action: String): Boolean {
        val normalized = action.lowercase(Locale.ROOT)
        if (!normalized.startsWith("tech_license:")) return false
        val definition = NpcConfig.get(npcId) ?: return true
        val npc = NpcFeature.existingConfiguredNpc(player.server, definition.id) ?: return true
        if (npc.level() != player.level() || player.distanceToSqr(npc) > 9.0 * 9.0) return true
        TechLicenseQuestService.handle(player, npc, definition, normalized.substringAfter(':'))
        return true
    }

    fun canSelectMission(player: ServerPlayer, missionRequiredLicense: String): Boolean {
        val licenseId = TechLicenseConfig.normalizeId(missionRequiredLicense)
        return licenseId.isBlank() || TechLicenseStore.has(player, licenseId)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        TechLicenseConfig.load()
        TechLicenseStore.load()
        checkShippingUnlocks(event.server)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        if (!TechLicenseConfig.enabled()) return
        val interval = TechLicenseConfig.settings().retryPendingSpawnIntervalTicks.coerceAtLeast(20)
        if (event.server.tickCount % interval != 0) return
        checkShippingUnlocks(event.server)
    }

    private fun spawnPending(server: MinecraftServer) {
        val pending = TechLicenseStore.pendingLicenseIds()
        if (pending.isEmpty()) return
        val level = server.overworld()
        val camp = NpcStore.campBlockPos() ?: return
        if (NpcFeature.hasActiveUnhousedCamper(server)) return
        val license = TechLicenseConfig.all()
            .filter { entry -> entry.id in pending }
            .sortedBy { entry -> entry.thresholdChowcoins }
            .firstOrNull()
            ?: return
        NpcConfig.load()
        val definition = NpcConfig.get(license.npcId) ?: return
        if (NpcStore.homePos(definition.id) != null || NpcStore.activeCamperId().equals(definition.id, ignoreCase = true)) {
            TechLicenseStore.markSpawned(license.id)
            return
        }
        if (!NpcFeature.spawnConfiguredNpcAsCamper(level, definition, camp, announceCamperArrival = true)) return
        TechLicenseStore.markSpawned(license.id)
        SnackbarNetwork.sendToAllKnown(
            server,
            SnackbarNotification.npc(definition.id, "TECH EXPERT UNLOCKED", "${definition.name} can certify ${license.displayName}.", SnackbarType.SUCCESS, SnackbarSounds.REWARD),
        )
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("ck")
                .then(
                    Commands.literal("techlicenses")
                        .requires { source -> source.hasPermission(2) }
                        .then(Commands.literal("reload").executes(::reload))
                        .then(Commands.literal("check").executes(::check))
                        .then(Commands.literal("audit").then(Commands.argument("license", StringArgumentType.word()).suggests(::suggestLicenses).executes(::audit)))
                        .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player()).executes(::status)))
                        .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("license", StringArgumentType.word()).suggests(::suggestLicenses).executes(::grant))))
                        .then(Commands.literal("revoke").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("license", StringArgumentType.word()).suggests(::suggestLicenses).executes(::revoke))))
                        .then(Commands.literal("resetspawn").then(Commands.argument("license", StringArgumentType.word()).suggests(::suggestLicenses).executes(::resetSpawn))),
                ),
        )
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        TechLicenseConfig.load()
        TechLicenseStore.load()
        NpcConfig.load()
        context.source.sendSuccess({ Component.literal("Reloaded ${TechLicenseConfig.all().size} tech license(s).") }, true)
        return TechLicenseConfig.all().size.coerceAtLeast(1)
    }

    private fun check(context: CommandContext<CommandSourceStack>): Int {
        checkShippingUnlocks(context.source.server)
        context.source.sendSuccess({ Component.literal("Checked tech license unlocks at ${ShippingBinStore.totalChowcoinsSold()} shipped Chowcoins.") }, true)
        return 1
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val licenses = TechLicenseConfig.all().joinToString(", ") { license -> "${license.id}=${TechLicenseStore.has(player, license.id)}" }
        context.source.sendSuccess({ Component.literal("${player.gameProfile.name}: $licenses") }, false)
        return 1
    }

    private fun grant(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val licenseId = StringArgumentType.getString(context, "license")
        val license = TechLicenseConfig.get(licenseId) ?: return unknownLicense(context, licenseId)
        val changed = TechLicenseStore.grant(player, license.id, "admin")
        if (changed) {
            SnackbarNetwork.send(player, SnackbarNotification.item(license.iconItem, "TECH LICENSE UNLOCKED", license.displayName, SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            BattlepassNetwork.syncAllPlayers()
        }
        context.source.sendSuccess({ Component.literal("${if (changed) "Granted" else "Already had"} ${license.displayName} for ${player.gameProfile.name}.") }, true)
        return if (changed) 1 else 0
    }

    private fun revoke(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val licenseId = StringArgumentType.getString(context, "license")
        val license = TechLicenseConfig.get(licenseId) ?: return unknownLicense(context, licenseId)
        val changed = TechLicenseStore.revoke(player, license.id)
        context.source.sendSuccess({ Component.literal("${if (changed) "Revoked" else "No active"} ${license.displayName} for ${player.gameProfile.name}.") }, true)
        return if (changed) 1 else 0
    }

    private fun resetSpawn(context: CommandContext<CommandSourceStack>): Int {
        val licenseId = StringArgumentType.getString(context, "license")
        val license = TechLicenseConfig.get(licenseId) ?: return unknownLicense(context, licenseId)
        val changed = TechLicenseStore.resetThresholdState(license.id)
        context.source.sendSuccess({ Component.literal("${if (changed) "Reset" else "No state for"} ${license.displayName} spawn threshold.") }, true)
        return if (changed) 1 else 0
    }

    private fun audit(context: CommandContext<CommandSourceStack>): Int {
        val licenseId = StringArgumentType.getString(context, "license")
        val license = TechLicenseConfig.get(licenseId) ?: return unknownLicense(context, licenseId)
        val itemMatches = BuiltInRegistries.ITEM.keySet().mapNotNull { id ->
            if (license.gateNamespaces.any { namespace -> id.namespace == namespace }) id.toString() else null
        }
        val blockMatches = BuiltInRegistries.BLOCK.keySet().mapNotNull { id ->
            if (license.gateNamespaces.any { namespace -> id.namespace == namespace }) id.toString() else null
        }
        val warningTerms = listOf("drygmy", "flight", "ritual", "source", "wixie", "starbuncle", "bookwyrm", "jar", "spell", "break", "place", "exchange", "interact")
        val warnings = (itemMatches + blockMatches).filter { id -> warningTerms.any { term -> id.contains(term, ignoreCase = true) } }.take(40)
        context.source.sendSuccess(
            {
                Component.literal("${license.displayName}: ${itemMatches.size} item(s), ${blockMatches.size} block(s). Audit hits: ${warnings.joinToString(", ").ifBlank { "none" }}")
            },
            false,
        )
        return (itemMatches.size + blockMatches.size).coerceAtLeast(1)
    }

    private fun unknownLicense(context: CommandContext<CommandSourceStack>, licenseId: String): Int {
        context.source.sendFailure(Component.literal("Unknown tech license '$licenseId'. Known: ${TechLicenseConfig.all().joinToString(", ") { it.id }}"))
        return 0
    }

    private fun suggestLicenses(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(TechLicenseConfig.all().map { it.id }, builder)

    private fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity as? ServerPlayer ?: return
        val denial = denialForItem(player, event.itemStack) ?: return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.FAIL
        deny(player, denial)
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        val blockDenial = denialForBlock(player, player.level().getBlockState(event.pos).block)
        val itemDenial = denialForItem(player, event.itemStack)
        val denial = blockDenial ?: itemDenial ?: return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.FAIL
        deny(player, denial)
    }

    private fun onUseItemStart(event: LivingEntityUseItemEvent.Start) {
        val player = event.entity as? ServerPlayer ?: return
        val denial = denialForItem(player, event.item) ?: return
        event.isCanceled = true
        event.duration = 0
        deny(player, denial)
    }

    private fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val denial = denialForBlock(player, event.placedBlock.block) ?: return
        event.isCanceled = true
        deny(player, denial)
    }

    private fun onEquipmentChange(event: LivingEquipmentChangeEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val denial = denialForItem(player, event.to) ?: return
        player.setItemSlot(event.slot, ItemStack.EMPTY)
        giveBack(player, event.to)
        deny(player, denial)
    }

    private fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.tickCount % EQUIPMENT_CHECK_INTERVAL != 0) return
        EquipmentSlot.values().forEach { slot ->
            val stack = player.getItemBySlot(slot)
            val denial = denialForItem(player, stack) ?: return@forEach
            player.setItemSlot(slot, ItemStack.EMPTY)
            giveBack(player, stack)
            deny(player, denial)
        }
    }

    private fun onItemTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        if (stack.isEmpty) return
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val license = licenseForItemId(id) ?: return
        val serverPlayer = event.entity as? ServerPlayer
        if (serverPlayer != null && TechLicenseStore.has(serverPlayer, license.id)) return
        event.toolTip.add(1.coerceAtMost(event.toolTip.size), Component.literal("Locked: ${license.displayName} required").withStyle(ChatFormatting.DARK_RED))
    }

    private fun denialForItem(player: ServerPlayer, stack: ItemStack): TechGateDenial? {
        if (stack.isEmpty) return null
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val license = licenseForItemId(id) ?: return null
        if (matchesAny(id, license.alwaysBannedItems)) return TechGateDenial(license, "Item is disabled by server config.")
        if (matchesAny(id, license.allowedWithoutLicense)) return null
        if (TechLicenseStore.has(player, license.id)) return null
        return TechGateDenial(license, "${license.displayName} required.")
    }

    private fun denialForBlock(player: ServerPlayer, block: Block): TechGateDenial? {
        val id = BuiltInRegistries.BLOCK.getKey(block).toString()
        val license = licenseForBlockId(id) ?: return null
        if (matchesAny(id, license.alwaysBannedBlocks)) return TechGateDenial(license, "Block is disabled by server config.")
        if (matchesAny(id, license.allowedBlocksWithoutLicense)) return null
        if (TechLicenseStore.has(player, license.id)) return null
        return TechGateDenial(license, "${license.displayName} required.")
    }

    private fun licenseForItemId(id: String): TechLicenseDefinition? =
        TechLicenseConfig.all().firstOrNull { license -> license.gateNamespaces.any { namespace -> id.substringBefore(':') == namespace } }

    private fun licenseForBlockId(id: String): TechLicenseDefinition? =
        TechLicenseConfig.all().firstOrNull { license -> license.gateNamespaces.any { namespace -> id.substringBefore(':') == namespace } }

    private fun matchesAny(id: String, patterns: List<String>): Boolean = patterns.any { pattern -> matchesPattern(id, pattern) }

    private fun matchesPattern(id: String, pattern: String): Boolean {
        val clean = pattern.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return false
        if (clean.endsWith(":*")) return id.substringBefore(':') == clean.substringBefore(':')
        if ('*' !in clean) return id == clean
        val regex = clean.split('*').joinToString(".*") { part -> Regex.escape(part) }.toRegex()
        return regex.matches(id)
    }

    private fun deny(player: ServerPlayer, denial: TechGateDenial) {
        val now = player.level().gameTime
        val key = "${player.uuid}:${denial.license.id}"
        val previous = recentDenials[key]
        if (previous != null && now - previous < DENIAL_COOLDOWN_TICKS) return
        recentDenials[key] = now
        recentDenials.entries.removeIf { (_, tick) -> now - tick > DENIAL_COOLDOWN_TICKS * 6 }
        SnackbarNetwork.send(player, SnackbarNotification.item(denial.license.iconItem, denial.license.displayName.uppercase(Locale.ROOT), denial.message, SnackbarType.ERROR, SnackbarSounds.ERROR))
        player.displayClientMessage(Component.literal(denial.message), true)
    }

    private fun giveBack(player: ServerPlayer, stack: ItemStack) {
        val copy = stack.copy()
        if (copy.isEmpty) return
        if (!player.inventory.add(copy)) player.drop(copy, false)
    }
}

data class TechLicenseDialogOption(
    val licenseId: String,
    val label: String,
    val iconItem: String,
    val cost: Long,
)

private data class TechGateDenial(
    val license: TechLicenseDefinition,
    val message: String,
)
