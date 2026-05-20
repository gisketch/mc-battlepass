package dev.gisketch.chowkingdom.compat

import net.bettercombat.api.MinecraftClient_BetterCombat
import net.bettercombat.api.client.BetterCombatClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.neoforged.fml.ModList
import java.util.UUID

object PunchyFirstPersonSuppressionBridge {
    private const val BETTER_COMBAT_GRACE_TICKS = 16

    private val shieldNParryClient by lazy { runCatching { ShieldNParryClientApi() }.getOrNull() }
    private var registeredBetterCombatEvents = false
    private var betterCombatSuppressPlayer: UUID? = null
    private var betterCombatSuppressUntilTick = 0

    @JvmStatic
    fun register() {
        if (registeredBetterCombatEvents || !ModList.get().isLoaded("bettercombat")) return
        registeredBetterCombatEvents = true
        runCatching {
            BetterCombatClientEvents.ATTACK_START.register(BetterCombatClientEvents.PlayerAttackStart { player, _ ->
                markBetterCombatAttack(player)
            })
            BetterCombatClientEvents.ATTACK_HIT.register(BetterCombatClientEvents.PlayerAttackHit { player, _, _, _ ->
                markBetterCombatAttack(player)
            })
        }
    }

    @JvmStatic
    fun shouldDisablePunchyFirstPerson(): Boolean {
        if (!ModList.get().isLoaded("punchy")) return false
        return shieldNParryVisualActive() || betterCombatAttackActive()
    }

    private fun shieldNParryVisualActive(): Boolean {
        if (!ModList.get().isLoaded("shieldnparry")) return false
        val api = shieldNParryClient ?: return false
        return runCatching { api.visualBlockTicks.getInt(null) > 0 }.getOrDefault(false)
    }

    private fun betterCombatAttackActive(): Boolean {
        if (!ModList.get().isLoaded("bettercombat")) return false
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        val bridge = minecraft as? MinecraftClient_BetterCombat ?: return withinBetterCombatGrace(player)
        return runCatching {
            bridge.getCurrentAttackHand() != null ||
                bridge.getUpswingTicks() > 0 ||
                bridge.isWeaponSwingInProgress() ||
                withinBetterCombatGrace(player)
        }.getOrDefault(withinBetterCombatGrace(player))
    }

    private fun markBetterCombatAttack(player: LocalPlayer) {
        val local = Minecraft.getInstance().player ?: return
        if (player.uuid != local.uuid) return
        betterCombatSuppressPlayer = player.uuid
        betterCombatSuppressUntilTick = player.tickCount + BETTER_COMBAT_GRACE_TICKS
    }

    private fun withinBetterCombatGrace(player: LocalPlayer): Boolean {
        if (betterCombatSuppressPlayer != player.uuid) return false
        val remaining = betterCombatSuppressUntilTick - player.tickCount
        if (remaining < 0) return false
        if (remaining > BETTER_COMBAT_GRACE_TICKS) {
            betterCombatSuppressPlayer = null
            betterCombatSuppressUntilTick = 0
            return false
        }
        return true
    }

    private class ShieldNParryClientApi {
        private val clientEventsClass = Class.forName("com.ashalex.shieldnparry.client.ClientModEvents")
        val visualBlockTicks = clientEventsClass.getDeclaredField("visualBlockTicks").also { it.isAccessible = true }
    }
}
