package dev.gisketch.chowkingdom.roles

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen
import net.minecraft.world.effect.MobEffectInstance
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions

class JobStatusClientEffectExtensions(private val jobSlot: Int) : IClientMobEffectExtensions {
    override fun renderInventoryIcon(effectInstance: MobEffectInstance, screen: EffectRenderingInventoryScreen<*>, guiGraphics: GuiGraphics, x: Int, y: Int, blitOffset: Int): Boolean {
        val status = RolesClientState.jobStatusFor(jobSlot, effectInstance.amplifier + 1) ?: return false
        return renderStatusIcon(guiGraphics, status.icon, x, y + INVENTORY_ICON_Y_OFFSET, INVENTORY_ICON_SIZE)
    }

    override fun renderGuiIcon(effectInstance: MobEffectInstance, gui: Gui, guiGraphics: GuiGraphics, x: Int, y: Int, z: Float, alpha: Float): Boolean {
        val status = RolesClientState.jobStatusFor(jobSlot, effectInstance.amplifier + 1) ?: return true
        return renderStatusIcon(guiGraphics, status.icon, x + GUI_ICON_X_OFFSET, y + GUI_ICON_Y_OFFSET, GUI_ICON_SIZE)
    }

    private fun renderStatusIcon(guiGraphics: GuiGraphics, icon: String, x: Int, y: Int, size: Int): Boolean {
        val stack = roleIconStack(icon)
        if (!stack.isEmpty) {
            guiGraphics.renderItem(stack, x, y)
            return true
        }
        val texture = roleIconTexture(icon) ?: return true
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(texture, x, y, size, size, 0.0f, 0.0f, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE)
        return true
    }

    override fun renderInventoryText(effectInstance: MobEffectInstance, screen: EffectRenderingInventoryScreen<*>, guiGraphics: GuiGraphics, x: Int, y: Int, blitOffset: Int): Boolean {
        val status = RolesClientState.jobStatusFor(jobSlot, effectInstance.amplifier + 1) ?: return false
        val font = Minecraft.getInstance().font
        guiGraphics.drawString(font, fitText(status.title, TITLE_MAX_WIDTH), x + TEXT_X, y + TITLE_Y, TITLE_COLOR)
        return true
    }

    private fun fitText(value: String, maxWidth: Int): String {
        val font = Minecraft.getInstance().font
        if (font.width(value) <= maxWidth) return value
        val ellipsis = "..."
        var text = value
        while (text.isNotEmpty() && font.width(text + ellipsis) > maxWidth) text = text.dropLast(1)
        return text.trimEnd() + ellipsis
    }

    private companion object {
        const val INVENTORY_ICON_Y_OFFSET = 7
        const val INVENTORY_ICON_SIZE = 18
        const val GUI_ICON_X_OFFSET = 4
        const val GUI_ICON_Y_OFFSET = 4
        const val GUI_ICON_SIZE = 16
        const val ICON_SOURCE_SIZE = 16
        const val TEXT_X = 28
        const val TITLE_Y = 11
        const val TITLE_MAX_WIDTH = 88
        const val TITLE_COLOR = 0xFFFFFF
    }
}