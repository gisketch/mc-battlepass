package dev.gisketch.chowkingdom.client

import dev.gisketch.chowkingdom.compat.ParagliderStaminaBridge
import dev.gisketch.chowkingdom.compat.StaminaCompatConfig
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.common.NeoForge

object ParagliderAttackInputClient {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onInteractionKey)
    }

    private fun onInteractionKey(event: InputEvent.InteractionKeyMappingTriggered) {
        if (!event.isAttack) return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.hitResult !is EntityHitResult) return
        val config = StaminaCompatConfig.values()
        if (!config.enabled) return
        if (ParagliderStaminaBridge.available(player) + 0.001 >= config.attackCost.coerceAtLeast(0.0)) return
        event.isCanceled = true
        event.setSwingHand(false)
    }
}