package dev.gisketch.chowkingdom.discord

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object DiscordScreenshotConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var config = DiscordScreenshotWebhookConfig()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("discord").resolve("screenshot.json")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            file.bufferedReader().use { reader -> gson.fromJson(reader, DiscordScreenshotWebhookConfig::class.java) } ?: DiscordScreenshotWebhookConfig()
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load Discord screenshot config {}", file, exception)
            DiscordScreenshotWebhookConfig()
        }.normalized()
    }

    fun current(): DiscordScreenshotWebhookConfig = config

    private fun writeDefault() {
        Files.createTempFile(file.parent, "discord_screenshot", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(DiscordScreenshotWebhookConfig(), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

data class DiscordScreenshotWebhookConfig(
    var enabled: Boolean = true,
    @SerializedName("webhook_url") var webhookUrl: String = "",
    @SerializedName("webhook_username") var webhookUsername: String = "Chow Kingdom Screenshots",
    @SerializedName("avatar_url") var avatarUrl: String = "",
    @SerializedName("hide_gui") var hideGui: Boolean = true,
    @SerializedName("keep_local_copy") var keepLocalCopy: Boolean = true,
    var message: String = "Screenshot from {player}{mention}",
) {
    fun normalized(): DiscordScreenshotWebhookConfig = apply {
        webhookUrl = webhookUrl.trim()
        webhookUsername = webhookUsername.trim().ifBlank { "Chow Kingdom Screenshots" }
        avatarUrl = avatarUrl.trim()
        message = message.trim().ifBlank { "Screenshot from {player}{mention}" }
    }
}