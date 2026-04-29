package dev.gisketch.chowkingdom.client

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent

object ChowKingdomHud {
    private val LAYER_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "player_hud")
    private val AVATAR_BORDER_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/gui/avatar-border.png")

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerGuiLayers)
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerAboveAll(LAYER_ID) { guiGraphics, _ -> render(guiGraphics) }
    }

    private fun render(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.options.hideGui || minecraft.screen != null) return

        val name = player.gameProfile.name
        val avatarX = HUD_PADDING
        val avatarY = HUD_PADDING
        val textX = avatarX + BORDER_SIZE + NAME_GAP
        val textY = avatarY + (BORDER_SIZE - minecraft.font.lineHeight) / 2

        guiGraphics.blit(AVATAR_BORDER_TEXTURE, avatarX, avatarY, BORDER_SIZE, BORDER_SIZE, 0.0f, 0.0f, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE)
        PlayerFaceRenderer.draw(guiGraphics, player.skin, avatarX + AVATAR_INSET, avatarY + AVATAR_INSET, AVATAR_SIZE)
        guiGraphics.drawString(minecraft.font, name, textX, textY, 0xF0F4FF, true)
    }

    private const val HUD_PADDING = 8
    private const val BORDER_SIZE = 32
    private const val AVATAR_SIZE = 28
    private const val AVATAR_INSET = (BORDER_SIZE - AVATAR_SIZE) / 2
    private const val NAME_GAP = 6
}