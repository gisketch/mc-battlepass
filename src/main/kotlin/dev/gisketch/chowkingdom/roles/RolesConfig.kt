package dev.gisketch.chowkingdom.roles

import com.google.gson.GsonBuilder
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.random.Random

object RolesConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var jobsById: Map<String, RoleDefinition> = emptyMap()
    private var classesById: Map<String, RoleDefinition> = emptyMap()
    private var onboardingDefinition = RolesOnboardingDefinition()

    private val root: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("roles")

    fun load() {
        root.resolve("jobs").createDirectories()
        root.resolve("classes").createDirectories()
        writeDefaultIfMissing(root.resolve("onboarding.json"), defaultOnboarding())
        writeDefaultIfMissing(root.resolve("jobs").resolve("farmer.json"), defaultFarmer())
        writeDefaultIfMissing(root.resolve("classes").resolve("rogue.json"), defaultRogue())
        writeDefaultIfMissing(root.resolve("classes").resolve("warrior.json"), defaultWarrior())
        onboardingDefinition = readOnboarding(root.resolve("onboarding.json"))
        jobsById = loadDefinitions(root.resolve("jobs"))
        classesById = loadDefinitions(root.resolve("classes"))
    }

    fun jobs(): Collection<RoleDefinition> = jobsById.values

    fun classes(): Collection<RoleDefinition> = classesById.values

    fun job(id: String): RoleDefinition? = jobsById[id]

    fun roleClass(id: String): RoleDefinition? = classesById[id]

    fun defaultJobId(): String = job("farmer")?.id ?: jobsById.keys.firstOrNull().orEmpty()

    fun defaultClassId(): String = roleClass("rogue")?.id ?: classesById.keys.firstOrNull().orEmpty()

    fun welcomeContent(): String {
        val lines = onboardingDefinition.welcomeContent.map(String::trim).filter(String::isNotBlank)
        return lines.getOrNull(Random.nextInt(lines.size.coerceAtLeast(1))) ?: DEFAULT_WELCOME_CONTENT
    }

    private fun loadDefinitions(directory: Path): Map<String, RoleDefinition> = Files.list(directory).use { stream ->
        stream.filter { path -> path.extension.equals("json", ignoreCase = true) }
            .map { path -> readDefinition(path) }
            .filter { definition -> definition.id.isNotBlank() }
            .toList()
            .associateBy { definition -> definition.id }
    }

    private fun readDefinition(path: Path): RoleDefinition = try {
        path.bufferedReader().use { reader -> gson.fromJson(reader, RoleDefinition::class.java) } ?: RoleDefinition()
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role definition {}", path, exception)
        RoleDefinition()
    }

    private fun readOnboarding(path: Path): RolesOnboardingDefinition = try {
        path.bufferedReader().use { reader -> gson.fromJson(reader, RolesOnboardingDefinition::class.java) } ?: defaultOnboarding()
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role onboarding config {}", path, exception)
        defaultOnboarding()
    }

    private fun writeDefaultIfMissing(file: Path, definition: Any) {
        if (file.exists()) return
        file.parent.createDirectories()
        Files.createTempFile(file.parent, file.fileName.toString(), ".tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(definition, writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun defaultFarmer(): RoleDefinition = RoleDefinition(
        id = "farmer",
        displayName = "Farmer",
        icon = "minecraft:grass_block",
        description = "Tend crops, protect farmland, and coax better harvests from the kingdom soil.",
        perks = mutableListOf(
            RolePerkDefinition(type = "prevent_crop_trample"),
            RolePerkDefinition(type = "cobblemon_catch_rate", pokemonType = "grass", multiplier = 1.5),
            RolePerkDefinition(type = "mount_speed", pokemonType = "grass", multiplier = 1.25),
            RolePerkDefinition(type = "quality_food_harvest_bonus", multiplier = 1.15),
        ),
    )

    private fun defaultRogue(): RoleDefinition = RoleDefinition(
        id = "rogue",
        displayName = "Rogue",
        icon = "minecraft:grass_block",
        description = "Move light, hit hard, and favor quick gear built for sharp openings.",
        perks = mutableListOf(
            RolePerkDefinition(
                type = "starting_items",
                startingItems = mutableListOf(
                    "minecraft:book",
                    "minecraft:diamond_axe",
                    "minecraft:leather_boots",
                ),
            ),
            RolePerkDefinition(
                type = "equipment_affinity",
                weaponTag = "gisketchs_chowkingdom_mod:class/rogue_weapons",
                armorTag = "gisketchs_chowkingdom_mod:class/rogue_armor",
                wrongWeaponDamageMultiplier = 0.2,
                wrongWeaponCooldownTicks = 12,
                wrongWeaponAttackSpeedMultiplier = 0.1,
                wrongArmorDisablesSprint = true,
            ),
        ),
    )

    private fun defaultWarrior(): RoleDefinition = RoleDefinition(
        id = "warrior",
        displayName = "Warrior",
        icon = "minecraft:grass_block",
        description = "Stand in the front line with sturdy weapons, heavier armor, and simple force.",
        perks = mutableListOf(
            RolePerkDefinition(
                type = "starting_items",
                startingItems = mutableListOf(
                    "minecraft:book",
                    "minecraft:wooden_sword",
                    "minecraft:iron_boots",
                ),
            ),
            RolePerkDefinition(
                type = "equipment_affinity",
                weaponTag = "gisketchs_chowkingdom_mod:class/warrior_weapons",
                armorTag = "gisketchs_chowkingdom_mod:class/warrior_armor",
                wrongWeaponDamageMultiplier = 0.2,
                wrongWeaponCooldownTicks = 12,
                wrongWeaponAttackSpeedMultiplier = 0.1,
                wrongArmorDisablesSprint = true,
            ),
        ),
    )

    private fun defaultOnboarding(): RolesOnboardingDefinition = RolesOnboardingDefinition(
        welcomeContent = mutableListOf(
            DEFAULT_WELCOME_CONTENT,
            "Pick your place in the kingdom before the road opens.",
            "Every story starts with work to do and a way to fight.",
        ),
    )

    private const val DEFAULT_WELCOME_CONTENT = "A new chapter begins in Chowkingdom. Choose how you work, then choose how you fight."
}
