package dev.gisketch.chowkingdom.client

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.discord.DiscordScreenshotConfig
import dev.gisketch.chowkingdom.discord.DiscordScreenshotWebhookConfig
import dev.gisketch.chowkingdom.profiles.NicknameClientConfigValues
import dev.gisketch.chowkingdom.profiles.NicknameConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ChowKingdomConfigScreen(private val parent: Screen) : Screen(Component.literal("Chowkingdom mod Client Config")) {
    private var nickname = NicknameConfig.current()
    private var screenshot = DiscordScreenshotConfig.current().copy()
    private var nicknameButton: Button? = null
    private var ownNameButton: Button? = null
    private var screenshotButton: Button? = null
    private var hideGuiButton: Button? = null
    private var keepCopyButton: Button? = null
    private lateinit var webhookUrl: EditBox
    private lateinit var webhookUsername: EditBox
    private lateinit var avatarUrl: EditBox
    private lateinit var message: EditBox

    override fun init() {
        val centerX = width / 2
        var y = 44

        nicknameButton = addRenderableWidget(toggleButton(centerX - 155, y, 150, "Nicknames", nickname.enableNickname) {
            nickname = nickname.copy(enableNickname = !nickname.enableNickname)
            refreshButtons()
        })
        ownNameButton = addRenderableWidget(toggleButton(centerX + 5, y, 150, "Own Name Tag", nickname.showOwnNameTag) {
            nickname = nickname.copy(showOwnNameTag = !nickname.showOwnNameTag)
            refreshButtons()
        })
        y += 48

        screenshotButton = addRenderableWidget(toggleButton(centerX - 155, y, 150, "Discord Screenshots", screenshot.enabled) {
            screenshot.enabled = !screenshot.enabled
            refreshButtons()
        })
        hideGuiButton = addRenderableWidget(toggleButton(centerX + 5, y, 150, "Hide GUI", screenshot.hideGui) {
            screenshot.hideGui = !screenshot.hideGui
            refreshButtons()
        })
        y += 24

        keepCopyButton = addRenderableWidget(toggleButton(centerX - 155, y, 150, "Keep Local Copy", screenshot.keepLocalCopy) {
            screenshot.keepLocalCopy = !screenshot.keepLocalCopy
            refreshButtons()
        })
        y += 36

        webhookUrl = addEditBox(centerX - 155, y, 310, "Webhook URL", screenshot.webhookUrl)
        y += 32
        webhookUsername = addEditBox(centerX - 155, y, 150, "Webhook Username", screenshot.webhookUsername)
        avatarUrl = addEditBox(centerX + 5, y, 150, "Avatar URL", screenshot.avatarUrl)
        y += 32
        message = addEditBox(centerX - 155, y, 310, "Message", screenshot.message)

        addRenderableWidget(Button.builder(Component.literal("Save")) {
            save()
            Minecraft.getInstance().setScreen(parent)
        }.bounds(centerX - 155, height - 32, 74, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            Minecraft.getInstance().setScreen(parent)
        }.bounds(centerX + 81, height - 32, 74, 20).build())

        refreshButtons()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF.toInt())
        guiGraphics.drawString(font, "Profiles", width / 2 - 155, 32, 0xFFFFD24A.toInt(), false)
        guiGraphics.drawString(font, "Discord Screenshots", width / 2 - 155, 80, 0xFFFFD24A.toInt(), false)
        guiGraphics.drawString(font, "Webhook URL", width / 2 - 155, 144, 0xFFA0A0A0.toInt(), false)
        guiGraphics.drawString(font, "Webhook Username", width / 2 - 155, 176, 0xFFA0A0A0.toInt(), false)
        guiGraphics.drawString(font, "Avatar URL", width / 2 + 5, 176, 0xFFA0A0A0.toInt(), false)
        guiGraphics.drawString(font, "Message", width / 2 - 155, 208, 0xFFA0A0A0.toInt(), false)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    private fun addEditBox(x: Int, y: Int, width: Int, label: String, value: String): EditBox {
        return addRenderableWidget(EditBox(font, x, y + 10, width, 20, Component.literal(label)).also { box ->
            box.setMaxLength(2048)
            box.value = value
        })
    }

    private fun toggleButton(x: Int, y: Int, width: Int, label: String, value: Boolean, action: () -> Unit): Button =
        Button.builder(toggleText(label, value)) { action() }.bounds(x, y, width, 20).build()

    private fun refreshButtons() {
        nicknameButton?.message = toggleText("Nicknames", nickname.enableNickname)
        ownNameButton?.message = toggleText("Own Name Tag", nickname.showOwnNameTag)
        screenshotButton?.message = toggleText("Discord Screenshots", screenshot.enabled)
        hideGuiButton?.message = toggleText("Hide GUI", screenshot.hideGui)
        keepCopyButton?.message = toggleText("Keep Local Copy", screenshot.keepLocalCopy)
    }

    private fun save() {
        NicknameConfig.save(NicknameClientConfigValues(nickname.enableNickname, nickname.showOwnNameTag))
        DiscordScreenshotConfig.save(
            DiscordScreenshotWebhookConfig(
                enabled = screenshot.enabled,
                webhookUrl = webhookUrl.value,
                webhookUsername = webhookUsername.value,
                avatarUrl = avatarUrl.value,
                hideGui = screenshot.hideGui,
                keepLocalCopy = screenshot.keepLocalCopy,
                message = message.value,
            ),
        )
    }

    private fun toggleText(label: String, value: Boolean): Component =
        Component.literal("$label: ${if (value) "ON" else "OFF"}")
}
