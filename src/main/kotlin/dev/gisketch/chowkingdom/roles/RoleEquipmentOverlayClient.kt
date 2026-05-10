package dev.gisketch.chowkingdom.roles

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.mixin.AbstractContainerScreenAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.ResourceLocation
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
            val x = left + slot.x
            val y = top + slot.y
            event.guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, GREY_OVERLAY)
            event.guiGraphics.blit(LOCKED_TEXTURE, x, y, SLOT_SIZE, SLOT_SIZE, 0.0f, 0.0f, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE)
        }
    }

    private val LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
    private const val GREY_OVERLAY = 0xAA202020.toInt()
    private const val SLOT_SIZE = 16
    private const val LOCKED_TEXTURE_SIZE = 32
}