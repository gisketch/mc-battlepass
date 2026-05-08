package dev.gisketch.chowkingdom.profiles

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object NicknameConfig {
    private var config = StoredNicknameClientConfig()
    private var loaded = false

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("profiles").resolve("client.toml")

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
        TomlConfigIO.write(file, config)
        loaded = true
    }

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            TomlConfigIO.read(file, StoredNicknameClientConfig::class.java, ::StoredNicknameClientConfig)
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load nickname client config {}", file, exception)
            StoredNicknameClientConfig()
        }
        loaded = true
    }

    private fun writeDefault() {
        TomlConfigIO.write(file, StoredNicknameClientConfig())
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
