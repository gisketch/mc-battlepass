package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ExternalStaminaConfigPatcher {
    fun apply(config: StaminaCompatDefinition) {
        if (!config.enabled) return
        if (ModList.get().isLoaded("parcool")) patchParCool(config)
        if (ModList.get().isLoaded("epicfight")) patchEpicFight(config)
    }

    private fun patchParCool(config: StaminaCompatDefinition) {
        val configDir = FMLPaths.CONFIGDIR.get()
        if (config.hideParCoolStaminaHud) {
            patchToml(configDir.resolve("parcool-client.toml"), mapOf("stamina_hud_type" to "\"Hide\""))
        }
        if (config.enforceParCoolParagliderStamina) {
            patchToml(
                configDir.resolve("parcool-server.toml"),
                mapOf(
                    "limitation_imposed" to "true",
                    "forced_stamina" to "\"PARAGLIDER\"",
                    "stamina_consumption_of_Dodge" to config.parCoolDodgeCost.coerceAtLeast(0).toString(),
                ),
            )
        }
    }

    private fun patchEpicFight(config: StaminaCompatDefinition) {
        if (!config.hideEpicFightStaminaHud) return
        patchToml(
            FMLPaths.CONFIGDIR.get().resolve("epicfight-client.toml"),
            mapOf(
                "stamina_bar_x" to "10000",
                "stamina_bar_y" to "10000",
            ),
        )
    }

    private fun patchToml(path: java.nio.file.Path, replacements: Map<String, String>) {
        if (!path.exists()) return
        runCatching {
            var text = path.readText()
            replacements.forEach { (key, value) ->
                text = text.replace(Regex("(?m)^\\s*$key\\s*=.*$"), "\t$key = $value")
            }
            path.writeText(text)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to patch stamina compatibility config {}", path, exception)
        }
    }
}