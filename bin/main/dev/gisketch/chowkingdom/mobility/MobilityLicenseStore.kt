package dev.gisketch.chowkingdom.mobility

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object MobilityLicenseStore {
    const val RIDING_LICENSE = "cobblemon_riding"

    private val players: MutableMap<String, PlayerMobilityLicenseRecord> = linkedMapOf()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = server?.getWorldPath(LevelResource.ROOT)?.resolve("data") ?: FMLPaths.CONFIGDIR.get()
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("mobility").resolve("licenses.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        players.clear()
        if (file.exists()) {
            try {
                val data = TomlConfigIO.read(file, StoredMobilityLicenseData::class.java, ::StoredMobilityLicenseData)
                data.players.forEach { (id, record) -> players[id] = record }
            } catch (exception: Exception) {
                ChowKingdomMod.LOGGER.warn("Failed to load mobility license store {}", file, exception)
            }
        }
        loaded = true
    }

    fun hasRidingLicense(player: ServerPlayer): Boolean = hasLicense(player.uuid, RIDING_LICENSE)

    fun hasLicense(playerId: UUID, licenseId: String): Boolean {
        if (!loaded) load()
        return players[playerId.toString()]?.licenses?.get(normalize(licenseId))?.granted == true
    }

    fun grantRidingLicense(player: ServerPlayer, source: String): Boolean = grant(player, RIDING_LICENSE, source)

    fun grant(player: ServerPlayer, licenseId: String, source: String): Boolean {
        if (!loaded) load()
        val id = normalize(licenseId)
        if (id.isBlank()) return false
        val record = players.getOrPut(player.uuid.toString()) { PlayerMobilityLicenseRecord() }
        val existing = record.licenses[id]
        if (existing?.granted == true) return false
        record.licenses[id] = MobilityLicenseRecord(
            granted = true,
            source = source.trim().ifBlank { "unknown" },
            grantedAtEpochMillis = System.currentTimeMillis(),
        )
        save()
        return true
    }

    fun revoke(player: ServerPlayer, licenseId: String): Boolean {
        if (!loaded) load()
        val record = players[player.uuid.toString()] ?: return false
        val existing = record.licenses[normalize(licenseId)] ?: return false
        if (!existing.granted) return false
        existing.granted = false
        save()
        return true
    }

    private fun save() {
        TomlConfigIO.write(file, StoredMobilityLicenseData(players))
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}

class PlayerMobilityLicenseRecord(
    var licenses: MutableMap<String, MobilityLicenseRecord> = linkedMapOf(),
)

class MobilityLicenseRecord(
    var granted: Boolean = true,
    var source: String = "",
    var grantedAtEpochMillis: Long = 0L,
)

private class StoredMobilityLicenseData(
    var players: MutableMap<String, PlayerMobilityLicenseRecord> = linkedMapOf(),
)
