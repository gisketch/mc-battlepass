package dev.gisketch.chowkingdom.roles

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.mixin.AbstractContainerScreenAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge

object RoleEquipmentOverlayClient {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onScreenRenderPost)
        NeoForge.EVENT_BUS.addListener(::onRenderGuiLayerPost)
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
            renderLockedOverlay(event.guiGraphics, x, y)
        }
    }

    private fun onRenderGuiLayerPost(event: RenderGuiLayerEvent.Post) {
        if (event.name != VanillaGuiLayers.HOTBAR) return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val playerId = BattlepassClientState.selfId() ?: player.uuid
        val activeClassIds = RolesClientState.activeClassIdsFor(playerId)
        if (activeClassIds.isEmpty()) return
        val left = event.guiGraphics.guiWidth() / 2 - HOTBAR_HALF_WIDTH
        val y = event.guiGraphics.guiHeight() - HOTBAR_ITEM_BOTTOM_OFFSET
        for (slotIndex in 0 until HOTBAR_SLOT_COUNT) {
            val stack = player.inventory.getItem(slotIndex)
            if (stack.isEmpty || !RoleClassEquipmentRules.shouldGreyOutForClasses(activeClassIds, stack)) continue
            val x = left + HOTBAR_ITEM_X_OFFSET + slotIndex * HOTBAR_SLOT_SPACING
            renderLockedOverlay(event.guiGraphics, x, y)
        }
    }

    private fun renderLockedOverlay(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0.0f, 0.0f, OVERLAY_Z)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, GREY_OVERLAY)
        guiGraphics.blit(LOCKED_TEXTURE, x, y, SLOT_SIZE, SLOT_SIZE, 0.0f, 0.0f, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE)
        pose.popPose()
    }

    private val LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
    private const val GREY_OVERLAY = 0xAA202020.toInt()
    private const val SLOT_SIZE = 16
    private const val LOCKED_TEXTURE_SIZE = 32
    private const val OVERLAY_Z = 400.0f
    private const val HOTBAR_SLOT_COUNT = 9
    private const val HOTBAR_HALF_WIDTH = 90
    private const val HOTBAR_SLOT_SPACING = 20
    private const val HOTBAR_ITEM_X_OFFSET = 2
    private const val HOTBAR_ITEM_BOTTOM_OFFSET = 19
}