package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.random.Random

object RolesConfig {
    private var jobsById: Map<String, RoleDefinition> = emptyMap()
    private var classesById: Map<String, RoleDefinition> = emptyMap()
    private var onboardingDefinition = RolesOnboardingDefinition()
    private var jobScalingDefinition = JobScalingDefinition()

    private val root: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("roles")

    fun load() {
        root.resolve("jobs").createDirectories()
        root.resolve("classes").createDirectories()
        writeDefaultIfMissing(root.resolve("onboarding.toml"), defaultOnboarding())
        writeDefaultIfMissing(root.resolve("job_scaling.toml"), defaultJobScaling())
        defaultJobs().forEach { definition -> writeDefaultIfMissing(root.resolve("jobs").resolve("${definition.id}.toml"), definition) }
        writeDefaultIfMissing(root.resolve("classes").resolve("rogue.toml"), defaultRogue())
        writeDefaultIfMissing(root.resolve("classes").resolve("warrior.toml"), defaultWarrior())
        onboardingDefinition = readOnboarding(root.resolve("onboarding.toml"))
        jobScalingDefinition = readJobScaling(root.resolve("job_scaling.toml"))
        jobsById = loadDefinitions(root.resolve("jobs"))
        classesById = loadDefinitions(root.resolve("classes"))
    }

    fun jobs(): Collection<RoleDefinition> = jobsById.values

    fun classes(): Collection<RoleDefinition> = classesById.values

    fun job(id: String): RoleDefinition? = jobsById[id]

    fun roleClass(id: String): RoleDefinition? = classesById[id]

    fun defaultJobId(): String = job("botanist")?.id ?: jobsById.keys.firstOrNull().orEmpty()

    fun defaultClassId(): String = roleClass("rogue")?.id ?: classesById.keys.firstOrNull().orEmpty()

    fun welcomeContent(): String {
        val lines = onboardingDefinition.welcomeContent.map(String::trim).filter(String::isNotBlank)
        return lines.getOrNull(Random.nextInt(lines.size.coerceAtLeast(1))) ?: DEFAULT_WELCOME_CONTENT
    }

    fun jobScaling(): JobScalingDefinition = jobScalingDefinition

    private fun loadDefinitions(directory: Path): Map<String, RoleDefinition> = Files.list(directory).use { stream ->
        stream.filter { path -> path.extension.equals("toml", ignoreCase = true) }
            .map { path -> readDefinition(path) }
            .map(::withBundledDefaultPerks)
            .filter { definition -> definition.id.isNotBlank() }
            .toList()
            .associateBy { definition -> definition.id }
    }

    private fun withBundledDefaultPerks(definition: RoleDefinition): RoleDefinition {
        val bundled = defaultJobs().firstOrNull { job -> job.id == definition.id } ?: return definition
        bundled.perks
            .filter { perk -> perk.type == "mount_speed" }
            .filterNot { defaultPerk -> definition.perks.any { perk -> perk.type == defaultPerk.type && perk.pokemonType == defaultPerk.pokemonType } }
            .forEach { perk -> definition.perks += perk.copy() }
        return definition
    }

    private fun readDefinition(path: Path): RoleDefinition = try {
        TomlConfigIO.read(path, RoleDefinition::class.java, ::RoleDefinition)
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role definition {}", path, exception)
        RoleDefinition()
    }

    private fun readOnboarding(path: Path): RolesOnboardingDefinition = try {
        TomlConfigIO.read(path, RolesOnboardingDefinition::class.java, ::defaultOnboarding)
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role onboarding config {}", path, exception)
        defaultOnboarding()
    }

    private fun readJobScaling(path: Path): JobScalingDefinition = try {
        TomlConfigIO.read(path, JobScalingDefinition::class.java, ::defaultJobScaling)
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role job scaling config {}", path, exception)
        defaultJobScaling()
    }

    private fun writeDefaultIfMissing(file: Path, definition: Any) {
        if (file.exists()) return
        file.parent.createDirectories()
        Files.createTempFile(file.parent, file.fileName.toString(), ".tmp").also { temp ->
            TomlConfigIO.write(temp, definition)
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun defaultJobs(): List<RoleDefinition> = listOf(
        typedJob("botanist", "Botanist", "grass", "Reads leaf, vine, and bloom signs to work with Grass Pokemon."),
        typedJob("diver", "Diver", "water", "Works rivers, reefs, and rain routes to track Water Pokemon."),
        typedJob("magma_scout", "Magma Scout", "fire", "Scouts hot springs, ash fields, and lava paths for Fire Pokemon."),
        typedJob("engineer", "Engineer", "electric", "Tunes circuits, rails, and storms to find Electric Pokemon."),
        typedJob("field_researcher", "Field Researcher", "normal", "Studies everyday habitats and migration trails for Normal Pokemon."),
        typedJob("bug_scout", "Bug Scout", "bug", "Checks groves, flowers, and old logs for Bug Pokemon."),
        typedJob("falconer", "Falconer", "flying", "Reads wind lanes and high nests to work with Flying Pokemon."),
        typedJob("shade_runner", "Shade Runner", "dark", "Moves through alleys, caves, and moonlit routes for Dark Pokemon."),
        typedJob("esper", "Esper", "psychic", "Follows strange signals and quiet pressure around Psychic Pokemon."),
        typedJob("martial_artist", "Martial Artist", "fighting", "Studies training grounds and hard roads for Fighting Pokemon."),
        typedJob("mountaineer", "Mountaineer", "ice", "Crosses frost lines and high passes to find Ice Pokemon."),
        typedJob("shinobi", "Shinobi", "poison", "Tracks marshes, spores, and hidden dens for Poison Pokemon."),
        typedJob("mason", "Mason", "rock", "Reads cliffs, ruins, and quarry marks to find Rock Pokemon."),
        typedJob("excavator", "Excavator", "ground", "Cuts tunnels and studies soil shifts for Ground Pokemon."),
        typedJob("blacksmith", "Blacksmith", "steel", "Works anvils, ore, and machine scrap around Steel Pokemon."),
        typedJob("spirit_medium", "Spirit Medium", "ghost", "Listens at old paths and quiet places for Ghost Pokemon."),
        typedJob("drake_tamer", "Drake Tamer", "dragon", "Studies old scales, high dens, and legends around Dragon Pokemon."),
        typedJob("performer", "Performer", "fairy", "Uses rhythm, charm, and bright places to meet Fairy Pokemon."),
    )

    private fun typedJob(id: String, displayName: String, pokemonType: String, description: String): RoleDefinition = RoleDefinition(
        id = id,
        displayName = displayName,
        icon = "textures/gui/jobs/$id.png",
        description = description,
        perks = mutableListOf(
            RolePerkDefinition(
                type = "cobblemon_catch_rate",
                pokemonType = pokemonType,
                multiplier = 1.05,
            ),
            RolePerkDefinition(
                type = "mount_speed",
                pokemonType = pokemonType,
            ),
        ),
    )

    private fun defaultJobScaling(): JobScalingDefinition = JobScalingDefinition(
        jobRankUnlockOverallLevels = JobLevels.fallbackJobRankUnlockOverallLevels.toMutableList(),
        catchRateBonusPercentByRank = JobLevels.fallbackCatchRateBonusPercentByRank.toMutableList(),
        mountSpeedBonusPercentByRank = JobLevels.fallbackMountSpeedBonusPercentByRank.toMutableList(),
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
