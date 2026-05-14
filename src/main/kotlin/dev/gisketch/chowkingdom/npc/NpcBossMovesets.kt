package dev.gisketch.chowkingdom.npc

import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object NpcBossMovesets {
    private const val DEFAULT_CLASS_ID = "warrior"
    private val root: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("npc_boss_movesets")
    private var movesets: Map<String, NpcBossMovesetDefinition> = emptyMap()

    fun load(): List<NpcBossMovesetDefinition> {
        root.createDirectories()
        defaultMovesets().forEach { definition ->
            val path = root.resolve("${definition.id}.toml")
            if (!path.exists()) TomlConfigIO.write(path, definition.normalized())
        }
        val loaded = movesetFiles().map { path ->
            TomlConfigIO.read(path, NpcBossMovesetDefinition::class.java, { NpcBossMovesetDefinition(id = path.nameWithoutExtension) })
                .also { definition -> if (definition.id.isBlank()) definition.id = path.nameWithoutExtension }
                .normalized()
        }
        movesets = loaded.associateBy { definition -> definition.id }
        return movesets.values.sortedBy { definition -> definition.id }
    }

    fun all(): List<NpcBossMovesetDefinition> {
        if (movesets.isEmpty()) load()
        return movesets.values.sortedBy { definition -> definition.id }
    }

    fun ids(): List<String> = all().map { definition -> definition.id }

    fun get(id: String): NpcBossMovesetDefinition? {
        val normalized = normalizeId(id)
        if (normalized.isBlank()) return null
        if (movesets.isEmpty()) load()
        return movesets[normalized]
    }

    fun forDefinition(definition: NpcDefinition): NpcBossMovesetDefinition {
        val classId = normalizeId(definition.classId)
        return if (classId.isNotBlank()) {
            get(classId) ?: get(DEFAULT_CLASS_ID) ?: defaultWarrior().normalized()
        } else {
            get(DEFAULT_CLASS_ID) ?: defaultWarrior().normalized()
        }
    }

    fun normalizeId(value: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9_.:-]+"), "_")
        .trim('_')

    private fun movesetFiles(): List<Path> {
        val paths = mutableListOf<Path>()
        Files.list(root).use { stream ->
            stream.forEach { path ->
                if (Files.isRegularFile(path) && path.extension.equals("toml", ignoreCase = true)) {
                    paths.add(path)
                }
            }
        }
        return paths.sortedBy { path -> path.fileName.toString() }
    }

    private fun defaultMovesets(): List<NpcBossMovesetDefinition> = listOf(
        defaultWarrior(),
        defaultRogue(),
        defaultArcher(),
        defaultWizard(),
        defaultWitcher(),
    )

    private fun defaultWarrior(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "warrior",
        displayName = "Warrior",
        health = 90.0,
        damage = 2.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 10,
        recoveryAnimationId = NpcBossMovesetDefinition.DEFAULT_RECOVERY_ANIMATION,
        recoveryHitsAllowed = 4,
        phases = mutableListOf(
            phase(
                id = "phase_1",
                displayName = "Phase 1",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.0,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 10,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.55,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "phase_2",
                displayName = "Phase 2",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.35,
                speedMultiplier = 1.25,
                offenseChainMin = 3,
                offenseChainRandom = 2,
                offenseChainRecoveryTicks = 8,
                transitionFallback = "Enough holding back. Now you face me for real.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, harder-hitting second phase. Reply as the warrior with one short in-character battle line. Sound focused, not defeated.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.6,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            melee("fast_slash", "bettercombat:one_handed_slash_horizontal_right", duration = 18, hitTick = 7, damage = 2.5, range = 2.8, arc = 100.0, cooldown = 16, recovery = 28, weight = 6),
            melee("left_slash", "bettercombat:one_handed_slash_horizontal_left", duration = 18, hitTick = 7, damage = 2.5, range = 2.8, arc = 100.0, cooldown = 16, recovery = 28, weight = 5),
            melee("stab", "bettercombat:one_handed_stab", duration = 17, hitTick = 6, damage = 3.0, range = 3.2, arc = 55.0, cooldown = 22, recovery = 32, weight = 4),
            melee("uppercut", "bettercombat:one_handed_uppercut_right", duration = 20, hitTick = 8, damage = 2.75, range = 2.8, arc = 85.0, cooldown = 26, recovery = 34, weight = 3),
            area("slam", "bettercombat:one_handed_slam", duration = 26, hitTick = 13, damage = 4.0, radius = 2.4, cooldown = 58, recovery = 46, weight = 2),
            area("battle_shout", "spell_engine:one_handed_shout_release", duration = 20, hitTick = 10, damage = 1.5, radius = 4.0, cooldown = 90, recovery = 36, weight = 2, spellId = "spell_engine:shout"),
            roll("combat_roll_backstep", "combat_roll:roll", duration = 14, cooldown = 70, distance = 3.0, direction = "back", weight = 2),
        ),
    )

    private fun defaultRogue(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "rogue",
        displayName = "Rogue",
        health = 75.0,
        damage = 2.5,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 9,
        recoveryAnimationId = NpcBossMovesetDefinition.DEFAULT_RECOVERY_ANIMATION,
        recoveryHitsAllowed = 4,
        phases = mutableListOf(
            phase(
                id = "phase_1",
                displayName = "Phase 1",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.08,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 9,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.5,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "phase_2",
                displayName = "Phase 2",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.25,
                speedMultiplier = 1.35,
                offenseChainMin = 3,
                offenseChainRandom = 2,
                offenseChainRecoveryTicks = 7,
                transitionFallback = "Enough. Now you will learn why patience wins the blade.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, more aggressive rogue second phase. Reply as the older Assassin mentor with one short in-character battle line. Sound calm, precise, and dangerous.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.58,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            melee("cross_slash", "bettercombat:dual_handed_slash_cross", duration = 17, hitTick = 7, damage = 2.25, range = 2.6, arc = 95.0, cooldown = 14, recovery = 24, weight = 5),
            melee("open_feint", "spell_engine:dual_handed_weapon_open", duration = 12, hitTick = 6, damage = 1.75, range = 2.4, arc = 90.0, cooldown = 16, recovery = 20, weight = 4),
            melee("blade_cross", "spell_engine:dual_handed_weapon_cross", duration = 14, hitTick = 6, damage = 2.25, range = 2.6, arc = 100.0, cooldown = 18, recovery = 22, weight = 4),
            melee("dagger_stab", "bettercombat:dual_handed_stab", duration = 15, hitTick = 6, damage = 2.75, range = 2.9, arc = 45.0, cooldown = 18, recovery = 28, weight = 4),
            melee("uncross", "bettercombat:dual_handed_slash_uncross", duration = 18, hitTick = 8, damage = 2.0, range = 2.8, arc = 110.0, cooldown = 18, recovery = 26, weight = 3),
            melee("uncross_swipe", "spell_engine:weapon_slash_uncross_swipe", duration = 20, hitTick = 10, damage = 2.25, range = 2.8, arc = 110.0, cooldown = 22, recovery = 30, weight = 3),
            melee("dual_throw_feint", "spell_engine:weapon_dual_throw", duration = 26, hitTick = 12, damage = 2.0, range = 3.2, arc = 70.0, cooldown = 50, recovery = 28, weight = 2),
            roll("dodge", "spell_engine:dodge", duration = 12, cooldown = 44, distance = 3.2, direction = "side", weight = 3),
        ),
    )

    private fun defaultArcher(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "archer",
        displayName = "Archer",
        health = 72.0,
        damage = 3.0,
        attackStartDistance = 12.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 8,
        approachAnimationId = "bettercombat:pose_two_handed_bow",
        strafeAnimationId = "bettercombat:pose_two_handed_bow",
        guardAnimationId = "bettercombat:pose_two_handed_bow",
        parryAnimationId = "spell_engine:archery_release",
        recoveryAnimationId = "bettercombat:pose_two_handed_bow",
        recoveryHitsAllowed = 4,
        guardDodgeDirection = "back",
        phases = mutableListOf(
            phase(
                id = "phase_1",
                displayName = "Phase 1",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.0,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 8,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.5,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "phase_2",
                displayName = "Phase 2",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.2,
                speedMultiplier = 1.25,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 6,
                transitionFallback = "The forest narrows. Run cleaner.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, more aggressive archer second phase. Reply as Huntress Wizard with one short in-character battle line. Sound dry, wild, and focused.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.55,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            projectile("aimed_shot", duration = 26, hitTick = 18, damage = 2.0, cooldown = 24, recovery = 22, weight = 6, min = 5.0, max = 13.0, speed = 2.35, inaccuracy = 0.35),
            projectile("quick_shot", duration = 18, hitTick = 11, damage = 1.4, cooldown = 18, recovery = 16, weight = 5, min = 3.0, max = 10.0, speed = 2.15, inaccuracy = 0.75),
            projectile("power_shot", duration = 34, hitTick = 25, damage = 3.0, cooldown = 52, recovery = 30, weight = 2, min = 6.0, max = 14.0, speed = 2.75, inaccuracy = 0.2, knockback = 1.0),
            projectile("volley", duration = 36, hitTick = 22, damage = 1.1, cooldown = 66, recovery = 26, weight = 4, min = 5.0, max = 13.0, speed = 2.2, inaccuracy = 1.0, count = 3, spreadDegrees = 9.0, minPhaseIndex = 1),
            roll("backstep", "combat_roll:roll", duration = 14, cooldown = 36, distance = 3.4, direction = "back", weight = 3),
            roll("side_roll", "spell_engine:dodge", duration = 12, cooldown = 34, distance = 2.7, direction = "side", weight = 2),
        ),
    )

    private fun defaultWizard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "wizard",
        displayName = "Wizard",
        health = 80.0,
        damage = 3.0,
        attackStartDistance = 10.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 8,
        approachAnimationId = "",
        approachAnimationSource = NpcBossAnimationSources.NATURAL,
        strafeAnimationId = "",
        strafeAnimationSource = NpcBossAnimationSources.NATURAL,
        guardAnimationId = "",
        guardAnimationSource = NpcBossAnimationSources.NATURAL,
        parryAnimationId = "spell_engine:one_handed_projectile_release",
        recoveryAnimationId = "",
        recoveryAnimationSource = NpcBossAnimationSources.NATURAL,
        recoveryHitsAllowed = 4,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeDistance = 3.0,
        guardDodgeDirection = "back",
        phases = mutableListOf(
            phase(
                id = "phase_1",
                displayName = "Phase 1",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.0,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 8,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.5,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "phase_2",
                displayName = "Phase 2",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.18,
                speedMultiplier = 1.18,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 6,
                transitionFallback = "Now you must choose courage, not haste.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, more aggressive starter wizard second phase. Reply as Gandalf with one short in-character battle line. Sound wise, stern, and encouraging, not hostile.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.55,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            magicProjectile("arcane_blast", spellId = "wizards:arcane_blast", duration = 24, hitTick = 15, damage = 2.4, cooldown = 24, recovery = 18, weight = 6, min = 4.0, max = 11.0, speed = 0.58, impactRadius = 0.55, particle = "minecraft:end_rod", impactParticle = "minecraft:poof"),
            magicProjectile("fire_blast", spellId = "wizards:fire_blast", duration = 30, hitTick = 21, damage = 3.4, cooldown = 46, recovery = 24, weight = 3, min = 5.0, max = 11.0, speed = 0.44, impactRadius = 1.35, particle = "minecraft:flame", impactParticle = "minecraft:flame"),
            magicProjectile("frostbolt", spellId = "wizards:frostbolt", duration = 26, hitTick = 17, damage = 1.8, cooldown = 34, recovery = 20, weight = 4, min = 4.0, max = 10.5, speed = 0.5, impactRadius = 0.7, particle = "minecraft:snowflake", impactParticle = "minecraft:poof", statusEffectId = "minecraft:slowness", statusEffectTicks = 45, statusEffectAmplifier = 0),
            area("frost_nova", "spell_engine:one_handed_area_release", duration = 24, hitTick = 14, damage = 2.2, radius = 3.0, cooldown = 56, recovery = 20, weight = 3, min = 0.0, max = 3.4, spellId = "wizards:frost_nova"),
            roll("blink_dodge", "spell_engine:dodge", duration = 12, cooldown = 36, distance = 3.6, direction = "back", weight = 4),
        ),
    )

    private fun defaultWitcher(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "witcher",
        displayName = "Witcher",
        health = 85.0,
        damage = 4.0,
        moves = mutableListOf(
            melee("fast_attack_1", "witcher_rpg:fast_attack_witcher_1", duration = 17, hitTick = 7, damage = 5.0, range = 2.8, arc = 95.0, cooldown = 14, recovery = 10, weight = 5),
            melee("fast_attack_2", "witcher_rpg:fast_attack_witcher_2", duration = 17, hitTick = 7, damage = 5.0, range = 2.8, arc = 95.0, cooldown = 14, recovery = 10, weight = 4),
            melee("strong_attack", "witcher_rpg:strong_attack_witcher_1", duration = 27, hitTick = 14, damage = 8.0, range = 3.0, arc = 85.0, cooldown = 46, recovery = 18, weight = 2),
            area("normal_spin", "witcher_rpg:witcher_normal_spin", duration = 25, hitTick = 12, damage = 6.0, radius = 2.7, cooldown = 44, recovery = 14, weight = 3),
            area("whirl", "witcher_rpg:witcher_whirl", duration = 34, hitTick = 12, damage = 7.0, radius = 3.0, cooldown = 70, recovery = 18, weight = 2),
            roll("reflexes", "witcher_rpg:witcher_reflexes", duration = 14, cooldown = 52, distance = 2.8, direction = "side", weight = 2),
        ),
    )

    private fun melee(id: String, animationId: String, duration: Int, hitTick: Int, damage: Double, range: Double, arc: Double, cooldown: Int, recovery: Int, weight: Int): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.MELEE, animationId = animationId, durationTicks = duration, hitTicks = mutableListOf(hitTick), damage = damage, range = range, arcDegrees = arc, cooldownTicks = cooldown, recoveryTicks = recovery, weight = weight, maxDistance = range + 0.4)

    private fun area(id: String, animationId: String, duration: Int, hitTick: Int, damage: Double, radius: Double, cooldown: Int, recovery: Int, weight: Int, min: Double = 0.0, max: Double = radius + 0.8, spellId: String = ""): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.AREA, animationId = animationId, durationTicks = duration, hitTicks = mutableListOf(hitTick), damage = damage, range = radius, arcDegrees = 360.0, areaRadius = radius, cooldownTicks = cooldown, recoveryTicks = recovery, weight = weight, minDistance = min, maxDistance = max, spellId = spellId)

    private fun projectile(
        id: String,
        duration: Int,
        hitTick: Int,
        damage: Double,
        cooldown: Int,
        recovery: Int,
        weight: Int,
        min: Double,
        max: Double,
        speed: Double,
        inaccuracy: Double,
        count: Int = 1,
        spreadDegrees: Double = 0.0,
        knockback: Double = 0.35,
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition = NpcBossMoveDefinition(
        id = id,
        kind = NpcBossMoveKinds.PROJECTILE,
        animationId = "spell_engine:archery_pull",
        releaseAnimationId = "spell_engine:archery_release",
        durationTicks = duration,
        hitTicks = mutableListOf(hitTick),
        damage = damage,
        range = max,
        arcDegrees = 80.0,
        cooldownTicks = cooldown,
        recoveryTicks = recovery,
        weight = weight,
        minDistance = min,
        maxDistance = max,
        projectileSpeed = speed,
        projectileInaccuracy = inaccuracy,
        projectileCount = count,
        projectileSpreadDegrees = spreadDegrees,
        knockback = knockback,
        minPhaseIndex = minPhaseIndex,
        maxPhaseIndex = maxPhaseIndex,
    )

    private fun magicProjectile(
        id: String,
        spellId: String,
        duration: Int,
        hitTick: Int,
        damage: Double,
        cooldown: Int,
        recovery: Int,
        weight: Int,
        min: Double,
        max: Double,
        speed: Double,
        impactRadius: Double,
        particle: String,
        impactParticle: String,
        statusEffectId: String = "",
        statusEffectTicks: Int = 0,
        statusEffectAmplifier: Int = 0,
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition = NpcBossMoveDefinition(
        id = id,
        kind = NpcBossMoveKinds.PROJECTILE,
        animationId = "spell_engine:one_handed_projectile_charge",
        releaseAnimationId = "spell_engine:one_handed_projectile_release",
        spellId = spellId,
        durationTicks = duration,
        hitTicks = mutableListOf(hitTick),
        damage = damage,
        range = max,
        arcDegrees = 80.0,
        cooldownTicks = cooldown,
        recoveryTicks = recovery,
        weight = weight,
        minDistance = min,
        maxDistance = max,
        projectileType = "magic",
        projectileSpeed = speed,
        projectileInaccuracy = 0.0,
        projectileCount = 1,
        projectileSpreadDegrees = 0.0,
        projectileParticle = particle,
        impactParticle = impactParticle,
        impactRadius = impactRadius,
        statusEffectId = statusEffectId,
        statusEffectTicks = statusEffectTicks,
        statusEffectAmplifier = statusEffectAmplifier,
        minPhaseIndex = minPhaseIndex,
        maxPhaseIndex = maxPhaseIndex,
    )

    private fun roll(id: String, animationId: String, duration: Int, cooldown: Int, distance: Double, direction: String, weight: Int): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.ROLL, animationId = animationId, durationTicks = duration, hitTicks = mutableListOf(), damage = 0.0, cooldownTicks = cooldown, recoveryTicks = 0, weight = weight, minDistance = 0.0, maxDistance = 3.5, rollDistance = distance, rollDirection = direction, iframeStartTick = 0, iframeEndTick = duration.coerceAtLeast(1))

    private fun phase(
        id: String,
        displayName: String,
        startsAtHealthRatio: Double,
        damageMultiplier: Double,
        speedMultiplier: Double,
        offenseChainMin: Int,
        offenseChainRandom: Int,
        offenseChainRecoveryTicks: Int,
        transitionFallback: String = "",
        transitionLlmPrompt: String = "",
        musicId: String = "",
        musicVolume: Double = 0.65,
        musicPitch: Double = 1.0,
        musicRepeatTicks: Int = 0,
    ): NpcBossPhaseDefinition = NpcBossPhaseDefinition(
        id = id,
        displayName = displayName,
        startsAtHealthRatio = startsAtHealthRatio,
        damageMultiplier = damageMultiplier,
        speedMultiplier = speedMultiplier,
        offenseChainMin = offenseChainMin,
        offenseChainRandom = offenseChainRandom,
        offenseChainRecoveryTicks = offenseChainRecoveryTicks,
        transitionFallback = transitionFallback,
        transitionLlmPrompt = transitionLlmPrompt,
        musicId = musicId,
        musicVolume = musicVolume,
        musicPitch = musicPitch,
        musicRepeatTicks = musicRepeatTicks,
    )
}

object NpcBossMoveKinds {
    const val MELEE = "melee"
    const val AREA = "area"
    const val PROJECTILE = "projectile"
    const val ROLL = "roll"
}

class NpcBossMovesetDefinition(
    var id: String = "warrior",
    @SerializedName("display_name") var displayName: String = "Warrior",
    var health: Double = 80.0,
    var damage: Double = 4.0,
    @SerializedName("attack_start_distance") var attackStartDistance: Double = 3.2,
    @SerializedName("offense_chain_min") var offenseChainMin: Int = 1,
    @SerializedName("offense_chain_random") var offenseChainRandom: Int = 0,
    @SerializedName("offense_chain_recovery_ticks") var offenseChainRecoveryTicks: Int = 10,
    @SerializedName("approach_animation_id") var approachAnimationId: String = DEFAULT_READY_ANIMATION,
    @SerializedName("approach_animation_source") var approachAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("strafe_animation_id") var strafeAnimationId: String = DEFAULT_READY_ANIMATION,
    @SerializedName("strafe_animation_source") var strafeAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("guard_animation_id") var guardAnimationId: String = DEFAULT_READY_ANIMATION,
    @SerializedName("guard_animation_source") var guardAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("parry_animation_id") var parryAnimationId: String = DEFAULT_COUNTER_ANIMATION,
    @SerializedName("parry_animation_source") var parryAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("hurt_animation_id") var hurtAnimationId: String = DEFAULT_HURT_ANIMATION,
    @SerializedName("hurt_animation_source") var hurtAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("recovery_animation_id") var recoveryAnimationId: String = DEFAULT_READY_ANIMATION,
    @SerializedName("recovery_animation_source") var recoveryAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("guard_react_ticks") var guardReactTicks: Int = 6,
    @SerializedName("guard_counter_ticks") var guardCounterTicks: Int = 12,
    @SerializedName("guard_roll_animation_id") var guardRollAnimationId: String = DEFAULT_ROLL_ANIMATION,
    @SerializedName("guard_roll_animation_source") var guardRollAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("guard_roll_ticks") var guardRollTicks: Int = 14,
    @SerializedName("guard_roll_iframe_ticks") var guardRollIframeTicks: Int = 14,
    @SerializedName("guard_roll_distance") var guardRollDistance: Double = 3.0,
    @SerializedName("guard_dodge_animation_id") var guardDodgeAnimationId: String = DEFAULT_DODGE_ANIMATION,
    @SerializedName("guard_dodge_animation_source") var guardDodgeAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("guard_dodge_ticks") var guardDodgeTicks: Int = 12,
    @SerializedName("guard_dodge_iframe_ticks") var guardDodgeIframeTicks: Int = 10,
    @SerializedName("guard_dodge_distance") var guardDodgeDistance: Double = 2.4,
    @SerializedName("guard_dodge_direction") var guardDodgeDirection: String = "back",
    @SerializedName("guard_min_ticks") var guardMinTicks: Int = 60,
    @SerializedName("guard_random_ticks") var guardRandomTicks: Int = 60,
    @SerializedName("guard_taunt_min_ticks") var guardTauntMinTicks: Int = 40,
    @SerializedName("guard_taunt_random_ticks") var guardTauntRandomTicks: Int = 40,
    @SerializedName("recovery_hits_allowed") var recoveryHitsAllowed: Int = 1,
    @SerializedName("parry_damage") var parryDamage: Double = 0.0,
    @SerializedName("parry_knockback") var parryKnockback: Double = 0.6,
    var phases: MutableList<NpcBossPhaseDefinition> = mutableListOf(),
    var moves: MutableList<NpcBossMoveDefinition> = mutableListOf(),
    var balloons: NpcBossBalloonDefinition = NpcBossBalloonDefinition(),
) {
    fun normalized(): NpcBossMovesetDefinition = apply {
        id = NpcBossMovesets.normalizeId(id).ifBlank { "warrior" }
        displayName = displayName.trim().ifBlank { id.replace('_', ' ').replaceFirstChar(Char::titlecase) }
        health = health.coerceIn(1.0, 10000.0)
        damage = damage.coerceIn(0.0, 1000.0)
        attackStartDistance = attackStartDistance.coerceIn(1.0, 16.0)
        offenseChainMin = offenseChainMin.coerceIn(1, 6)
        offenseChainRandom = offenseChainRandom.coerceIn(0, 6)
        offenseChainRecoveryTicks = offenseChainRecoveryTicks.coerceIn(1, 80)
        approachAnimationSource = cleanBossAnimationSource(approachAnimationSource)
        approachAnimationId = if (approachAnimationSource == NpcBossAnimationSources.NATURAL) "" else cleanBossPlayerlikeAnimation(approachAnimationId, DEFAULT_READY_ANIMATION)
        strafeAnimationSource = cleanBossAnimationSource(strafeAnimationSource)
        strafeAnimationId = if (strafeAnimationSource == NpcBossAnimationSources.NATURAL) "" else cleanBossPlayerlikeAnimation(strafeAnimationId, approachAnimationId.ifBlank { DEFAULT_READY_ANIMATION })
        guardAnimationSource = cleanBossAnimationSource(guardAnimationSource)
        guardAnimationId = if (guardAnimationSource == NpcBossAnimationSources.NATURAL) "" else cleanBossPlayerlikeAnimation(guardAnimationId, DEFAULT_READY_ANIMATION)
        parryAnimationId = cleanBossPlayerlikeAnimation(parryAnimationId, DEFAULT_COUNTER_ANIMATION)
        parryAnimationSource = NpcBossAnimationSources.PLAYERLIKE
        hurtAnimationId = cleanBossPlayerlikeAnimation(hurtAnimationId, DEFAULT_HURT_ANIMATION)
        hurtAnimationSource = NpcBossAnimationSources.PLAYERLIKE
        recoveryAnimationSource = cleanBossAnimationSource(recoveryAnimationSource)
        recoveryAnimationId = if (recoveryAnimationSource == NpcBossAnimationSources.NATURAL) "" else cleanBossPlayerlikeAnimation(recoveryAnimationId, DEFAULT_READY_ANIMATION)
        guardReactTicks = guardReactTicks.coerceIn(1, 40)
        guardCounterTicks = guardCounterTicks.coerceIn(1, 40)
        guardRollAnimationId = cleanBossPlayerlikeAnimation(guardRollAnimationId, DEFAULT_ROLL_ANIMATION)
        guardRollAnimationSource = NpcBossAnimationSources.PLAYERLIKE
        guardRollTicks = guardRollTicks.coerceIn(1, 40)
        guardRollIframeTicks = guardRollIframeTicks.coerceIn(0, guardRollTicks)
        guardRollDistance = guardRollDistance.coerceIn(0.0, 8.0)
        guardDodgeAnimationId = cleanBossPlayerlikeAnimation(guardDodgeAnimationId, DEFAULT_DODGE_ANIMATION)
        guardDodgeAnimationSource = NpcBossAnimationSources.PLAYERLIKE
        guardDodgeTicks = guardDodgeTicks.coerceIn(1, 40)
        guardDodgeIframeTicks = guardDodgeIframeTicks.coerceIn(0, guardDodgeTicks)
        guardDodgeDistance = guardDodgeDistance.coerceIn(0.0, 8.0)
        guardDodgeDirection = cleanEvadeDirection(guardDodgeDirection)
        guardMinTicks = guardMinTicks.coerceIn(10, 20 * 30)
        guardRandomTicks = guardRandomTicks.coerceIn(0, 20 * 30)
        guardTauntMinTicks = guardTauntMinTicks.coerceIn(10, 20 * 30)
        guardTauntRandomTicks = guardTauntRandomTicks.coerceIn(0, 20 * 30)
        recoveryHitsAllowed = recoveryHitsAllowed.coerceIn(0, 10)
        parryDamage = parryDamage.coerceIn(0.0, 1000.0)
        parryKnockback = parryKnockback.coerceIn(0.0, 4.0)
        phases = phases
            .map { phase -> phase.normalized(this) }
            .filter { phase -> phase.id.isNotBlank() }
            .sortedByDescending { phase -> phase.startsAtHealthRatio }
            .toMutableList()
        if (phases.isEmpty()) phases = mutableListOf(NpcBossPhaseDefinition.fromLegacy(this))
        moves = moves.map { move -> move.normalized(damage) }.filter { move -> move.id.isNotBlank() }.toMutableList()
        if (moves.isEmpty()) moves = mutableListOf(NpcBossMoveDefinition().normalized(damage))
        balloons = balloons.normalized()
    }

    private fun cleanAnimation(value: String, fallback: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9_.:/-]+"), "_")
        .trim('_')
        .ifBlank { fallback }

    private fun cleanBossPlayerlikeAnimation(value: String, fallback: String): String = when (val clean = cleanAnimation(value, fallback)) {
        "running",
        "running_sword",
        "run_with_sword",
        "guard" -> DEFAULT_READY_ANIMATION
        "attack",
        "attack_sword",
        "parry" -> DEFAULT_COUNTER_ANIMATION
        "hurt" -> DEFAULT_HURT_ANIMATION
        "roll" -> DEFAULT_ROLL_ANIMATION
        "dodge" -> DEFAULT_DODGE_ANIMATION
        else -> clean
    }

    private fun cleanBossAnimationSource(value: String): String = when (value.trim().lowercase()) {
        NpcBossAnimationSources.NATURAL -> NpcBossAnimationSources.NATURAL
        else -> NpcBossAnimationSources.PLAYERLIKE
    }

    private fun cleanEvadeDirection(value: String): String = when (value.trim().lowercase()) {
        "side", "left", "right", "random_side" -> "side"
        "forward" -> "forward"
        else -> "back"
    }

    companion object {
        const val DEFAULT_READY_ANIMATION = "bettercombat:pose_two_handed_sword"
        const val DEFAULT_COUNTER_ANIMATION = "bettercombat:one_handed_slash_horizontal_right"
        const val DEFAULT_HURT_ANIMATION = "spell_engine:dodge"
        const val DEFAULT_RECOVERY_ANIMATION = "bettercombat:pose_one_handed_backwards"
        const val DEFAULT_ROLL_ANIMATION = "combat_roll:roll"
        const val DEFAULT_DODGE_ANIMATION = "spell_engine:dodge"
    }
}

class NpcBossPhaseDefinition(
    var id: String = "phase_1",
    @SerializedName("display_name") var displayName: String = "Phase 1",
    @SerializedName("starts_at_health_ratio") var startsAtHealthRatio: Double = 1.0,
    @SerializedName("damage_multiplier") var damageMultiplier: Double = 1.0,
    @SerializedName("speed_multiplier") var speedMultiplier: Double = 1.0,
    @SerializedName("offense_chain_min") var offenseChainMin: Int = 1,
    @SerializedName("offense_chain_random") var offenseChainRandom: Int = 0,
    @SerializedName("offense_chain_recovery_ticks") var offenseChainRecoveryTicks: Int = 10,
    @SerializedName("transition_fallback") var transitionFallback: String = "",
    @SerializedName("transition_llm_prompt") var transitionLlmPrompt: String = "",
    @SerializedName("music_id") var musicId: String = "",
    @SerializedName("music_volume") var musicVolume: Double = 0.65,
    @SerializedName("music_pitch") var musicPitch: Double = 1.0,
    @SerializedName("music_repeat_ticks") var musicRepeatTicks: Int = 0,
) {
    fun normalized(owner: NpcBossMovesetDefinition): NpcBossPhaseDefinition = apply {
        id = NpcBossMovesets.normalizeId(id)
        displayName = displayName.trim().ifBlank { id.replace('_', ' ').replaceFirstChar(Char::titlecase) }
        startsAtHealthRatio = startsAtHealthRatio.coerceIn(0.0, 1.0)
        damageMultiplier = damageMultiplier.coerceIn(0.1, 10.0)
        speedMultiplier = speedMultiplier.coerceIn(0.25, 4.0)
        offenseChainMin = offenseChainMin.coerceIn(1, 8)
        offenseChainRandom = offenseChainRandom.coerceIn(0, 8)
        offenseChainRecoveryTicks = offenseChainRecoveryTicks.coerceIn(1, 80)
        transitionFallback = transitionFallback.trim().take(MAX_TRANSITION_TEXT_LENGTH)
        transitionLlmPrompt = transitionLlmPrompt.trim().take(MAX_TRANSITION_PROMPT_LENGTH)
        musicId = musicId.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
        musicVolume = musicVolume.coerceIn(0.0, 1.0)
        musicPitch = musicPitch.coerceIn(0.25, 4.0)
        musicRepeatTicks = musicRepeatTicks.coerceIn(0, 20 * 60 * 10)
        if (transitionFallback.isBlank() && startsAtHealthRatio < 1.0) {
            transitionFallback = "${owner.displayName} changes stance."
        }
    }

    companion object {
        private const val MAX_TRANSITION_TEXT_LENGTH = 220
        private const val MAX_TRANSITION_PROMPT_LENGTH = 600

        fun fromLegacy(owner: NpcBossMovesetDefinition): NpcBossPhaseDefinition = NpcBossPhaseDefinition(
            id = "phase_1",
            displayName = "Phase 1",
            startsAtHealthRatio = 1.0,
            damageMultiplier = 1.0,
            speedMultiplier = 1.0,
            offenseChainMin = owner.offenseChainMin,
            offenseChainRandom = owner.offenseChainRandom,
            offenseChainRecoveryTicks = owner.offenseChainRecoveryTicks,
        ).normalized(owner)
    }
}

class NpcBossMoveDefinition(
    var id: String = "slash",
    var kind: String = NpcBossMoveKinds.MELEE,
    @SerializedName("animation_id") var animationId: String = "bettercombat:one_handed_slash_horizontal_right",
    @SerializedName("animation_source") var animationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("release_animation_id") var releaseAnimationId: String = "",
    @SerializedName("release_animation_source") var releaseAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("spell_id") var spellId: String = "",
    @SerializedName("duration_ticks") var durationTicks: Int = 18,
    @SerializedName("hit_ticks") var hitTicks: MutableList<Int> = mutableListOf(7),
    @SerializedName("cooldown_ticks") var cooldownTicks: Int = 18,
    @SerializedName("recovery_ticks") var recoveryTicks: Int = 14,
    var damage: Double = 0.0,
    var range: Double = 2.8,
    @SerializedName("arc_degrees") var arcDegrees: Double = 95.0,
    @SerializedName("area_radius") var areaRadius: Double = 0.0,
    var knockback: Double = 0.35,
    var weight: Int = 1,
    @SerializedName("min_distance") var minDistance: Double = 0.0,
    @SerializedName("max_distance") var maxDistance: Double = 3.2,
    @SerializedName("roll_distance") var rollDistance: Double = 0.0,
    @SerializedName("roll_direction") var rollDirection: String = "back",
    @SerializedName("iframe_start_tick") var iframeStartTick: Int = 0,
    @SerializedName("iframe_end_tick") var iframeEndTick: Int = 0,
    @SerializedName("projectile_type") var projectileType: String = "arrow",
    @SerializedName("projectile_speed") var projectileSpeed: Double = 2.2,
    @SerializedName("projectile_inaccuracy") var projectileInaccuracy: Double = 0.5,
    @SerializedName("projectile_count") var projectileCount: Int = 1,
    @SerializedName("projectile_spread_degrees") var projectileSpreadDegrees: Double = 0.0,
    @SerializedName("projectile_particle") var projectileParticle: String = "",
    @SerializedName("impact_particle") var impactParticle: String = "",
    @SerializedName("impact_radius") var impactRadius: Double = 0.4,
    @SerializedName("status_effect_id") var statusEffectId: String = "",
    @SerializedName("status_effect_ticks") var statusEffectTicks: Int = 0,
    @SerializedName("status_effect_amplifier") var statusEffectAmplifier: Int = 0,
    @SerializedName("min_phase_index") var minPhaseIndex: Int = 0,
    @SerializedName("max_phase_index") var maxPhaseIndex: Int = 99,
) {
    fun normalized(baseDamage: Double): NpcBossMoveDefinition = apply {
        id = NpcBossMovesets.normalizeId(id)
        kind = when (kind.trim().lowercase()) {
            NpcBossMoveKinds.AREA, "spell" -> NpcBossMoveKinds.AREA
            NpcBossMoveKinds.PROJECTILE, "ranged", "arrow", "bow" -> NpcBossMoveKinds.PROJECTILE
            NpcBossMoveKinds.ROLL, "dodge", "evade" -> NpcBossMoveKinds.ROLL
            else -> NpcBossMoveKinds.MELEE
        }
        animationId = animationId.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
            .ifBlank { NpcBossMovesetDefinition.DEFAULT_COUNTER_ANIMATION }
        animationId = when (animationId) {
            "attack",
            "attack_sword",
            "parry" -> NpcBossMovesetDefinition.DEFAULT_COUNTER_ANIMATION
            "guard",
            "running",
            "running_sword",
            "run_with_sword" -> NpcBossMovesetDefinition.DEFAULT_READY_ANIMATION
            "hurt",
            "dodge" -> NpcBossMovesetDefinition.DEFAULT_HURT_ANIMATION
            "roll" -> NpcBossMovesetDefinition.DEFAULT_ROLL_ANIMATION
            else -> animationId
        }
        if (kind == NpcBossMoveKinds.PROJECTILE && animationId == NpcBossMovesetDefinition.DEFAULT_COUNTER_ANIMATION) {
            animationId = "spell_engine:archery_pull"
        }
        animationSource = NpcBossAnimationSources.PLAYERLIKE
        releaseAnimationId = releaseAnimationId.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
            .ifBlank { if (kind == NpcBossMoveKinds.PROJECTILE) "spell_engine:archery_release" else animationId }
        releaseAnimationSource = NpcBossAnimationSources.PLAYERLIKE
        spellId = spellId.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
        durationTicks = durationTicks.coerceIn(1, 20 * 10)
        hitTicks = hitTicks.map { tick -> tick.coerceIn(0, durationTicks) }.distinct().sorted().toMutableList()
        if (kind != NpcBossMoveKinds.ROLL && hitTicks.isEmpty()) hitTicks = mutableListOf((durationTicks / 2).coerceAtLeast(1))
        if (kind == NpcBossMoveKinds.ROLL) hitTicks = mutableListOf()
        cooldownTicks = cooldownTicks.coerceIn(0, 20 * 60)
        recoveryTicks = recoveryTicks.coerceIn(0, 20 * 10)
        damage = if (kind == NpcBossMoveKinds.ROLL) 0.0 else damage.takeIf { it > 0.0 }?.coerceIn(0.0, 1000.0) ?: baseDamage
        range = range.coerceIn(0.5, 16.0)
        arcDegrees = arcDegrees.coerceIn(1.0, 360.0)
        areaRadius = if (kind == NpcBossMoveKinds.AREA) areaRadius.takeIf { it > 0.0 }?.coerceIn(0.5, 16.0) ?: range else 0.0
        knockback = knockback.coerceIn(0.0, 4.0)
        weight = weight.coerceIn(0, 100)
        minDistance = minDistance.coerceIn(0.0, 16.0)
        maxDistance = maxDistance.coerceIn(minDistance, 16.0)
        rollDistance = rollDistance.coerceIn(0.0, 8.0)
        rollDirection = when (rollDirection.trim().lowercase()) {
            "side", "left", "right", "random_side" -> "side"
            "forward" -> "forward"
            else -> "back"
        }
        iframeStartTick = iframeStartTick.coerceIn(0, durationTicks)
        iframeEndTick = iframeEndTick.coerceIn(iframeStartTick, durationTicks)
        projectileType = when (projectileType.trim().lowercase()) {
            "magic", "spell" -> "magic"
            else -> "arrow"
        }
        projectileSpeed = projectileSpeed.coerceIn(0.1, 8.0)
        projectileInaccuracy = projectileInaccuracy.coerceIn(0.0, 20.0)
        projectileCount = projectileCount.coerceIn(1, 8)
        projectileSpreadDegrees = projectileSpreadDegrees.coerceIn(0.0, 60.0)
        projectileParticle = cleanParticleId(projectileParticle, if (projectileType == "magic") "minecraft:end_rod" else "")
        impactParticle = cleanParticleId(impactParticle, if (projectileType == "magic") "minecraft:poof" else "")
        impactRadius = impactRadius.coerceIn(0.1, 5.0)
        statusEffectId = cleanParticleId(statusEffectId, "")
        statusEffectTicks = statusEffectTicks.coerceIn(0, 20 * 20)
        statusEffectAmplifier = statusEffectAmplifier.coerceIn(0, 10)
        minPhaseIndex = minPhaseIndex.coerceIn(0, 99)
        maxPhaseIndex = maxPhaseIndex.coerceIn(minPhaseIndex, 99)
    }

    private fun cleanParticleId(value: String, fallback: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9_.:/-]+"), "_")
        .trim('_')
        .ifBlank { fallback }
}
