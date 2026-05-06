package dev.gisketch.chowkingdom.profiles

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object NicknameConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var config = StoredNicknameClientConfig()
    private var loaded = false

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("profiles").resolve("client.json")

    @JvmStatic
    fun enableNickname(): Boolean {
        if (!loaded) load()
        return config.enableNickname
    }

    @JvmStatic
    fun showOwnNameTag(): Boolean {
        if (!loaded) load()
        return config.enableNickname && config.showOwnNameTag
    }

    fun current(): NicknameClientConfigValues {
        if (!loaded) load()
        return NicknameClientConfigValues(config.enableNickname, config.showOwnNameTag)
    }

    fun save(values: NicknameClientConfigValues) {
        file.parent.createDirectories()
        config = StoredNicknameClientConfig(values.enableNickname, values.showOwnNameTag)
        Files.createTempFile(file.parent, "nickname_client", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(config, writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        loaded = true
    }

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            file.bufferedReader().use { reader -> gson.fromJson(reader, StoredNicknameClientConfig::class.java) } ?: StoredNicknameClientConfig()
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load nickname client config {}", file, exception)
            StoredNicknameClientConfig()
        }
        loaded = true
    }

    private fun writeDefault() {
        Files.createTempFile(file.parent, "nickname_client", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(StoredNicknameClientConfig(), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

data class NicknameClientConfigValues(
    var enableNickname: Boolean = true,
    var showOwnNameTag: Boolean = true,
)

private data class StoredNicknameClientConfig(
    var enableNickname: Boolean = true,
    var showOwnNameTag: Boolean = true,
)
