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

private data class StoredNicknameClientConfig(
    var enableNickname: Boolean = true,
    var showOwnNameTag: Boolean = true,
)
