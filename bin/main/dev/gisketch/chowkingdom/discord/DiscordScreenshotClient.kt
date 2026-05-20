package dev.gisketch.chowkingdom.discord

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.profiles.NicknameStore
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.client.settings.KeyModifier
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.glfw.GLFW
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture

object DiscordScreenshotClient {
    private const val CATEGORY = "key.category.${ChowKingdomMod.MOD_ID}"
    private val gson = Gson()
    private val httpClient = HttpClient.newHttpClient()
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS")
    private var captureState: CaptureState? = null

    val SEND_SCREENSHOT: KeyMapping = KeyMapping(
        "key.${ChowKingdomMod.MOD_ID}.discord_screenshot.send",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F2,
        CATEGORY,
    )

    fun register(modBus: IEventBus) {
        DiscordScreenshotConfig.load()
        modBus.addListener(::registerKeyMappings)
        NeoForge.EVENT_BUS.addListener(::onRenderGuiPre)
        NeoForge.EVENT_BUS.addListener(::onScreenRenderPre)
        NeoForge.EVENT_BUS.addListener(::onRenderFramePost)
    }

    @JvmStatic
    fun handleKeyPress(window: Long, key: Int, scanCode: Int, action: Int, modifiers: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.window) return false
        if (action != GLFW.GLFW_PRESS) return false
        if (minecraft.screen is KeyBindsScreen) return false
        if (!SEND_SCREENSHOT.matches(key, scanCode)) return false

        val config = DiscordScreenshotConfig.current()
        if (!config.enabled || config.webhookUrl.isBlank()) return false

        beginCapture(minecraft, config)
        return true
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(SEND_SCREENSHOT)
    }

    private fun beginCapture(minecraft: Minecraft, config: DiscordScreenshotWebhookConfig) {
        if (captureState != null) return
        captureState = CaptureState(config.copy(), minecraft.options.hideGui, screenshotIdentity(minecraft))
        if (config.hideGui) minecraft.options.hideGui = true
    }

    private fun onRenderGuiPre(event: RenderGuiEvent.Pre) {
        if (captureState?.config?.hideGui == true) event.isCanceled = true
    }

    private fun onScreenRenderPre(event: ScreenEvent.Render.Pre) {
        if (captureState?.config?.hideGui == true) event.isCanceled = true
    }

    private fun onRenderFramePost(event: RenderFrameEvent.Post) {
        val state = captureState ?: return
        captureState = null

        val minecraft = Minecraft.getInstance()
        val filename = "chowkingdom-discord-${fileTimestamp.format(LocalDateTime.now())}.png"
        val screenshot = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(filename).toFile()

        try {
            Screenshot.grab(minecraft.gameDirectory, filename, minecraft.mainRenderTarget) {
                uploadAfterSave(screenshot, state.config, state.identity)
            }
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to capture Discord screenshot", exception)
            clientMessage("message.${ChowKingdomMod.MOD_ID}.discord_screenshot.capture_failed")
        } finally {
            if (state.config.hideGui) minecraft.options.hideGui = state.previousHideGui
        }
    }

    private fun uploadAfterSave(file: File, config: DiscordScreenshotWebhookConfig, identity: ScreenshotIdentity) {
        CompletableFuture.supplyAsync { upload(file, config, identity) }
            .thenAccept { result ->
                Minecraft.getInstance().execute {
                    if (result.success) {
                        if (!config.keepLocalCopy) runCatching { Files.deleteIfExists(file.toPath()) }
                        clientMessage("message.${ChowKingdomMod.MOD_ID}.discord_screenshot.sent")
                    } else {
                        clientMessage("message.${ChowKingdomMod.MOD_ID}.discord_screenshot.failed", result.message)
                    }
                }
            }
    }

    private fun upload(file: File, config: DiscordScreenshotWebhookConfig, identity: ScreenshotIdentity): UploadResult {
        val uri = runCatching { URI.create(config.webhookUrl) }.getOrElse { return UploadResult(false, "invalid webhook URL") }
        if (uri.scheme !in setOf("http", "https")) return UploadResult(false, "invalid webhook URL")
        if (!file.exists() || file.length() <= 0L) return UploadResult(false, "screenshot file was empty")

        return runCatching {
            val boundary = "ChowKingdomBoundary${System.currentTimeMillis()}"
            val body = multipartBody(boundary, payloadJson(config, identity), file)
            val request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                UploadResult(true, "ok")
            } else {
                UploadResult(false, "HTTP ${response.statusCode()}")
            }
        }.getOrElse { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to send screenshot to Discord", exception)
            UploadResult(false, exception.message ?: "upload error")
        }
    }

    private fun payloadJson(config: DiscordScreenshotWebhookConfig, identity: ScreenshotIdentity): String {
        val values = mapOf(
            "player" to DiscordText.escapeMarkdown(identity.displayName),
            "player_raw" to identity.displayName,
            "mention" to identity.discordMention.orEmpty(),
        )
        val templateContent = DiscordText.applyTemplate(config.message, values)
        val content = if (identity.discordMention != null && !config.message.contains("{mention}")) {
            "$templateContent ${identity.discordMention}"
        } else {
            templateContent
        }
        return gson.toJson(JsonObject().apply {
            addProperty("content", DiscordText.cleanContent(content))
            addProperty("username", config.webhookUsername.take(DISCORD_USERNAME_LIMIT))
            if (config.avatarUrl.isNotBlank()) addProperty("avatar_url", config.avatarUrl)
            add("allowed_mentions", JsonObject().apply {
                add("parse", JsonArray())
                add("users", JsonArray().apply { identity.discordId?.let(::add) })
            })
        })
    }

    private fun multipartBody(boundary: String, payloadJson: String, file: File): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeText("--$boundary\r\n")
        output.writeText("Content-Disposition: form-data; name=\"payload_json\"\r\n")
        output.writeText("Content-Type: application/json\r\n\r\n")
        output.writeText(payloadJson)
        output.writeText("\r\n--$boundary\r\n")
        output.writeText("Content-Disposition: form-data; name=\"files[0]\"; filename=\"${file.name}\"\r\n")
        output.writeText("Content-Type: image/png\r\n\r\n")
        output.write(Files.readAllBytes(file.toPath()))
        output.writeText("\r\n--$boundary--\r\n")
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeText(value: String) {
        write(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun screenshotIdentity(minecraft: Minecraft): ScreenshotIdentity {
        val player = minecraft.player
        val playerId = player?.uuid
        val displayName = NicknameStore.nicknameFor(playerId) ?: player?.gameProfile?.name ?: minecraft.user.name
        val link = playerId?.let { DiscordAccountLinkStore.linkForMinecraftId(it.toString()) }
        val discordId = link?.discordId?.takeIf(String::isNotBlank)
        return ScreenshotIdentity(displayName, discordId, discordId?.let { " <@$it>" })
    }

    private fun clientMessage(key: String, vararg args: Any) {
        val minecraft = Minecraft.getInstance()
        minecraft.player?.displayClientMessage(Component.translatable(key, *args), false)
    }

    private data class CaptureState(
        val config: DiscordScreenshotWebhookConfig,
        val previousHideGui: Boolean,
        val identity: ScreenshotIdentity,
    )

    private data class ScreenshotIdentity(
        val displayName: String,
        val discordId: String?,
        val discordMention: String?,
    )

    private data class UploadResult(val success: Boolean, val message: String)

    private const val DISCORD_USERNAME_LIMIT = 80
}