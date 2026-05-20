package dev.gisketch.chowkingdom.profiles

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import dev.gisketch.chowkingdom.discord.DiscordAccountLinkStore
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object NicknameStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val nicknames: MutableMap<String, String> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("profiles").resolve("nicknames.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        nicknames.clear()

        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, StoredNicknames::class.java, ::StoredNicknames)
                data.players
                    .mapValues { (_, nickname) -> nickname.trim() }
                    .filterValues(::isValidNickname)
                    .forEach { (playerId, nickname) -> nicknames[playerId] = nickname }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load nicknames {}", file, exception)
            }
        }

        loaded = true
    }

    fun set(player: ServerPlayer, nickname: String): NicknameResult {
        ensureLoaded()
        val normalized = nickname.trim()
        if (!isValidNickname(normalized)) return NicknameResult.Invalid
        nicknames[player.stringUUID] = normalized
        save()
        DiscordAccountLinkStore.refreshMinecraftName(player, normalized)
        player.refreshDisplayName()
        refreshPlayerInfo(player)
        NicknameNetwork.syncAllPlayers()
        return NicknameResult.Changed(normalized)
    }

    fun clear(player: ServerPlayer): Boolean {
        ensureLoaded()
        val removed = nicknames.remove(player.stringUUID) != null
        if (removed) save()
        DiscordAccountLinkStore.refreshMinecraftName(player, originalName(player))
        player.refreshDisplayName()
        refreshPlayerInfo(player)
        NicknameNetwork.syncAllPlayers()
        return removed
    }

    fun originalName(player: ServerPlayer): String = runCatching {
        val field = player.gameProfile.javaClass.getDeclaredField("name")
        field.isAccessible = true
        field.get(player.gameProfile) as? String
    }.getOrNull()?.takeIf(String::isNotBlank) ?: player.gameProfile.name

    fun displayName(player: ServerPlayer): String = nicknameFor(player.uuid) ?: originalName(player)

    @JvmStatic
    fun nicknameFor(playerId: UUID?): String? {
        if (playerId == null) return null
        if (!NicknameConfig.enableNickname()) return null
        if (ServerLifecycleHooks.getCurrentServer() == null) return NicknameClientState.nicknameFor(playerId)
        ensureLoaded()
        return nicknames[playerId.toString()]
    }

    private fun refreshPlayerInfo(player: ServerPlayer) {
        val remove = ClientboundPlayerInfoRemovePacket(listOf(player.uuid))
        val add = ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
            ),
            listOf(player),
        )
        player.server.playerList.players.forEach { onlinePlayer ->
            onlinePlayer.connection.send(remove)
            onlinePlayer.connection.send(add)
        }
    }

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    private fun save() {
        TomlConfigIO.write(file, StoredNicknames(nicknames))
    }

    private fun isValidNickname(nickname: String): Boolean = nickname.length in 1..16 && nickname.all { char -> char.isLetterOrDigit() || char == '_' }
}

sealed class NicknameResult {
    data class Changed(val nickname: String) : NicknameResult()
    data object Invalid : NicknameResult()
}

private data class StoredNicknames(
    var players: MutableMap<String, String> = linkedMapOf(),
)
