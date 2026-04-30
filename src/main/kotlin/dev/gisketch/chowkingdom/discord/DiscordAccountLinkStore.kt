package dev.gisketch.chowkingdom.discord

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Locale
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object DiscordAccountLinkStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val random = SecureRandom()
    private val linksByMinecraftId: MutableMap<String, DiscordAccountLink> = linkedMapOf()
    private val linksByDiscordId: MutableMap<String, DiscordAccountLink> = linkedMapOf()
    private val pendingByCode: MutableMap<String, PendingDiscordAccountLink> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("discord").resolve("account_links.json")
        }

    fun load() {
        file.parent.createDirectories()
        linksByMinecraftId.clear()
        linksByDiscordId.clear()
        pendingByCode.clear()

        if (file.exists()) {
            try {
                val data = file.bufferedReader().use { reader -> gson.fromJson(reader, StoredDiscordAccountLinks::class.java) }
                data?.links.orEmpty().forEach(::putLink)
                data?.pending.orEmpty().filter { pending -> !pending.expired() }.forEach { pending -> pendingByCode[pending.code] = pending }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load Discord account links {}", file, exception)
            }
        }

        loaded = true
    }

    fun createPending(player: ServerPlayer): PendingDiscordAccountLink {
        return createPending(player, player.gameProfile.name)
    }

    fun createPending(player: ServerPlayer, minecraftName: String): PendingDiscordAccountLink {
        ensureLoaded()
        pruneExpired()
        pendingByCode.entries.removeIf { (_, pending) -> pending.minecraftUuid == player.stringUUID }
        val pending = PendingDiscordAccountLink(generateCode(), player.stringUUID, minecraftName, System.currentTimeMillis() + LINK_CODE_TTL_MILLIS)
        pendingByCode[pending.code] = pending
        save()
        return pending
    }

    fun link(discordId: String, discordName: String, code: String): DiscordLinkResult {
        ensureLoaded()
        pruneExpired()
        val normalizedCode = code.trim().uppercase(Locale.ROOT)
        val pending = pendingByCode[normalizedCode] ?: return DiscordLinkResult.InvalidCode
        val existingDiscordLink = linksByDiscordId[discordId]
        if (existingDiscordLink != null && existingDiscordLink.minecraftUuid != pending.minecraftUuid) return DiscordLinkResult.DiscordAlreadyLinked(existingDiscordLink.minecraftName)
        val existingMinecraftLink = linksByMinecraftId[pending.minecraftUuid]
        if (existingMinecraftLink != null && existingMinecraftLink.discordId != discordId) return DiscordLinkResult.MinecraftAlreadyLinked(pending.minecraftName)

        val link = DiscordAccountLink(pending.minecraftUuid, pending.minecraftName, discordId, discordName, System.currentTimeMillis())
        putLink(link)
        pendingByCode.remove(normalizedCode)
        save()
        return DiscordLinkResult.Linked(link)
    }

    fun unlink(player: ServerPlayer): DiscordAccountLink? {
        ensureLoaded()
        val removed = linksByMinecraftId.remove(player.stringUUID)
        if (removed != null) linksByDiscordId.remove(removed.discordId)
        pendingByCode.entries.removeIf { (_, pending) -> pending.minecraftUuid == player.stringUUID }
        save()
        return removed
    }

    fun linkFor(player: ServerPlayer): DiscordAccountLink? {
        ensureLoaded()
        return linksByMinecraftId[player.stringUUID]
    }

    fun linkForMinecraftId(minecraftUuid: String): DiscordAccountLink? {
        ensureLoaded()
        return linksByMinecraftId[minecraftUuid]
    }

    fun linkForDiscord(discordId: String): DiscordAccountLink? {
        ensureLoaded()
        return linksByDiscordId[discordId]
    }

    fun minecraftNameForDiscord(discordId: String): String? {
        ensureLoaded()
        return linksByDiscordId[discordId]?.minecraftName
    }

    fun refreshMinecraftName(player: ServerPlayer) {
        refreshMinecraftName(player, player.gameProfile.name)
    }

    fun refreshMinecraftName(player: ServerPlayer, minecraftName: String) {
        ensureLoaded()
        val link = linksByMinecraftId[player.stringUUID] ?: return
        if (link.minecraftName == minecraftName) return
        val updated = link.copy(minecraftName = minecraftName)
        putLink(updated)
        save()
    }

    private fun putLink(link: DiscordAccountLink) {
        if (link.minecraftUuid.isBlank() || link.discordId.isBlank()) return
        linksByMinecraftId[link.minecraftUuid] = link
        linksByDiscordId[link.discordId] = link
    }

    private fun pruneExpired() {
        val removed = pendingByCode.entries.removeIf { (_, pending) -> pending.expired() }
        if (removed) save()
    }

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    private fun save() {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, "discord_account_links", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredDiscordAccountLinks(linksByMinecraftId.values.toMutableList(), pendingByCode.values.toMutableList()), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun generateCode(): String {
        repeat(20) {
            val code = "CK-%06d".format(Locale.ROOT, random.nextInt(1_000_000))
            if (!pendingByCode.containsKey(code)) return code
        }
        return "CK-%06d".format(Locale.ROOT, random.nextInt(1_000_000))
    }

    private fun PendingDiscordAccountLink.expired(): Boolean = expiresAt <= System.currentTimeMillis()

    private const val LINK_CODE_TTL_MILLIS = 10 * 60 * 1000L
}

data class DiscordAccountLink(
    var minecraftUuid: String = "",
    var minecraftName: String = "",
    var discordId: String = "",
    var discordName: String = "",
    var linkedAt: Long = 0L,
)

data class PendingDiscordAccountLink(
    var code: String = "",
    var minecraftUuid: String = "",
    var minecraftName: String = "",
    var expiresAt: Long = 0L,
)

sealed class DiscordLinkResult {
    data class Linked(val link: DiscordAccountLink) : DiscordLinkResult()
    data object InvalidCode : DiscordLinkResult()
    data class DiscordAlreadyLinked(val minecraftName: String) : DiscordLinkResult()
    data class MinecraftAlreadyLinked(val minecraftName: String) : DiscordLinkResult()
}

private data class StoredDiscordAccountLinks(
    var links: MutableList<DiscordAccountLink> = mutableListOf(),
    var pending: MutableList<PendingDiscordAccountLink> = mutableListOf(),
)
