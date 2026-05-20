package dev.gisketch.chowkingdom.profiles

import net.minecraft.client.Minecraft
import java.util.UUID

object NicknameClientState {
    private val nicknames: MutableMap<UUID, String> = linkedMapOf()

    fun apply(payload: NicknameSyncPayload) {
        nicknames.clear()
        nicknames.putAll(payload.nicknames)
        Minecraft.getInstance().level?.players()?.forEach { player -> player.refreshDisplayName() }
    }

    fun nicknameFor(playerId: UUID?): String? = playerId?.let(nicknames::get)
}