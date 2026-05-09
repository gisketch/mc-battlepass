package dev.gisketch.chowkingdom.roles

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen
import net.minecraft.world.effect.MobEffectInstance
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions

class JobStatusClientEffectExtensions(private val jobSlot: Int) : IClientMobEffectExtensions {
    override fun renderInventoryIcon(effectInstance: MobEffectInstance, screen: EffectRenderingInventoryScreen<*>, guiGraphics: GuiGraphics, x: Int, y: Int, blitOffset: Int): Boolean {
        val status = RolesClientState.jobStatusFor(jobSlot, effectInstance.amplifier + 1) ?: return false
        val stack = roleIconStack(status.icon)
        if (!stack.isEmpty) {
            guiGraphics.renderItem(stack, x, y + ICON_Y_OFFSET)
            return true
        }
        val texture = roleIconTexture(status.icon) ?: return false
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(texture, x, y + ICON_Y_OFFSET, ICON_SIZE, ICON_SIZE, 0.0f, 0.0f, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE)
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
        const val ICON_Y_OFFSET = 7
        const val ICON_SIZE = 18
        const val ICON_SOURCE_SIZE = 16
        const val TEXT_X = 28
        const val TITLE_Y = 11
        const val TITLE_MAX_WIDTH = 88
        const val TITLE_COLOR = 0xFFFFFF
    }
}