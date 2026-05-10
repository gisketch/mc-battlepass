package dev.gisketch.chowkingdom.roles

import com.mojang.blaze3d.systems.RenderSystem
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.inventory.Slot
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge

object RoleEquipmentOverlayClient {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onRenderGuiLayerPost)
    }

    @JvmStatic
    fun renderContainerSlotOverlay(guiGraphics: GuiGraphics, slot: Slot, x: Int, y: Int) {
        val playerId = BattlepassClientState.selfId() ?: Minecraft.getInstance().player?.uuid ?: return
        val activeClassIds = RolesClientState.activeClassIdsFor(playerId)
        if (activeClassIds.isEmpty()) return
        if (!slot.isActive || !slot.hasItem()) return
        if (!shouldGreyOut(activeClassIds, slot.item)) return
        renderLockedOverlay(guiGraphics, x, y)
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
            if (stack.isEmpty || !shouldGreyOut(activeClassIds, stack)) continue
            val x = left + HOTBAR_ITEM_X_OFFSET + slotIndex * HOTBAR_SLOT_SPACING
            renderLockedOverlay(event.guiGraphics, x, y)
        }
    }

    private fun shouldGreyOut(activeClassIds: Set<String>, stack: net.minecraft.world.item.ItemStack): Boolean {
        val syncedClasses = RolesClientState.classDefinitions()
        return if (syncedClasses.isNotEmpty()) {
            RoleClassEquipmentRules.shouldGreyOutForClassDefinitions(activeClassIds, syncedClasses, stack)
        } else {
            RoleClassEquipmentRules.shouldGreyOutForClasses(activeClassIds, stack)
        }
    }

    private fun renderLockedOverlay(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0.0f, 0.0f, OVERLAY_Z)
        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, GREY_OVERLAY)
        guiGraphics.blit(LOCKED_TEXTURE, x, y, SLOT_SIZE, SLOT_SIZE, 0.0f, 0.0f, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE, LOCKED_TEXTURE_SIZE)
        RenderSystem.enableDepthTest()
        pose.popPose()
    }

    private val LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/locked.png")
    private const val GREY_OVERLAY = 0xAA202020.toInt()
    private const val SLOT_SIZE = 16
    private const val LOCKED_TEXTURE_SIZE = 32
    private const val OVERLAY_Z = 300.0f
    private const val HOTBAR_SLOT_COUNT = 9
    private const val HOTBAR_HALF_WIDTH = 90
    private const val HOTBAR_SLOT_SPACING = 20
    private const val HOTBAR_ITEM_X_OFFSET = 2
    private const val HOTBAR_ITEM_BOTTOM_OFFSET = 19
}