package dev.gisketch.chowkingdom.compat

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object StaminaCompatConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var current = StaminaCompatDefinition()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("compat").resolve("stamina.json")

    fun load(): StaminaCompatDefinition {
        if (!file.exists()) write(current)
        current = try {
            val json = file.bufferedReader().use { reader -> JsonParser.parseReader(reader).asJsonObject }
            (gson.fromJson(json, StaminaCompatDefinition::class.java) ?: StaminaCompatDefinition()).withMissingDefaults(json).also { config ->
                if (missingKeys(json).isNotEmpty()) write(config)
            }
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load stamina compatibility config {}", file, exception)
            StaminaCompatDefinition()
        }
        return current
    }

    fun values(): StaminaCompatDefinition = current

    private fun write(config: StaminaCompatDefinition) {
        file.parent.createDirectories()
        Files.createTempFile(file.parent, file.fileName.toString(), ".tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(config, writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun StaminaCompatDefinition.withMissingDefaults(json: JsonObject): StaminaCompatDefinition {
        val defaults = StaminaCompatDefinition()
        if (!json.has("epicFightBasicAttackCost")) epicFightBasicAttackCost = defaults.epicFightBasicAttackCost
        if (!json.has("epicFightJumpAttackCost")) epicFightJumpAttackCost = defaults.epicFightJumpAttackCost
        if (!json.has("epicFightInnateSkillCost")) epicFightInnateSkillCost = defaults.epicFightInnateSkillCost
        if (!json.has("epicFightGuardSkillCost")) epicFightGuardSkillCost = defaults.epicFightGuardSkillCost
        if (!json.has("epicFightBlockCost")) epicFightBlockCost = defaults.epicFightBlockCost
        if (!json.has("epicFightParryCost")) epicFightParryCost = defaults.epicFightParryCost
        if (!json.has("staminaDrainTicks")) staminaDrainTicks = defaults.staminaDrainTicks
        if (!json.has("paragliderRecoveryDelayTicksAfterSpend")) paragliderRecoveryDelayTicksAfterSpend = defaults.paragliderRecoveryDelayTicksAfterSpend
        if (!json.has("disableEpicFightBattleModeWhileParagliding")) disableEpicFightBattleModeWhileParagliding = defaults.disableEpicFightBattleModeWhileParagliding
        if (!json.has("restoreEpicFightBattleModeAfterParagliding")) restoreEpicFightBattleModeAfterParagliding = defaults.restoreEpicFightBattleModeAfterParagliding
        return this
    }

    private fun missingKeys(json: JsonObject): List<String> = REQUIRED_KEYS.filterNot(json::has)

    private val REQUIRED_KEYS = listOf(
        "enabled",
        "attackCost",
        "blockedHitCost",
        "parCoolDodgeCost",
        "epicFightBasicAttackCost",
        "epicFightJumpAttackCost",
        "epicFightInnateSkillCost",
        "epicFightGuardSkillCost",
        "epicFightBlockCost",
        "epicFightParryCost",
        "staminaDrainTicks",
        "paragliderRecoveryDelayTicksAfterSpend",
        "disableEpicFightBattleModeWhileParagliding",
        "restoreEpicFightBattleModeAfterParagliding",
        "refillEpicFightStamina",
        "enforceParCoolParagliderStamina",
        "hideParCoolStaminaHud",
        "hideEpicFightStaminaHud",
    )
}

data class StaminaCompatDefinition(
    var enabled: Boolean = true,
    var attackCost: Double = 120.0,
    var blockedHitCost: Double = 140.0,
    var parCoolDodgeCost: Int = 320,
    var epicFightBasicAttackCost: Double = 160.0,
    var epicFightJumpAttackCost: Double = 240.0,
    var epicFightInnateSkillCost: Double = 450.0,
    var epicFightGuardSkillCost: Double = 90.0,
    var epicFightBlockCost: Double = 150.0,
    var epicFightParryCost: Double = 260.0,
    var staminaDrainTicks: Int = 8,
    var paragliderRecoveryDelayTicksAfterSpend: Int = 80,
    var disableEpicFightBattleModeWhileParagliding: Boolean = true,
    var restoreEpicFightBattleModeAfterParagliding: Boolean = true,
    var refillEpicFightStamina: Boolean = true,
    var enforceParCoolParagliderStamina: Boolean = true,
    var hideParCoolStaminaHud: Boolean = true,
    var hideEpicFightStaminaHud: Boolean = true,
)