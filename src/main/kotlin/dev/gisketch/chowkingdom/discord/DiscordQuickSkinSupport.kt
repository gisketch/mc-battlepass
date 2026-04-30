package dev.gisketch.chowkingdom.discord

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerPlayer
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object DiscordQuickSkinSupport {
    fun avatarUrl(player: ServerPlayer, config: DiscordWebhookConfig): String {
        configuredPlayerAvatar(player, config)?.let { url ->
            logDebug(config, player, "using configured player_avatar_urls entry")
            return url
        }

        val quickSkinTemplate = config.quickSkinAvatarUrlTemplate.trim()
        val skinId = quickSkinSkinId(player.uuid)
        if (quickSkinTemplate.isNotBlank()) {
            skinId?.let {
                logDebug(config, player, "using quick_skin_avatar_url_template with skin_id={}", it)
                return fillTemplate(quickSkinTemplate, player, it)
            }
            logDebug(config, player, "quick_skin_avatar_url_template set but no Quick Skin skin_id found")
        }
        val publicBaseUrl = usablePublicBaseUrl(config.quickSkinAvatarServer.publicBaseUrl)
        if (skinId != null && config.quickSkinAvatarServer.enabled && publicBaseUrl != null) {
            logDebug(config, player, "using built-in Quick Skin avatar server with skin_id={}", skinId)
            return "$publicBaseUrl/quickskin/avatar/${player.uuid}.png?skin=${encode(skinId)}"
        }
        if (skinId == null) {
            logDebug(config, player, "falling back to minecraft_avatar_url_template because no Quick Skin skin_id was found")
        } else if (!config.quickSkinAvatarServer.enabled) {
            logDebug(config, player, "falling back to minecraft_avatar_url_template because quick_skin_avatar_server.enabled=false")
        } else if (config.quickSkinAvatarServer.publicBaseUrl.isBlank()) {
            logDebug(config, player, "falling back to minecraft_avatar_url_template because quick_skin_avatar_server.public_base_url is blank")
        } else {
            logDebug(config, player, "falling back to minecraft_avatar_url_template because quick_skin_avatar_server.public_base_url is placeholder or invalid")
        }
        return fillTemplate(config.minecraftAvatarUrlTemplate, player, null)
    }

    fun diagnostics(player: ServerPlayer, config: DiscordWebhookConfig): List<String> {
        val skinId = quickSkinSkinId(player.uuid)
        val textureCacheKey = skinId?.let(::textureCacheKey)
        val textureBytes = textureCacheKey?.let(::quickSkinTexture)
        val selectedUrl = avatarUrl(player, config)
        val localHost = if (config.quickSkinAvatarServer.bindHost == "0.0.0.0") "127.0.0.1" else config.quickSkinAvatarServer.bindHost
        val localAvatarUrl = "http://$localHost:${config.quickSkinAvatarServer.port}/quickskin/avatar/${player.uuid}.png${skinId?.let { "?skin=${encode(it)}" } ?: ""}"
        val headResult = quickSkinHeadResult(player.uuid, skinId)
        return listOf(
            "Discord avatar diagnostics for ${player.gameProfile.name}",
            "uuid=${player.uuid}",
            "quick_skin_classes_present=${quickSkinClassesPresent()}",
            "quick_skin_skin_id=${skinId ?: "missing"}",
            "quick_skin_texture_cache_key=${textureCacheKey ?: "missing"}",
            "quick_skin_texture_bytes=${textureBytes?.size ?: 0}",
            "quick_skin_avatar_url_template_set=${config.quickSkinAvatarUrlTemplate.isNotBlank()}",
            "player_avatar_url_configured=${configuredPlayerAvatar(player, config) != null}",
            "quick_skin_avatar_server_enabled=${config.quickSkinAvatarServer.enabled}",
            "quick_skin_avatar_server_public_base_url_valid=${usablePublicBaseUrl(config.quickSkinAvatarServer.publicBaseUrl) != null}",
            "quick_skin_head_result=${headResult.reason}",
            "local_quick_skin_avatar_url=$localAvatarUrl",
            "selected_avatar_url=$selectedUrl",
        )
    }

    fun quickSkinHeadPng(playerId: UUID): ByteArray? {
        return quickSkinHeadResult(playerId, null).image
    }

    fun quickSkinHeadResult(playerId: UUID, skinIdOverride: String?): QuickSkinHeadResult {
        val skinId = skinIdOverride?.takeIf(String::isNotBlank) ?: quickSkinSkinId(playerId)
            ?: return QuickSkinHeadResult(null, "missing Quick Skin skin_id for uuid=$playerId")
        val textureCacheKey = textureCacheKey(skinId)
        val texture = quickSkinTexture(textureCacheKey)
            ?: return QuickSkinHeadResult(null, "missing Quick Skin texture bytes for skin_id=$skinId cache_key=$textureCacheKey")
        val image = renderHead(texture)
            ?: return QuickSkinHeadResult(null, "failed to render Quick Skin texture for skin_id=$skinId")
        return QuickSkinHeadResult(image, "ok skin_id=$skinId cache_key=$textureCacheKey texture_bytes=${texture.size}")
    }

    data class QuickSkinHeadResult(val image: ByteArray?, val reason: String)

    private fun quickSkinSkinId(playerId: UUID): String? = runCatching {
        val repositoryClass = Class.forName("com.quickskin.mod.server.data.ServerPlayerAppearanceRepository")
        val repository = repositoryClass.getMethod("getInstance").invoke(null)
        val appearance = repositoryClass.getMethod("getAppearance", UUID::class.java).invoke(repository, playerId) ?: return null
        appearance.javaClass.getMethod("getSkinId").invoke(appearance) as? String
    }.onFailure { exception ->
        if (exception !is ClassNotFoundException && exception.cause !is ClassNotFoundException) {
            ChowKingdomMod.LOGGER.debug("Quick Skin avatar lookup failed", exception)
        }
    }.getOrNull()?.takeIf(String::isNotBlank)

    private fun quickSkinTexture(skinId: String): ByteArray? = runCatching {
        val cacheClass = Class.forName("com.quickskin.mod.server.storage.ServerTextureCache")
        val cache = cacheClass.getMethod("getInstance").invoke(null)
        cacheClass.getMethod("getTexture", String::class.java).invoke(cache, skinId) as? ByteArray
    }.getOrNull()

    private fun textureCacheKey(skinId: String): String = skinId
        .removePrefix("local_skin:")
        .removePrefix("local_cape:")

    private fun quickSkinClassesPresent(): Boolean = runCatching {
        Class.forName("com.quickskin.mod.server.data.ServerPlayerAppearanceRepository")
        Class.forName("com.quickskin.mod.server.storage.ServerTextureCache")
    }.isSuccess

    private fun logDebug(config: DiscordWebhookConfig, player: ServerPlayer, message: String, vararg args: Any) {
        if (!config.debugAvatarResolution) return
        ChowKingdomMod.LOGGER.info("Discord avatar for {} (${player.uuid}): $message", player.gameProfile.name, *args)
    }

    private fun renderHead(textureBytes: ByteArray): ByteArray? {
        val skin = ImageIO.read(ByteArrayInputStream(textureBytes)) ?: return null
        val unit = (skin.width / 8).coerceAtLeast(1)
        if (skin.width < unit * 6 || skin.height < unit * 2) return null

        val head = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
        val graphics = head.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        graphics.drawImage(skin.getSubimage(unit, unit, unit, unit), 0, 0, 128, 128, null)
        graphics.drawImage(skin.getSubimage(unit * 5, unit, unit, unit), 0, 0, 128, 128, null)
        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(head, "png", output)
        return output.toByteArray()
    }

    private fun fillTemplate(template: String, player: ServerPlayer, skinId: String?): String = template
        .replace("{uuid}", encode(player.uuid.toString()))
        .replace("{uuid_no_dash}", encode(player.uuid.toString().replace("-", "")))
        .replace("{name}", encode(player.gameProfile.name))
        .replace("{skin_id}", encode(skinId.orEmpty()))

    private fun configuredPlayerAvatar(player: ServerPlayer, config: DiscordWebhookConfig): String? {
        val urls = config.playerAvatarUrls
        return urls[player.uuid.toString().lowercase()]
            ?: urls[player.uuid.toString().replace("-", "").lowercase()]
            ?: urls[player.gameProfile.name.lowercase()]
    }

    private fun usablePublicBaseUrl(value: String): String? {
        val trimmed = value.trim().removeSuffix("/")
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return null
        val lower = trimmed.lowercase()
        if (lower.contains("your-") || lower.contains("example.com")) return null
        if (lower.startsWith("http://localhost") || lower.startsWith("https://localhost")) return null
        if (lower.startsWith("http://127.") || lower.startsWith("https://127.")) return null
        if (lower.startsWith("http://0.0.0.0") || lower.startsWith("https://0.0.0.0")) return null
        return trimmed
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}