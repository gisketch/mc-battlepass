package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.random.Random

object RolesConfig {
    private val gson = GsonBuilder().create()
    private var jobsById: Map<String, RoleDefinition> = emptyMap()
    private var classesById: Map<String, RoleDefinition> = emptyMap()
    private var onboardingDefinition = RolesOnboardingDefinition()
    private var jobScalingDefinition = JobScalingDefinition()
    private var classLicenseDefinition = ClassLicenseDefinition()
    private var equipmentWhitelistDefinition = EquipmentWhitelistDefinition()
    private var spellWhitelistDefinition = SpellWhitelistDefinition()

    private val root: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("roles")

    fun load() {
        root.resolve("jobs").createDirectories()
        root.resolve("classes").createDirectories()
        writeDefaultIfMissing(root.resolve("onboarding.toml"), defaultOnboarding())
        writeDefaultIfMissing(root.resolve("job_scaling.toml"), defaultJobScaling())
        writeDefaultIfMissing(root.resolve("class_licenses.toml"), defaultClassLicenses())
        writeDefaultIfMissing(root.resolve("equipment_whitelist.toml"), EquipmentWhitelistDefinition())
        writeDefaultIfMissing(root.resolve("spell_whitelist.toml"), SpellWhitelistDefinition())
        defaultJobs().forEach { definition -> writeDefaultIfMissing(root.resolve("jobs").resolve("${definition.id}.toml"), definition) }
        writeDefaultIfMissing(root.resolve("classes").resolve("rogue.toml"), defaultRogue())
        writeDefaultIfMissing(root.resolve("classes").resolve("warrior.toml"), defaultWarrior())
        onboardingDefinition = readOnboarding(root.resolve("onboarding.toml"))
        jobScalingDefinition = readJobScaling(root.resolve("job_scaling.toml"))
        classLicenseDefinition = readClassLicenses(root.resolve("class_licenses.toml"))
        equipmentWhitelistDefinition = readEquipmentWhitelist(root.resolve("equipment_whitelist.toml"))
        spellWhitelistDefinition = readSpellWhitelist(root.resolve("spell_whitelist.toml"))
        jobsById = loadDefinitions(root.resolve("jobs"))
        classesById = loadDefinitions(root.resolve("classes"))
    }

    fun jobs(): Collection<RoleDefinition> = jobsById.values

    fun classes(): Collection<RoleDefinition> = classesById.values

    fun sortedClassesForOnboarding(): List<RoleDefinition> = classesById.values.sortedWith(compareBy<RoleDefinition> { classSortGroup(it.id) }.thenBy { it.displayName.ifBlank { it.id } })

    fun job(id: String): RoleDefinition? = roleByIdOrName(jobsById, id)

    fun roleClass(id: String): RoleDefinition? = roleByIdOrName(classesById, id)

    fun classClassification(id: String): String = classClassification(roleClass(id))

    fun classClassification(role: RoleDefinition?): String {
        val explicit = role?.classification?.trim()?.lowercase().orEmpty()
        if (explicit == CLASS_STARTER || explicit == CLASS_UPGRADE) return explicit
        return if (role?.id in STARTER_CLASS_IDS) CLASS_STARTER else CLASS_UPGRADE
    }

    fun isStarterClass(id: String): Boolean = classClassification(id) == CLASS_STARTER

    fun starterClassIds(role: RoleDefinition): List<String> {
        val explicit = role.starterClassIds.map(String::trim).filter(String::isNotBlank)
        return explicit.ifEmpty { FALLBACK_UPGRADE_STARTERS[role.id].orEmpty() }
    }

    fun upgradeClassIds(role: RoleDefinition): List<String> {
        val explicit = role.upgradeClassIds.map(String::trim).filter(String::isNotBlank)
        return explicit.ifEmpty { FALLBACK_STARTER_UPGRADES[role.id].orEmpty() }
    }

    fun defaultJobId(): String = job("botanist")?.id ?: jobsById.keys.firstOrNull().orEmpty()

    fun defaultClassId(): String = roleClass("rogue")?.id ?: classesById.keys.firstOrNull(::isStarterClass) ?: classesById.keys.firstOrNull().orEmpty()

    fun welcomeContent(): String {
        val lines = onboardingDefinition.welcomeContent.map(String::trim).filter(String::isNotBlank)
        return lines.getOrNull(Random.nextInt(lines.size.coerceAtLeast(1))) ?: DEFAULT_WELCOME_CONTENT
    }

    fun jobScaling(): JobScalingDefinition = jobScalingDefinition

    fun classLicenses(): ClassLicenseDefinition = classLicenseDefinition

    fun equipmentWhitelist(): EquipmentWhitelistDefinition = equipmentWhitelistDefinition

    fun spellWhitelist(): SpellWhitelistDefinition = spellWhitelistDefinition

    fun configRoot(): Path = root

    private fun roleByIdOrName(rolesById: Map<String, RoleDefinition>, rawId: String): RoleDefinition? {
        val id = rawId.trim()
        return rolesById[id]
            ?: rolesById.entries.firstOrNull { (key, role) -> key.equals(id, ignoreCase = true) || role.displayName.equals(id, ignoreCase = true) }?.value
    }

    private fun loadDefinitions(directory: Path): Map<String, RoleDefinition> = Files.list(directory).use { stream ->
        stream.filter { path -> path.extension.equals("toml", ignoreCase = true) }
            .map { path -> readDefinition(path) }
            .map(::withBundledDefaultPerks)
            .map(::withSpellEquipmentItemPatterns)
            .filter { definition -> definition.id.isNotBlank() }
            .toList()
            .associateBy { definition -> definition.id }
    }

    private fun withBundledDefaultPerks(definition: RoleDefinition): RoleDefinition {
        val classSpellAffinity = defaultClassSpellAffinity(definition.id)
        if (classSpellAffinity != null && definition.perks.none { perk -> perk.type == "spell_affinity" }) {
            definition.perks += classSpellAffinity
        }
        val bundled = defaultJobs().firstOrNull { job -> job.id == definition.id } ?: return definition
        definition.perks.forEach { existing ->
            val defaultPerk = bundled.perks.firstOrNull { perk -> perk.type == existing.type && perk.pokemonType == existing.pokemonType } ?: return@forEach
            if (existing.bonusPercentByLevel.isEmpty() && defaultPerk.bonusPercentByLevel.isNotEmpty()) existing.bonusPercentByLevel += defaultPerk.bonusPercentByLevel
        }
        bundled.perks
            .filter { perk -> perk.type in BUNDLED_BACKFILL_PERK_TYPES }
            .filterNot { defaultPerk -> definition.perks.any { perk -> perk.type == defaultPerk.type && perk.pokemonType == defaultPerk.pokemonType } }
            .forEach { perk -> definition.perks += perk.copy() }
        return definition
    }

    private fun withSpellEquipmentItemPatterns(definition: RoleDefinition): RoleDefinition {
        val spellItemPatterns = spellEquipmentItemPatterns(definition.perks)
        val equipment = definition.perks.filter { perk -> perk.type == "equipment_affinity" }
        equipment.forEach { perk ->
            perk.weaponTags.removeAll { tag -> tag.equals("accessories:spell_book", ignoreCase = true) }
            spellItemPatterns
                .filterNot { pattern -> perk.weaponPatterns.any { existing -> existing.equals(pattern, ignoreCase = true) } }
                .forEach { pattern -> perk.weaponPatterns += pattern }
        }
        return definition
    }

    private fun spellEquipmentItemPatterns(perks: List<RolePerkDefinition>): List<String> = RoleSpellEquipmentPatterns.fromPerks(perks)

    private fun classSortGroup(id: String): Int = when {
        isStarterClass(id) -> 0
        else -> 1
    }

    private fun readDefinition(path: Path): RoleDefinition = try {
        TomlConfigIO.read(path, RoleDefinition::class.java, ::RoleDefinition).also { definition ->
            if (definition.perks.isEmpty()) definition.perks += misplacedRootPerks(path)
        }
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role definition {}", path, exception)
        RoleDefinition()
    }

    private fun misplacedRootPerks(path: Path): List<RolePerkDefinition> {
        val json = TomlConfigIO.readObject(path) ?: return emptyList()
        val mentorQuest = json.getAsJsonObject("mentor_quest") ?: return emptyList()
        val perks = mentorQuest.getAsJsonArray("perks") ?: return emptyList()
        ChowKingdomMod.LOGGER.warn("Loaded misplaced root perks from {}. Move perks before [mentor_quest] in TOML.", path)
        return perks.mapNotNull { element ->
            runCatching { gson.fromJson(element, RolePerkDefinition::class.java) }.getOrNull()
        }
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

    private fun readClassLicenses(path: Path): ClassLicenseDefinition = try {
        TomlConfigIO.read(path, ClassLicenseDefinition::class.java, ::defaultClassLicenses)
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role class license config {}", path, exception)
        defaultClassLicenses()
    }

    private fun readEquipmentWhitelist(path: Path): EquipmentWhitelistDefinition = try {
        TomlConfigIO.read(path, EquipmentWhitelistDefinition::class.java, ::EquipmentWhitelistDefinition)
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role equipment whitelist config {}", path, exception)
        EquipmentWhitelistDefinition()
    }

    private fun readSpellWhitelist(path: Path): SpellWhitelistDefinition = try {
        TomlConfigIO.read(path, SpellWhitelistDefinition::class.java, ::SpellWhitelistDefinition)
    } catch (exception: Exception) {
        ChowKingdomMod.LOGGER.warn("Failed to load role spell whitelist config {}", path, exception)
        SpellWhitelistDefinition()
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
        botanistJob(),
        diverJob(),
        magmaScoutJob(),
        engineerJob(),
        fieldResearcherJob(),
        bugScoutJob(),
        falconerJob(),
        shadeRunnerJob(),
        esperJob(),
        martialArtistJob(),
        mountaineerJob(),
        shinobiJob(),
        masonJob(),
        excavatorJob(),
        blacksmithJob(),
        spiritMediumJob(),
        drakeTamerJob(),
        performerJob(),
    )

    private fun typedJob(id: String, displayName: String, pokemonType: String, description: String): RoleDefinition = RoleDefinition(
        id = id,
        displayName = displayName,
        icon = "textures/gui/jobs/$id.png",
        description = description,
        previewItems = defaultJobPreviewItems(id).toMutableList(),
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

    private fun botanistJob(): RoleDefinition = typedJob("botanist", "Botanist", "grass", "Reads leaf, vine, and bloom signs to work with Grass Pokemon.").also { role ->
        role.perks += RolePerkDefinition(
            type = "crop_bonus_drop_chance",
            bonusPercentByLevel = DEFAULT_BOTANIST_HARVEST_CHANCES.toMutableList(),
        )
        role.perks += RolePerkDefinition(
            type = "quality_harvest_upgrade_chance",
            bonusPercentByLevel = DEFAULT_BOTANIST_HARVEST_CHANCES.toMutableList(),
        )
        role.perks += RolePerkDefinition(
            type = "gentle_steps",
        )
        role.perks += RolePerkDefinition(
            type = "seasonal_farmer",
            bonusPercentByLevel = mutableListOf(DEFAULT_SEASONAL_FARMER_GROWTH_CHANCE),
        )
    }

    private fun diverJob(): RoleDefinition = typedJob("diver", "Diver", "water", "Works rivers, reefs, and rain routes to track Water Pokemon.").also { role ->
        role.perks += RolePerkDefinition(
            type = "swim_speed",
            bonusPercentByLevel = mutableListOf(0.08, 0.14, 0.22, 0.32, 0.45),
        )
        role.perks += RolePerkDefinition(
            type = "underwater_mining_penalty_reduction",
            bonusPercentByLevel = mutableListOf(0.15, 0.25, 0.40, 0.60, 0.80),
        )
        role.perks += RolePerkDefinition(
            type = "fishing_bonus_drop_chance",
            bonusPercentByLevel = mutableListOf(0.10),
        )
        role.perks += RolePerkDefinition(
            type = "rain_catch_rate_bonus",
            pokemonType = "water",
            bonusPercentByLevel = mutableListOf(0.20),
        )
    }

    private fun magmaScoutJob(): RoleDefinition = typedJob("magma_scout", "Magma Scout", "fire", "Scouts hot springs, ash fields, and lava paths for Fire Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "fire_damage_reduction",
            bonusPercentByLevel = mutableListOf(0.10, 0.18, 0.28, 0.40, 0.55),
        )
        role.perks += RolePerkDefinition(
            type = "lava_walker",
            bonusPercentByLevel = mutableListOf(0.12, 0.20, 0.28, 0.36, 0.45),
        )
        role.perks += RolePerkDefinition(
            type = "nether_hunter_catch_rate_bonus",
            pokemonType = "fire",
            bonusPercentByLevel = mutableListOf(0.15),
        )
        role.perks += RolePerkDefinition(
            type = "heat_burst",
        )
    }

    private fun engineerJob(): RoleDefinition = typedJob("engineer", "Engineer", "electric", "Tunes circuits, rails, and storms to find Electric Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "tool_mining_speed",
            bonusPercentByLevel = mutableListOf(0.02, 0.04, 0.06, 0.08, 0.10),
        )
        role.perks += RolePerkDefinition(
            type = "magnet",
            bonusPercentByLevel = mutableListOf(1.5, 2.0, 2.5, 3.0, 3.5),
        )
        role.perks += RolePerkDefinition(
            type = "technician_reach",
            bonusPercentByLevel = mutableListOf(0.25, 0.50, 0.75, 1.0, 1.25),
        )
        role.perks += RolePerkDefinition(
            type = "charged_maintenance",
            bonusPercentByLevel = mutableListOf(0.02, 0.03, 0.04, 0.05, 0.06),
        )
    }

    private fun fieldResearcherJob(): RoleDefinition = typedJob("field_researcher", "Field Researcher", "normal", "Studies everyday habitats and migration trails for Normal Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "luck_lite",
            bonusPercentByLevel = mutableListOf(1.0, 2.0, 3.0, 4.0, 5.0),
        )
        role.perks += RolePerkDefinition(
            type = "surveyor_chowcoins",
            bonusPercentByLevel = mutableListOf(2.0, 4.0, 6.0, 8.0, 10.0),
        )
        role.perks += RolePerkDefinition(
            type = "first_encounter_bp_xp",
            bonusPercentByLevel = mutableListOf(5.0),
        )
        role.perks += RolePerkDefinition(
            type = "field_notes",
            rewardPool = mutableListOf(
                "cobblemon:rare_candy",
                "cobblemon:exp_candy_s*2",
                "cobblemon:poke_ball*8",
                "cobblemon:great_ball*4",
            ),
        )
    }

    private fun bugScoutJob(): RoleDefinition = typedJob("bug_scout", "Bug Scout", "bug", "Checks groves, flowers, and old logs for Bug Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "arthropod_damage_bonus",
            bonusPercentByLevel = mutableListOf(0.04, 0.08, 0.12, 0.16, 0.20),
        )
        role.perks += RolePerkDefinition(
            type = "web_walker",
            bonusPercentByLevel = mutableListOf(0.20, 0.35, 0.50, 0.65, 0.80),
        )
        role.perks += RolePerkDefinition(
            type = "tiny_forager",
            bonusPercentByLevel = mutableListOf(0.03),
            rewardPool = mutableListOf(
                "minecraft:wheat_seeds",
                "minecraft:sweet_berries",
                "minecraft:string",
                "minecraft:spider_eye",
            ),
        )
        role.perks += RolePerkDefinition(
            type = "swarm_sense",
        )
    }

    private fun falconerJob(): RoleDefinition = typedJob("falconer", "Falconer", "flying", "Reads wind lanes and high nests to work with Flying Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "fall_damage_reduction",
            bonusPercentByLevel = mutableListOf(0.10, 0.20, 0.30, 0.40, 0.50),
        )
        role.perks += RolePerkDefinition(
            type = "slow_fall_lite",
            bonusPercentByLevel = mutableListOf(1.0, 2.0, 3.0, 4.0, 5.0),
        )
        role.perks += RolePerkDefinition(
            type = "high_ground_speed",
            bonusPercentByLevel = mutableListOf(0.05),
        )
        role.perks += RolePerkDefinition(
            type = "scouts_leap",
            bonusPercentByLevel = mutableListOf(0.25),
        )
    }

    private fun shadeRunnerJob(): RoleDefinition = typedJob("shade_runner", "Shade Runner", "dark", "Moves through alleys, caves, and moonlit routes for Dark Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "swift_sneak_lite",
            bonusPercentByLevel = mutableListOf(0.10, 0.20, 0.30, 0.40, 0.50),
        )
        role.perks += RolePerkDefinition(
            type = "nightstep",
            bonusPercentByLevel = mutableListOf(0.02, 0.04, 0.06, 0.08, 0.10),
        )
        role.perks += RolePerkDefinition(
            type = "backstab_lite",
            bonusPercentByLevel = mutableListOf(0.15),
        )
        role.perks += RolePerkDefinition(
            type = "shadow_escape",
        )
    }

    private fun esperJob(): RoleDefinition = typedJob("esper", "Esper", "psychic", "Follows strange signals and quiet pressure around Psychic Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "projectile_damage_reduction",
            bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.15, 0.20, 0.25),
        )
        role.perks += RolePerkDefinition(
            type = "telekinesis_lite",
            bonusPercentByLevel = mutableListOf(0.5, 1.0, 1.5, 2.0, 2.5),
        )
        role.perks += RolePerkDefinition(
            type = "focus_mind",
        )
        role.perks += RolePerkDefinition(
            type = "premonition",
        )
    }

    private fun martialArtistJob(): RoleDefinition = typedJob("martial_artist", "Martial Artist", "fighting", "Studies training grounds and hard roads for Fighting Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "knockback_lite",
            bonusPercentByLevel = mutableListOf(0.03, 0.06, 0.09, 0.12, 0.15),
        )
        role.perks += RolePerkDefinition(
            type = "agility_lite",
            bonusPercentByLevel = mutableListOf(0.02, 0.04, 0.06, 0.08, 0.10),
        )
        role.perks += RolePerkDefinition(
            type = "combo_flow",
        )
        role.perks += RolePerkDefinition(
            type = "second_wind",
        )
    }

    private fun mountaineerJob(): RoleDefinition = typedJob("mountaineer", "Mountaineer", "ice", "Crosses frost lines and high passes to find Ice Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "freeze_damage_reduction",
            bonusPercentByLevel = mutableListOf(0.15, 0.30, 0.45, 0.60, 0.75),
        )
        role.perks += RolePerkDefinition(
            type = "step_assist_lite",
            bonusPercentByLevel = mutableListOf(0.10, 0.20, 0.30, 0.40, 0.50),
        )
        role.perks += RolePerkDefinition(
            type = "coldproof",
        )
        role.perks += RolePerkDefinition(
            type = "climber",
        )
    }

    private fun shinobiJob(): RoleDefinition = typedJob("shinobi", "Shinobi", "poison", "Tracks marshes, spores, and hidden dens for Poison Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "poison_aspect_lite",
            bonusPercentByLevel = mutableListOf(0.02, 0.04, 0.06, 0.08, 0.10),
        )
        role.perks += RolePerkDefinition(
            type = "shinobi_sneak_speed",
            bonusPercentByLevel = mutableListOf(0.08, 0.16, 0.24, 0.32, 0.40),
        )
        role.perks += RolePerkDefinition(
            type = "toxic_resistance",
        )
        role.perks += RolePerkDefinition(
            type = "smoke_step",
        )
    }

    private fun masonJob(): RoleDefinition = typedJob("mason", "Mason", "rock", "Reads cliffs, ruins, and quarry marks to find Rock Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "explosion_damage_reduction",
            bonusPercentByLevel = mutableListOf(0.05, 0.08, 0.12, 0.16, 0.20),
        )
        role.perks += RolePerkDefinition(
            type = "builders_reach",
            bonusPercentByLevel = mutableListOf(0.5, 0.75, 1.0, 1.5, 2.0),
        )
        role.perks += RolePerkDefinition(
            type = "steady_hands",
            bonusPercentByLevel = mutableListOf(0.03),
        )
        role.perks += RolePerkDefinition(
            type = "masons_eye",
        )
    }

    private fun excavatorJob(): RoleDefinition = typedJob("excavator", "Excavator", "ground", "Cuts tunnels and studies soil shifts for Ground Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "terrain_mining_speed",
            bonusPercentByLevel = mutableListOf(0.04, 0.08, 0.12, 0.16, 0.20),
        )
        role.perks += RolePerkDefinition(
            type = "excavation_lite",
        )
        role.perks += RolePerkDefinition(
            type = "archaeologist",
            bonusPercentByLevel = mutableListOf(0.05),
            rewardPool = mutableListOf("minecraft:flint", "minecraft:iron_nugget*2", "minecraft:gold_nugget", "minecraft:emerald"),
        )
        role.perks += RolePerkDefinition(
            type = "tunnel_sense",
        )
    }

    private fun blacksmithJob(): RoleDefinition = typedJob("blacksmith", "Blacksmith", "steel", "Works anvils, ore, and machine scrap around Steel Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "unbreaking_lite",
            bonusPercentByLevel = mutableListOf(0.02, 0.04, 0.06, 0.08, 0.10),
        )
        role.perks += RolePerkDefinition(
            type = "repairing_lite",
            bonusPercentByLevel = mutableListOf(0.02, 0.03, 0.04, 0.05, 0.06),
        )
        role.perks += RolePerkDefinition(
            type = "forge_discount",
            bonusPercentByLevel = mutableListOf(0.20),
        )
        role.perks += RolePerkDefinition(
            type = "ore_tempering",
            bonusPercentByLevel = mutableListOf(0.10),
        )
    }

    private fun spiritMediumJob(): RoleDefinition = typedJob("spirit_medium", "Spirit Medium", "ghost", "Listens at old paths and quiet places for Ghost Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "soul_speed_lite",
            bonusPercentByLevel = mutableListOf(0.10, 0.20, 0.30, 0.40, 0.50),
        )
        role.perks += RolePerkDefinition(
            type = "ethereal_step_lite",
        )
        role.perks += RolePerkDefinition(
            type = "spirit_sight",
        )
        role.perks += RolePerkDefinition(
            type = "grave_whisper",
            bonusPercentByLevel = mutableListOf(0.05),
        )
    }

    private fun drakeTamerJob(): RoleDefinition = typedJob("drake_tamer", "Drake Tamer", "dragon", "Studies old scales, high dens, and legends around Dragon Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "protection_lite",
            bonusPercentByLevel = mutableListOf(0.02, 0.04, 0.06, 0.08, 0.10),
        )
        role.perks += RolePerkDefinition(
            type = "dragon_mount_velocity",
            bonusPercentByLevel = mutableListOf(0.04, 0.08, 0.12, 0.16, 0.20),
        )
        role.perks += RolePerkDefinition(
            type = "draconic_presence",
        )
        role.perks += RolePerkDefinition(
            type = "treasure_sense",
            bonusPercentByLevel = mutableListOf(0.03),
        )
    }

    private fun performerJob(): RoleDefinition = typedJob("performer", "Performer", "fairy", "Uses rhythm, charm, and bright places to meet Fairy Pokemon.").also { role ->
        role.perks.firstOrNull { perk -> perk.type == "cobblemon_catch_rate" }?.bonusPercentByLevel = mutableListOf(0.05, 0.10, 0.18, 0.28, 0.40)
        role.perks.firstOrNull { perk -> perk.type == "mount_speed" }?.bonusPercentByLevel = mutableListOf(0.03, 0.05, 0.09, 0.14, 0.20)
        role.perks += RolePerkDefinition(
            type = "charisma_lite",
            bonusPercentByLevel = mutableListOf(0.03, 0.06, 0.09, 0.12, 0.15),
        )
        role.perks += RolePerkDefinition(
            type = "happy_boost_lite",
        )
        role.perks += RolePerkDefinition(
            type = "charming_gift",
            bonusPercentByLevel = mutableListOf(10.0),
        )
        role.perks += RolePerkDefinition(
            type = "encore",
            bonusPercentByLevel = mutableListOf(0.10),
        )
    }

    private fun defaultJobScaling(): JobScalingDefinition = JobScalingDefinition(
        jobRankUnlockOverallLevels = JobLevels.fallbackJobRankUnlockOverallLevels.toMutableList(),
        catchRateBonusPercentByRank = JobLevels.fallbackCatchRateBonusPercentByRank.toMutableList(),
        mountSpeedBonusPercentByRank = JobLevels.fallbackMountSpeedBonusPercentByRank.toMutableList(),
    )

    private fun defaultClassLicenses(): ClassLicenseDefinition = ClassLicenseDefinition(
        starterLicenseUnlockOverallLevels = ClassLicenses.fallbackStarterUnlockOverallLevels.toMutableList(),
        upgradeLicenseUnlockOverallLevels = ClassLicenses.fallbackUpgradeUnlockOverallLevels.toMutableList(),
    )

    private val DEFAULT_BOTANIST_HARVEST_CHANCES = listOf(0.02, 0.04, 0.06, 0.08, 0.10)
    private const val DEFAULT_SEASONAL_FARMER_GROWTH_CHANCE = 0.10
    private val BUNDLED_BACKFILL_PERK_TYPES = setOf(
        "mount_speed",
        "crop_bonus_drop_chance",
        "quality_harvest_upgrade_chance",
        "gentle_steps",
        "seasonal_farmer",
        "swim_speed",
        "underwater_mining_penalty_reduction",
        "fishing_bonus_drop_chance",
        "rain_catch_rate_bonus",
        "fire_damage_reduction",
        "lava_walker",
        "nether_hunter_catch_rate_bonus",
        "heat_burst",
        "tool_mining_speed",
        "magnet",
        "technician_reach",
        "charged_maintenance",
        "luck_lite",
        "surveyor_chowcoins",
        "first_encounter_bp_xp",
        "field_notes",
        "arthropod_damage_bonus",
        "web_walker",
        "tiny_forager",
        "swarm_sense",
        "fall_damage_reduction",
        "slow_fall_lite",
        "high_ground_speed",
        "scouts_leap",
        "swift_sneak_lite",
        "nightstep",
        "backstab_lite",
        "shadow_escape",
        "projectile_damage_reduction",
        "telekinesis_lite",
        "focus_mind",
        "premonition",
        "knockback_lite",
        "agility_lite",
        "combo_flow",
        "second_wind",
        "freeze_damage_reduction",
        "step_assist_lite",
        "coldproof",
        "climber",
        "poison_aspect_lite",
        "shinobi_sneak_speed",
        "toxic_resistance",
        "smoke_step",
        "explosion_damage_reduction",
        "builders_reach",
        "steady_hands",
        "masons_eye",
        "terrain_mining_speed",
        "excavation_lite",
        "archaeologist",
        "tunnel_sense",
        "unbreaking_lite",
        "repairing_lite",
        "forge_discount",
        "ore_tempering",
        "soul_speed_lite",
        "ethereal_step_lite",
        "spirit_sight",
        "grave_whisper",
        "protection_lite",
        "dragon_mount_velocity",
        "draconic_presence",
        "treasure_sense",
        "charisma_lite",
        "happy_boost_lite",
        "charming_gift",
        "encore",
    )

    private fun defaultJobPreviewItems(id: String): List<String> = when (id) {
        "botanist" -> listOf("minecraft:wheat_seeds", "minecraft:grass_block")
        "diver" -> listOf("minecraft:fishing_rod", "minecraft:kelp")
        "magma_scout" -> listOf("minecraft:blaze_powder", "minecraft:magma_block")
        "engineer" -> listOf("minecraft:redstone", "minecraft:comparator")
        "field_researcher" -> listOf("minecraft:spyglass", "minecraft:paper")
        "bug_scout" -> listOf("minecraft:spider_eye", "minecraft:oak_leaves")
        "falconer" -> listOf("minecraft:feather", "minecraft:spyglass")
        "shade_runner" -> listOf("minecraft:echo_shard", "minecraft:ender_pearl")
        "esper" -> listOf("minecraft:amethyst_shard", "minecraft:ender_eye")
        "martial_artist" -> listOf("minecraft:leather_boots", "minecraft:iron_nugget")
        "mountaineer" -> listOf("minecraft:powder_snow_bucket", "minecraft:snowball")
        "shinobi" -> listOf("minecraft:poisonous_potato", "minecraft:fermented_spider_eye")
        "mason" -> listOf("minecraft:stone_bricks", "minecraft:brick")
        "excavator" -> listOf("minecraft:iron_shovel", "minecraft:brush")
        "blacksmith" -> listOf("minecraft:iron_ingot", "minecraft:anvil")
        "spirit_medium" -> listOf("minecraft:soul_lantern", "minecraft:echo_shard")
        "drake_tamer" -> listOf("minecraft:dragon_breath", "minecraft:amethyst_shard")
        "performer" -> listOf("minecraft:note_block", "minecraft:amethyst_shard")
        else -> emptyList()
    }

    private fun defaultRogue(): RoleDefinition = RoleDefinition(
        id = "rogue",
        displayName = "Rogue",
        icon = "textures/gui/classes/rogue.png",
        description = "Move light, hit hard, and favor quick gear built for sharp openings.",
        previewItems = mutableListOf("simplymore:iron_dagger", "minecraft:stone_sword"),
        classification = CLASS_STARTER,
        upgradeClassIds = mutableListOf("forcemaster", "bounty_hunter", "bard", "witcher"),
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
                weaponPatterns = mutableListOf("rogues:spell_book/rogue", "rogues:spell_scroll/rogue"),
                wrongWeaponDamageMultiplier = 0.2,
                wrongWeaponCooldownTicks = 12,
                wrongWeaponAttackSpeedMultiplier = 0.1,
                wrongArmorDisablesSprint = true,
            ),
            defaultClassSpellAffinity("rogue") ?: RolePerkDefinition(type = "spell_affinity"),
        ),
    )

    private fun defaultWarrior(): RoleDefinition = RoleDefinition(
        id = "warrior",
        displayName = "Warrior",
        icon = "textures/gui/classes/warrior.png",
        description = "Stand in the front line with sturdy weapons, heavier armor, and simple force.",
        previewItems = mutableListOf("minecraft:iron_sword", "minecraft:wooden_sword"),
        classification = CLASS_STARTER,
        upgradeClassIds = mutableListOf("berserker", "paladin", "witcher"),
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
                weaponPatterns = mutableListOf("rogues:spell_book/warrior", "rogues:spell_scroll/warrior"),
                wrongWeaponDamageMultiplier = 0.2,
                wrongWeaponCooldownTicks = 12,
                wrongWeaponAttackSpeedMultiplier = 0.1,
                wrongArmorDisablesSprint = true,
            ),
            defaultClassSpellAffinity("warrior") ?: RolePerkDefinition(type = "spell_affinity"),
        ),
    )

    private fun defaultClassSpellAffinity(classId: String): RolePerkDefinition? {
        val tagMap = mapOf(
            "warrior" to listOf("rogues:spell_book/warrior"),
            "rogue" to listOf("rogues:spell_book/rogue"),
            "archer" to listOf("archers:spell_book/archer"),
            "war_archer" to listOf("archers_expansion:spell_book/war_archer"),
            "tundra_archer" to listOf("archers_expansion:spell_book/tundra_hunter"),
            "bounty_hunter" to listOf("archers_expansion:spell_book/deadeye"),
            "wizard" to listOf("wizards:weapon/arcane_staff", "wizards:weapon/fire_staff", "wizards:weapon/frost_staff"),
            "arcane_wizard" to listOf("wizards:spell_book/arcane", "wizards:weapon/arcane_staff"),
            "fire_wizard" to listOf("wizards:spell_book/fire", "wizards:weapon/fire_staff"),
            "frost_wizard" to listOf("wizards:spell_book/frost", "wizards:weapon/frost_staff"),
            "water_wizard" to listOf("elemental_wizards_rpg:spell_book/aqua", "elemental_wizards_rpg:weapon/aqua_staff"),
            "earth_wizard" to listOf("elemental_wizards_rpg:spell_book/terra", "elemental_wizards_rpg:weapon/terra_staff"),
            "wind_wizard" to listOf("elemental_wizards_rpg:spell_book/wind", "elemental_wizards_rpg:weapon/wind_staff"),
            "priest" to listOf(
                "paladins:spell_book/priest",
                "paladins:weapon/holy_staff",
                "paladins:weapon/holy_wand",
            ),
            "paladin" to listOf("paladins:spell_book/paladin"),
            "berserker" to listOf("berserker_rpg:spell_book/berserker"),
            "forcemaster" to listOf("forcemaster_rpg:spell_book/forcemaster"),
            "bard" to listOf(
                "bards_rpg:spell_book/bard",
                "bards_rpg:weapon/lute",
                "bards_rpg:weapon/lyre",
                "bards_rpg:songs",
            ),
            "witcher" to listOf(
                "witcher_rpg:spell_book/fencing",
                "witcher_rpg:spell_book/signs",
            ),
        )
        val idMap = mapOf(
            "warrior" to listOf("rogues:throw", "rogues:shout", "rogues:charge"),
            "rogue" to listOf("rogues:slice_and_dice", "rogues:shock_powder", "rogues:shadow_step", "rogues:vanish"),
            "archer" to listOf("archers:power_shot", "archers:entangling_roots", "archers:barrage", "archers:magic_arrow"),
            "war_archer" to listOf("archers_expansion:dual_shot", "archers_expansion:smoldering_arrow", "archers_expansion:point_blank_shot", "archers_expansion:pin_down", "archers_expansion:fan_of_fire", "archers_expansion:improved_point_blank_shot"),
            "tundra_archer" to listOf("archers_expansion:frozen_shot", "archers_expansion:frozen_pact", "archers_expansion:arctic_volley", "archers_expansion:enchanted_crystal_arrow", "archers_expansion:improved_arctic_volley", "archers_expansion:winters_grip"),
            "bounty_hunter" to listOf("archers_expansion:fast_shot", "archers_expansion:trick_shot", "archers_expansion:disabling_shot", "archers_expansion:choking_gas", "archers_expansion:alter_ego", "archers_expansion:improved_disabling_shot", "archers_expansion:infiltrators_arrow"),
            "wizard" to listOf("wizards:arcane_blast", "wizards:fire_blast", "wizards:frostbolt"),
            "arcane_wizard" to listOf("wizards:arcane_blast", "wizards:arcane_bolt"),
            "fire_wizard" to listOf("wizards:fire_blast", "wizards:fireball", "wizards:fire_scorch"),
            "frost_wizard" to listOf("wizards:frostbolt", "wizards:frost_shard"),
            "water_wizard" to listOf("elemental_wizards_rpg:aqua_water_whip", "elemental_wizards_rpg:aqua_splash", "elemental_wizards_rpg:elemental_avatar", "elemental_wizards_rpg:improved_aqua_springwater"),
            "earth_wizard" to listOf("elemental_wizards_rpg:terra_stone_spear", "elemental_wizards_rpg:terra_stone_throw", "elemental_wizards_rpg:elemental_avatar", "elemental_wizards_rpg:improved_terra_drip_circle"),
            "wind_wizard" to listOf("elemental_wizards_rpg:wind_air_cutter", "elemental_wizards_rpg:wind_gust", "elemental_wizards_rpg:elemental_avatar", "elemental_wizards_rpg:improved_wind_updraft"),
            "priest" to listOf("paladins:holy_shock", "paladins:heal"),
        )
        val patternMap = mapOf(
            "bard" to listOf("bards_rpg:*"),
            "berserker" to listOf("berserker_rpg:*"),
            "forcemaster" to listOf("forcemaster_rpg:*"),
            "witcher" to listOf("witcher_rpg:*"),
        )
        val tags = tagMap[classId].orEmpty().withSpellScrollTags()
        val ids = idMap[classId].orEmpty()
        val patterns = patternMap[classId].orEmpty()
        if (tags.isEmpty() && ids.isEmpty() && patterns.isEmpty()) return null
        return RolePerkDefinition(
            type = "spell_affinity",
            spellIds = ids.toMutableList(),
            spellTags = tags.toMutableList(),
            spellPatterns = patterns.toMutableList(),
        )
    }

    private fun List<String>.withSpellScrollTags(): List<String> = (this + mapNotNull { tag ->
        val path = tag.substringAfter(':', missingDelimiterValue = "")
        if (!path.startsWith("spell_book/")) null else tag.replace(":spell_book/", ":spell_scroll/")
    }).distinct()

    private fun defaultOnboarding(): RolesOnboardingDefinition = RolesOnboardingDefinition(
        welcomeContent = mutableListOf(
            DEFAULT_WELCOME_CONTENT,
            "Pick your place in the kingdom before the road opens.",
            "Every story starts with work to do and a way to fight.",
        ),
    )

    private const val DEFAULT_WELCOME_CONTENT = "A new chapter begins in Chowkingdom. Choose how you work, then choose how you fight."
    const val CLASS_STARTER = "starter"
    const val CLASS_UPGRADE = "upgrade"
    private val STARTER_CLASS_IDS = setOf("warrior", "rogue", "archer", "wizard", "priest")
    private val FALLBACK_STARTER_UPGRADES = mapOf(
        "warrior" to listOf("berserker", "paladin", "witcher"),
        "rogue" to listOf("forcemaster", "bounty_hunter", "bard", "witcher"),
        "archer" to listOf("war_archer", "tundra_archer", "bounty_hunter", "bard"),
        "wizard" to listOf("arcane_wizard", "fire_wizard", "frost_wizard", "water_wizard", "earth_wizard", "wind_wizard", "witcher"),
        "priest" to listOf("paladin", "bard"),
    )
    private val FALLBACK_UPGRADE_STARTERS = FALLBACK_STARTER_UPGRADES.entries
        .flatMap { (starterId, upgradeIds) -> upgradeIds.map { upgradeId -> upgradeId to starterId } }
        .groupBy({ it.first }, { it.second })
}
