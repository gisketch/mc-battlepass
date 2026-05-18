package dev.gisketch.chowkingdom.mobility

import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import java.util.UUID

object MobilityLicenseFeature {
    private const val RIDING_LICENSE_NAME = "Riding License"
    private const val RIDING_LICENSE_ICON = "minecraft:saddle"
    private const val DENIAL_COOLDOWN_TICKS = 40L
    private val recentRideDenials: MutableMap<UUID, Long> = linkedMapOf()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
    }

    fun isLicenseReward(type: String): Boolean = type.equals("license", ignoreCase = true)

    fun licenseDisplayName(licenseId: String, configuredName: String? = null): String =
        configuredName?.trim()?.takeIf(String::isNotBlank) ?: when (normalizeLicenseId(licenseId)) {
            MobilityLicenseStore.RIDING_LICENSE -> RIDING_LICENSE_NAME
            else -> licenseId.trim().ifBlank { "License" }
        }

    fun grantReward(player: ServerPlayer, licenseId: String, source: String): Boolean {
        val id = normalizeLicenseId(licenseId)
        if (id != MobilityLicenseStore.RIDING_LICENSE) return false
        val changed = MobilityLicenseStore.grantRidingLicense(player, source)
        if (changed) {
            SnackbarNetwork.send(
                player,
                SnackbarNotification.item(RIDING_LICENSE_ICON, "RIDING LICENSE UNLOCKED", "Cobblemon riding is now available", SnackbarType.SUCCESS, SnackbarSounds.REWARD),
            )
        }
        return true
    }

    fun handleCobblemonRidePre(event: Any) {
        runCatching {
            val player = event.javaClass.getMethod("getPlayer").invoke(event) as? ServerPlayer ?: return
            if (MobilityLicenseStore.hasRidingLicense(player)) return
            event.javaClass.getMethod("cancel").invoke(event)
            sendRideDenied(player)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Cobblemon riding license gate unavailable", exception)
        }
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        MobilityLicenseStore.load()
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("ck")
                .then(
                    Commands.literal("mobility")
                        .requires { source -> source.hasPermission(2) }
                        .then(
                            Commands.literal("license")
                                .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(::getLicense)))
                                .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player()).executes(::grantLicense)))
                                .then(Commands.literal("revoke").then(Commands.argument("player", EntityArgument.player()).executes(::revokeLicense))),
                        ),
                ),
        )
    }

    private fun getLicense(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val hasLicense = MobilityLicenseStore.hasRidingLicense(player)
        context.source.sendSuccess({ Component.literal("${player.gameProfile.name}: riding_license=$hasLicense") }, false)
        return if (hasLicense) 1 else 0
    }

    private fun grantLicense(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val changed = MobilityLicenseStore.grantRidingLicense(player, "admin")
        context.source.sendSuccess({ Component.literal("${if (changed) "Granted" else "Already had"} Riding License for ${player.gameProfile.name}.") }, true)
        return if (changed) 1 else 0
    }

    private fun revokeLicense(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val changed = MobilityLicenseStore.revoke(player, MobilityLicenseStore.RIDING_LICENSE)
        context.source.sendSuccess({ Component.literal("${if (changed) "Revoked" else "No active"} Riding License for ${player.gameProfile.name}.") }, true)
        return if (changed) 1 else 0
    }

    private fun sendRideDenied(player: ServerPlayer) {
        val now = player.level().gameTime
        val previous = recentRideDenials[player.uuid]
        if (previous != null && now - previous < DENIAL_COOLDOWN_TICKS) return
        recentRideDenials[player.uuid] = now
        recentRideDenials.entries.removeIf { (_, tick) -> now - tick > DENIAL_COOLDOWN_TICKS * 4 }
        SnackbarNetwork.send(
            player,
            SnackbarNotification.item(RIDING_LICENSE_ICON, "RIDING LICENSE REQUIRED", "Reach Cozy Pass level 45 and claim your Riding License", SnackbarType.ERROR, SnackbarSounds.ERROR),
        )
    }

    private fun normalizeLicenseId(value: String): String = value.trim().lowercase()
}
