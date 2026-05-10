package dev.gisketch.chowkingdom.roles

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.mixin.AbstractContainerScreenAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

object RoleEquipmentOverlayClient {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onScreenRenderPost)
    }

    private fun onScreenRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        val playerId = BattlepassClientState.selfId() ?: Minecraft.getInstance().player?.uuid ?: return
        val activeClassIds = RolesClientState.activeClassIdsFor(playerId)
        if (activeClassIds.isEmpty()) return
        val accessor = screen as AbstractContainerScreenAccessor
        val left = accessor.chowkingdom_getLeftPos()
        val top = accessor.chowkingdom_getTopPos()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        screen.menu.slots.forEach { slot ->
            if (!slot.isActive || !slot.hasItem()) return@forEach
            if (!RoleClassEquipmentRules.shouldGreyOutForClasses(activeClassIds, slot.item)) return@forEach
            event.guiGraphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, GREY_OVERLAY)
        }
    }

    private const val GREY_OVERLAY = 0xAA202020.toInt()
}