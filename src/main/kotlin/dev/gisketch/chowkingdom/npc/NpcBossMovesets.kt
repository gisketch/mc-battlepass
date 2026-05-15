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
        val templateId = normalizeId(definition.boss.template)
        if (templateId.isNotBlank() && templateId != NpcBossDefinition.DEFAULT_BOSS_TEMPLATE) {
            get(templateId)?.let { return it }
        }
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
        defaultBountyHunter(),
        defaultWizard(),
        defaultWaterWizard(),
        defaultFireWizard(),
        defaultWindWizard(),
        defaultArcaneWizard(),
        defaultEarthWizard(),
        defaultPriest(),
        defaultBard(),
        defaultForcemaster(),
        defaultBerserker(),
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

    private fun defaultBard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "bard",
        displayName = "Bard",
        health = 72.0,
        damage = 3.0,
        attackStartDistance = 12.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 8,
        approachAnimationId = "bards_rpg:harp_channel",
        strafeAnimationId = "bards_rpg:harp_channel",
        guardAnimationId = "bards_rpg:harp_channel",
        parryAnimationId = "bards_rpg:harp_release",
        recoveryAnimationId = "bards_rpg:harp_channel",
        recoveryHitsAllowed = 4,
        guardDodgeDirection = "back",
        phases = mutableListOf(
            phase(
                id = "phase_1",
                displayName = "Opening Verse",
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
                displayName = "Encore",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.2,
                speedMultiplier = 1.25,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 6,
                transitionFallback = "Fine, fine. I will play the loud part.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster harp-crossbow encore phase. Reply as Venti with one short playful battle line about wind, music, or freedom. Sound teasing but focused.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.55,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            projectile("starshot", duration = 26, hitTick = 18, damage = 2.0, cooldown = 24, recovery = 22, weight = 6, min = 5.0, max = 13.0, speed = 2.35, inaccuracy = 0.35, spellId = "bards_rpg:starshots", animationId = "bards_rpg:harp_channel", releaseAnimationId = "bards_rpg:harp_release", projectileParticle = "more_rpg_classes:star", impactParticle = "more_rpg_classes:star", impactRadius = 0.55, castParticle = "more_rpg_classes:music_note", releaseParticle = "spell_engine:magic_spark_decelerate", castSoundId = "bards_rpg:troubadours_minuet", releaseSoundId = "bards_rpg:harp_crossbow_shoot", impactSoundId = "bards_rpg:bard_impact"),
            projectile("mocking_shot", duration = 18, hitTick = 11, damage = 1.4, cooldown = 18, recovery = 16, weight = 5, min = 3.0, max = 10.0, speed = 2.15, inaccuracy = 0.75, spellId = "bards_rpg:vicious_mockery", animationId = "bards_rpg:harp_channel", releaseAnimationId = "bards_rpg:harp_release", projectileParticle = "more_rpg_classes:music_note", impactParticle = "more_rpg_classes:rage_particle", impactRadius = 0.55, castParticle = "more_rpg_classes:music_note", releaseParticle = "more_rpg_classes:music_note", releaseSoundId = "bards_rpg:harp_crossbow_shoot", impactSoundId = "bards_rpg:bard_impact"),
            projectile("ballad_shot", duration = 34, hitTick = 25, damage = 3.0, cooldown = 52, recovery = 30, weight = 2, min = 6.0, max = 14.0, speed = 2.75, inaccuracy = 0.2, knockback = 1.0, spellId = "bards_rpg:magical_ballad", animationId = "bards_rpg:harp_channel", releaseAnimationId = "bards_rpg:harp_release", projectileParticle = "more_rpg_classes:music_note", impactParticle = "spell_engine:magic_spark_burst", impactRadius = 0.75, castParticle = "more_rpg_classes:music_note", releaseParticle = "more_rpg_classes:music_note", castSoundId = "bards_rpg:magical_ballad", releaseSoundId = "bards_rpg:harp_crossbow_shoot", impactSoundId = "bards_rpg:bard_impact"),
            projectile("crescendo_volley", duration = 36, hitTick = 22, damage = 1.1, cooldown = 66, recovery = 26, weight = 4, min = 5.0, max = 13.0, speed = 2.2, inaccuracy = 1.0, count = 3, spreadDegrees = 9.0, spellId = "bards_rpg:crescendo", animationId = "bards_rpg:harp_channel", releaseAnimationId = "bards_rpg:harp_release", projectileParticle = "more_rpg_classes:music_note", impactParticle = "spell_engine:magic_spark_burst", impactRadius = 0.65, castParticle = "more_rpg_classes:music_note", releaseParticle = "more_rpg_classes:music_note", castSoundId = "bards_rpg:encore_channel", releaseSoundId = "bards_rpg:harp_crossbow_shoot", impactSoundId = "bards_rpg:encore_cooldown_impact", minPhaseIndex = 1),
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
            magicProjectile("arcane_blast", spellId = "wizards:arcane_blast", duration = 24, hitTick = 15, damage = 2.4, cooldown = 24, recovery = 18, weight = 6, min = 4.0, max = 11.0, speed = 0.58, impactRadius = 0.55, particle = "spell_engine:magic_arcane_float", impactParticle = "spell_engine:magic_arcane_burst", releaseSoundId = "spell_engine:generic_arcane_release"),
            magicProjectile("fire_blast", spellId = "wizards:fire_blast", duration = 30, hitTick = 21, damage = 3.4, cooldown = 46, recovery = 24, weight = 3, min = 5.0, max = 11.0, speed = 0.44, impactRadius = 1.35, particle = "spell_engine:flame_spark", impactParticle = "spell_engine:fire_explosion", releaseSoundId = "spell_engine:generic_fire_release"),
            magicProjectile("frostbolt", spellId = "wizards:frostbolt", duration = 26, hitTick = 17, damage = 1.8, cooldown = 34, recovery = 20, weight = 4, min = 4.0, max = 10.5, speed = 0.5, impactRadius = 0.7, particle = "spell_engine:magic_frost_float", impactParticle = "spell_engine:magic_frost_burst", releaseSoundId = "spell_engine:generic_frost_release", statusEffectId = "minecraft:slowness", statusEffectTicks = 45, statusEffectAmplifier = 0),
            area("frost_nova", "spell_engine:one_handed_area_release", duration = 24, hitTick = 14, damage = 2.2, radius = 3.0, cooldown = 56, recovery = 20, weight = 3, min = 0.0, max = 3.4, spellId = "wizards:frost_nova", impactParticle = "spell_engine:magic_frost_burst", releaseParticle = "spell_engine:area_circle_1"),
            roll("blink_dodge", "spell_engine:dodge", duration = 12, cooldown = 36, distance = 3.6, direction = "back", weight = 4, supportParticle = "spell_engine:magic_arcane_decelerate"),
        ),
    )

    private fun defaultBountyHunter(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "bounty_hunter",
        displayName = "Bounty Hunter",
        health = 86.0,
        damage = 3.4,
        attackStartDistance = 14.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 8,
        approachAnimationId = "bettercombat:pose_two_handed_bow",
        strafeAnimationId = "bettercombat:pose_two_handed_bow",
        guardAnimationId = "bettercombat:pose_two_handed_bow",
        parryAnimationId = "spell_engine:archery_release",
        recoveryAnimationId = "bettercombat:pose_two_handed_bow",
        guardRollWeight = 0,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeIframeTicks = 8,
        guardDodgeDistance = 2.2,
        guardDodgeDirection = "back",
        guardDodgeWeight = 1,
        recoveryHitsAllowed = 4,
        phases = mutableListOf(
            phase(
                id = "phase_1",
                displayName = "Marked Quarry",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.0,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 8,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.52,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "phase_2",
                displayName = "Deadeye Hunt",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.15,
                speedMultiplier = 1.18,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 7,
                transitionFallback = "Focus is locked. Every weak point is lit.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster Deadeye archer second phase. Reply as Aloy with one short focused battle line about weak points, tracking, or target control. Sound precise and intense.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.58,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            projectile("fast_shot", duration = 18, hitTick = 11, damage = 1.5, cooldown = 18, recovery = 16, weight = 8, min = 4.0, max = 14.0, speed = 2.45, inaccuracy = 0.65, spellId = "archers_expansion:fast_shot", projectileParticle = "minecraft:crit", impactParticle = "minecraft:enchanted_hit", impactRadius = 0.5, castParticle = "minecraft:crit", releaseParticle = "minecraft:crit", releaseSoundId = "minecraft:entity.arrow.shoot", impactSoundId = "minecraft:entity.arrow.hit"),
            projectile("trick_shot", duration = 22, hitTick = 14, damage = 1.8, cooldown = 26, recovery = 18, weight = 5, min = 4.0, max = 14.0, speed = 2.4, inaccuracy = 1.25, spellId = "archers_expansion:trick_shot", projectileParticle = "minecraft:crit", impactParticle = "minecraft:poof", impactRadius = 0.65, castParticle = "minecraft:enchanted_hit", releaseParticle = "minecraft:crit", releaseSoundId = "minecraft:entity.arrow.shoot", impactSoundId = "minecraft:entity.arrow.hit"),
            projectile("disabling_shot", duration = 24, hitTick = 16, damage = 1.6, cooldown = 36, recovery = 20, weight = 4, min = 5.0, max = 14.0, speed = 2.45, inaccuracy = 0.45, spellId = "archers_expansion:disabling_shot", projectileParticle = "minecraft:smoke", impactParticle = "minecraft:cloud", impactRadius = 0.85, castParticle = "minecraft:crit", releaseParticle = "minecraft:smoke", releaseSoundId = "minecraft:entity.arrow.shoot", impactSoundId = "minecraft:entity.arrow.hit_player", statusEffectId = "minecraft:slowness", statusEffectTicks = 60),
            projectile("choking_gas", duration = 28, hitTick = 18, damage = 1.2, cooldown = 86, recovery = 24, weight = 2, min = 5.0, max = 14.0, speed = 2.25, inaccuracy = 0.75, spellId = "archers_expansion:choking_gas", projectileParticle = "minecraft:smoke", impactParticle = "minecraft:cloud", impactRadius = 0.9, castParticle = "minecraft:smoke", releaseParticle = "minecraft:smoke", releaseSoundId = "minecraft:entity.arrow.shoot", impactSoundId = "minecraft:block.fire.extinguish", statusEffectId = "minecraft:slowness", statusEffectTicks = 40, hazardRadius = 2.6, hazardTicks = 60, hazardIntervalTicks = 14, hazardDamage = 0.45, hazardParticle = "minecraft:cloud"),
            projectile("improved_disabling_shot", duration = 26, hitTick = 17, damage = 1.6, cooldown = 54, recovery = 22, weight = 3, min = 5.0, max = 14.0, speed = 2.45, inaccuracy = 0.4, spellId = "archers_expansion:improved_disabling_shot", projectileParticle = "minecraft:crit", impactParticle = "minecraft:enchanted_hit", impactRadius = 0.9, castParticle = "minecraft:enchanted_hit", releaseParticle = "minecraft:crit", releaseSoundId = "minecraft:entity.arrow.shoot", impactSoundId = "minecraft:entity.arrow.hit_player", statusEffectId = "slowness_weakness", statusEffectTicks = 55, minPhaseIndex = 1),
            projectile("infiltrators_arrow", duration = 34, hitTick = 23, damage = 3.4, cooldown = 72, recovery = 28, weight = 2, min = 7.0, max = 16.0, speed = 2.75, inaccuracy = 0.12, knockback = 0.65, spellId = "archers_expansion:infiltrators_arrow", projectileParticle = "minecraft:enchanted_hit", impactParticle = "minecraft:crit", impactRadius = 0.75, castParticle = "minecraft:enchanted_hit", releaseParticle = "minecraft:crit", releaseSoundId = "minecraft:entity.arrow.shoot", impactSoundId = "minecraft:entity.arrow.hit_player", minPhaseIndex = 1),
            projectile("deadeye_barrage", duration = 36, hitTick = 23, damage = 0.9, cooldown = 90, recovery = 28, weight = 3, min = 5.0, max = 14.0, speed = 2.3, inaccuracy = 1.0, count = 4, spreadDegrees = 12.0, spellId = "archers_expansion:fast_shot", projectileParticle = "minecraft:crit", impactParticle = "minecraft:enchanted_hit", impactRadius = 0.6, castParticle = "minecraft:crit", releaseParticle = "minecraft:enchanted_hit", releaseSoundId = "minecraft:entity.arrow.shoot", impactSoundId = "minecraft:entity.arrow.hit", minPhaseIndex = 1),
            roll("alter_ego", "spell_engine:dodge", duration = 12, cooldown = 70, distance = 2.1, direction = "side", weight = 1, supportParticle = "minecraft:smoke", spellId = "archers_expansion:alter_ego"),
        ),
    )

    private fun defaultWaterWizard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "water_wizard",
        displayName = "Water Wizard",
        health = 90.0,
        damage = 3.0,
        attackStartDistance = 11.0,
        offenseChainMin = 2,
        offenseChainRandom = 1,
        offenseChainRecoveryTicks = 6,
        approachAnimationId = "",
        approachAnimationSource = NpcBossAnimationSources.NATURAL,
        strafeAnimationId = "",
        strafeAnimationSource = NpcBossAnimationSources.NATURAL,
        guardAnimationId = "",
        guardAnimationSource = NpcBossAnimationSources.NATURAL,
        parryAnimationId = "spell_engine:one_handed_area_release",
        recoveryAnimationId = "",
        recoveryAnimationSource = NpcBossAnimationSources.NATURAL,
        recoveryHitsAllowed = 3,
        guardRollWeight = 0,
        guardDodgeWeight = 2,
        guardParryWeight = 2,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeTicks = 10,
        guardDodgeIframeTicks = 9,
        guardDodgeDistance = 2.6,
        guardDodgeDirection = "side",
        hoverHeight = 0.0,
        parrySpellId = "elemental_wizards_rpg:aqua_water_whip",
        parryParticle = "minecraft:splash",
        parrySoundId = "minecraft:entity.generic.splash",
        parryDamage = 0.75,
        parryKnockback = 1.15,
        phases = mutableListOf(
            phase(
                id = "patient_current",
                displayName = "Patient Current",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.08,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 6,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.5,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "moonlit_tide",
                displayName = "Moonlit Tide",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.14,
                speedMultiplier = 1.22,
                offenseChainMin = 3,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 5,
                transitionFallback = "The tide rises. Stay centered.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, stronger Water Wizard second phase. Reply as young Katara with one short firm battle line about the tide, healing, discipline, or moonlit water.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.56,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            magicProjectile("water_whip", spellId = "elemental_wizards_rpg:aqua_water_whip", duration = 20, hitTick = 11, damage = 1.65, cooldown = 18, recovery = 12, weight = 8, min = 2.8, max = 10.5, speed = 0.86, impactRadius = 0.55, particle = "minecraft:splash", impactParticle = "minecraft:splash", animationId = "spell_engine:one_handed_projectile_side_charge", releaseAnimationId = "spell_engine:one_handed_projectile_side_release", castParticle = "minecraft:bubble", releaseParticle = "minecraft:splash", castSoundId = "minecraft:block.bubble_column.bubble_pop", releaseSoundId = "minecraft:item.trident.throw", impactSoundId = "minecraft:entity.generic.splash", knockback = 0.85),
            area("aqua_splash", "spell_engine:one_handed_area_release", duration = 21, hitTick = 11, damage = 1.7, radius = 4.0, cooldown = 24, recovery = 13, weight = 7, min = 0.0, max = 4.6, spellId = "elemental_wizards_rpg:aqua_splash", knockback = 0.8, arc = 130.0, impactParticle = "minecraft:splash", releaseParticle = "minecraft:bubble", castParticle = "minecraft:bubble", releaseSoundId = "minecraft:entity.generic.splash", impactSoundId = "minecraft:entity.player.splash"),
            magicProjectile("waterball", spellId = "elemental_wizards_rpg:aqua_waterball", duration = 28, hitTick = 17, damage = 2.65, cooldown = 42, recovery = 20, weight = 4, min = 4.0, max = 11.5, speed = 0.58, impactRadius = 1.25, particle = "minecraft:bubble", impactParticle = "minecraft:splash", animationId = "spell_engine:one_handed_projectile_charge", releaseAnimationId = "spell_engine:one_handed_projectile_release", castParticle = "minecraft:bubble", releaseParticle = "minecraft:splash", castSoundId = "minecraft:block.bubble_column.bubble_pop", releaseSoundId = "minecraft:item.trident.throw", impactSoundId = "minecraft:entity.generic.splash", knockback = 0.95),
            magicProjectile("ice_bind", spellId = "elemental_wizards_rpg:aqua_water_whip", duration = 26, hitTick = 15, damage = 1.35, cooldown = 44, recovery = 18, weight = 4, min = 3.5, max = 10.5, speed = 0.66, impactRadius = 0.75, particle = "minecraft:snowflake", impactParticle = "minecraft:snowflake", animationId = "spell_engine:one_handed_projectile_side_charge", releaseAnimationId = "spell_engine:one_handed_projectile_side_release", castParticle = "minecraft:splash", releaseParticle = "minecraft:snowflake", releaseSoundId = "minecraft:block.glass.break", impactSoundId = "minecraft:block.glass.hit", statusEffectId = "minecraft:slowness", statusEffectTicks = 55, statusEffectAmplifier = 1, knockback = 0.35),
            beam("hydro_beam", spellId = "elemental_wizards_rpg:aqua_hydro_beam", duration = 40, hitTicks = listOf(18, 23, 28, 33), damage = 0.72, cooldown = 74, recovery = 22, weight = 4, min = 4.0, max = 11.5, impactRadius = 0.8, particle = "minecraft:splash", impactParticle = "minecraft:bubble", castParticle = "minecraft:bubble", releaseParticle = "minecraft:splash", castSoundId = "minecraft:block.bubble_column.bubble_pop", releaseSoundId = "minecraft:item.trident.riptide_1", impactSoundId = "minecraft:entity.generic.splash", minPhaseIndex = 1),
            support("springwater", "more_rpg_classes:two_handed_ground_channeling", duration = 30, hitTick = 18, cooldown = 110, recovery = 18, weight = 2, min = 0.0, max = 10.0, spellId = "elemental_wizards_rpg:improved_aqua_springwater", selfHeal = 4.0, healCapRatio = 0.48, maxHealUses = 1, absorption = 3.0, absorptionTicks = 90, castParticle = "minecraft:bubble", supportParticle = "minecraft:splash", castSoundId = "minecraft:block.bubble_column.bubble_pop", releaseSoundId = "minecraft:entity.generic.splash", impactSoundId = "minecraft:entity.generic.splash", releaseAnimationId = "more_rpg_classes:two_handed_ground_release"),
            area("elemental_avatar", "spell_engine:two_handed_channeling", duration = 36, hitTick = 21, damage = 2.45, radius = 5.4, cooldown = 86, recovery = 22, weight = 3, min = 0.0, max = 6.0, spellId = "elemental_wizards_rpg:elemental_avatar", knockback = 1.45, impactParticle = "minecraft:splash", releaseParticle = "minecraft:bubble", castParticle = "minecraft:bubble", releaseSoundId = "minecraft:item.trident.riptide_2", impactSoundId = "minecraft:entity.generic.splash", releaseAnimationId = "spell_engine:one_handed_area_release", minPhaseIndex = 1),
            dodge("water_step", animationId = "spell_engine:dodge", duration = 10, cooldown = 34, distance = 2.7, direction = "side", weight = 2, supportParticle = "minecraft:splash", releaseSoundId = "minecraft:entity.generic.splash", spellId = "elemental_wizards_rpg:aqua_splash"),
        ),
    )

    private fun defaultFireWizard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "fire_wizard",
        displayName = "Fire Wizard",
        health = 84.0,
        damage = 3.3,
        attackStartDistance = 11.0,
        offenseChainMin = 1,
        offenseChainRandom = 1,
        offenseChainRecoveryTicks = 6,
        approachAnimationId = "",
        approachAnimationSource = NpcBossAnimationSources.NATURAL,
        strafeAnimationId = "",
        strafeAnimationSource = NpcBossAnimationSources.NATURAL,
        guardAnimationId = "",
        guardAnimationSource = NpcBossAnimationSources.NATURAL,
        parryAnimationId = "spell_engine:one_handed_area_release",
        recoveryAnimationId = "",
        recoveryAnimationSource = NpcBossAnimationSources.NATURAL,
        recoveryHitsAllowed = 3,
        guardRollWeight = 2,
        guardDodgeWeight = 1,
        guardParryWeight = 1,
        guardRollAnimationId = "combat_roll:roll",
        guardRollDistance = 3.0,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeDistance = 2.5,
        guardDodgeDirection = "side",
        parrySpellId = "wizards:fire_scorch",
        parryParticle = "spell_engine:fire_explosion",
        parrySoundId = "spell_engine:generic_fire_release",
        parryDamage = 0.9,
        parryKnockback = 1.0,
        phases = mutableListOf(
            phase(
                id = "controlled_flame",
                displayName = "Controlled Flame",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.12,
                offenseChainMin = 1,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 6,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.52,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "dragon_flame",
                displayName = "Dragon Flame",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.16,
                speedMultiplier = 1.28,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 5,
                transitionFallback = "Enough. Breathe first, then strike.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, stronger Fire Wizard second phase. Reply as Zuko with one short serious battle line about breath, control, or true fire.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.58,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            area("fire_jab", "bettercombat:one_handed_punch", duration = 16, hitTick = 7, damage = 1.6, radius = 2.7, cooldown = 18, recovery = 12, weight = 6, min = 0.0, max = 3.0, spellId = "wizards:fire_scorch", knockback = 0.75, arc = 120.0, impactParticle = "spell_engine:flame_medium_a", releaseParticle = "spell_engine:flame_spark", castParticle = "spell_engine:flame_spark", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "spell_engine:generic_fire_impact", fireTicks = 50),
            magicProjectile("fire_blast", spellId = "wizards:fire_blast", duration = 20, hitTick = 11, damage = 2.0, cooldown = 22, recovery = 14, weight = 7, min = 3.0, max = 11.0, speed = 0.72, impactRadius = 0.9, particle = "spell_engine:flame_spark", impactParticle = "spell_engine:fire_explosion", animationId = "spell_engine:one_handed_projectile_side_charge", releaseAnimationId = "spell_engine:one_handed_projectile_side_release", castParticle = "spell_engine:flame_spark", releaseParticle = "spell_engine:flame_spark", castSoundId = "spell_engine:generic_fire_casting", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "spell_engine:generic_fire_impact", fireTicks = 60, knockback = 0.55),
            magicProjectile("fireball", spellId = "wizards:fireball", duration = 27, hitTick = 16, damage = 3.0, cooldown = 42, recovery = 20, weight = 4, min = 4.0, max = 11.5, speed = 0.56, impactRadius = 1.55, particle = "spell_engine:flame_spark", impactParticle = "spell_engine:fire_explosion", animationId = "spell_engine:one_handed_projectile_charge", releaseAnimationId = "spell_engine:one_handed_projectile_release", castParticle = "spell_engine:flame_spark", releaseParticle = "spell_engine:flame_medium_a", castSoundId = "spell_engine:generic_fire_casting", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "spell_engine:generic_fire_impact", fireTicks = 90, knockback = 0.9),
            area("fire_scorch", "forcemaster_rpg:straight_punch", duration = 22, hitTick = 11, damage = 2.1, radius = 4.0, cooldown = 34, recovery = 16, weight = 5, min = 0.0, max = 4.6, spellId = "wizards:fire_scorch", knockback = 0.55, arc = 105.0, impactParticle = "spell_engine:flame_medium_a", releaseParticle = "spell_engine:flame_spark", castParticle = "spell_engine:flame_spark", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "spell_engine:generic_fire_impact", fireTicks = 80),
            area("flame_sweep", "spell_engine:one_handed_area_release", duration = 24, hitTick = 12, damage = 1.8, radius = 3.8, cooldown = 36, recovery = 16, weight = 4, min = 0.0, max = 4.1, spellId = "wizards:fire_scorch", knockback = 0.7, arc = 180.0, impactParticle = "spell_engine:flame_medium_a", releaseParticle = "spell_engine:flame_spark", castParticle = "spell_engine:flame_spark", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "spell_engine:generic_fire_impact", fireTicks = 60),
            beam("dragon_breath", spellId = "wizards:fire_breath", duration = 36, hitTicks = listOf(15, 20, 25, 30), damage = 0.75, cooldown = 68, recovery = 20, weight = 4, min = 2.5, max = 9.5, impactRadius = 0.85, particle = "spell_engine:flame_spark", impactParticle = "spell_engine:fire_explosion", castParticle = "spell_engine:flame_spark", releaseParticle = "spell_engine:flame_medium_a", castSoundId = "spell_engine:generic_fire_casting", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "spell_engine:generic_fire_impact", fireTicks = 80, minPhaseIndex = 1),
            area("fire_wall", "spell_engine:one_handed_area_release_ground_left_to_right", duration = 30, hitTick = 16, damage = 1.3, radius = 4.6, cooldown = 76, recovery = 20, weight = 4, min = 2.0, max = 5.2, spellId = "wizards:fire_wall", knockback = 0.35, impactParticle = "spell_engine:flame_medium_a", releaseParticle = "spell_engine:flame_spark", castParticle = "spell_engine:flame_spark", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "spell_engine:generic_fire_impact", fireTicks = 80, hazardRadius = 3.4, hazardTicks = 90, hazardIntervalTicks = 14, hazardDamage = 0.55, hazardParticle = "spell_engine:flame_medium_a", minPhaseIndex = 1),
            area("fire_meteor", "spell_engine:one_handed_sky_charge", duration = 38, hitTick = 25, damage = 3.4, radius = 5.2, cooldown = 92, recovery = 26, weight = 3, min = 3.0, max = 6.0, spellId = "wizards:fire_meteor", knockback = 1.15, impactParticle = "spell_engine:fire_explosion", releaseParticle = "spell_engine:flame_spark", castParticle = "spell_engine:flame_spark", releaseSoundId = "spell_engine:generic_fire_release", impactSoundId = "minecraft:entity.generic.explode", fireTicks = 110, hazardRadius = 2.8, hazardTicks = 50, hazardIntervalTicks = 16, hazardDamage = 0.45, hazardParticle = "minecraft:lava", minPhaseIndex = 1),
            roll("flame_roll", "combat_roll:roll", duration = 13, cooldown = 34, distance = 3.2, direction = "side", weight = 3, supportParticle = "spell_engine:flame_spark", spellId = "wizards:fire_scorch"),
            dodge("flame_step", animationId = "spell_engine:dodge", duration = 10, cooldown = 38, distance = 2.5, direction = "side", weight = 2, supportParticle = "spell_engine:flame_spark", spellId = "wizards:fire_scorch"),
        ),
    )

    private fun defaultWindWizard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "wind_wizard",
        displayName = "Wind Wizard",
        health = 86.0,
        damage = 3.1,
        attackStartDistance = 12.0,
        offenseChainMin = 2,
        offenseChainRandom = 1,
        offenseChainRecoveryTicks = 5,
        approachAnimationId = "",
        approachAnimationSource = NpcBossAnimationSources.NATURAL,
        strafeAnimationId = "",
        strafeAnimationSource = NpcBossAnimationSources.NATURAL,
        guardAnimationId = "",
        guardAnimationSource = NpcBossAnimationSources.NATURAL,
        parryAnimationId = "spell_engine:one_handed_area_release",
        recoveryAnimationId = "",
        recoveryAnimationSource = NpcBossAnimationSources.NATURAL,
        recoveryHitsAllowed = 3,
        guardRollWeight = 1,
        guardDodgeWeight = 2,
        guardParryWeight = 2,
        guardRollAnimationId = "combat_roll:roll",
        guardRollDistance = 3.4,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeTicks = 10,
        guardDodgeIframeTicks = 10,
        guardDodgeDistance = 3.0,
        guardDodgeDirection = "side",
        hoverHeight = 0.0,
        parrySpellId = "elemental_wizards_rpg:wind_gust",
        parryParticle = "more_rpg_classes:wind_vacuum",
        parrySoundId = "minecraft:entity.breeze.wind_burst",
        parryDamage = 0.7,
        parryKnockback = 1.35,
        phases = mutableListOf(
            phase(
                id = "air_nomad_flow",
                displayName = "Air Nomad Flow",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.28,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 5,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.5,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "avatar_current",
                displayName = "Avatar Current",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.12,
                speedMultiplier = 1.45,
                offenseChainMin = 3,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 4,
                transitionFallback = "Breathe. Move with it.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, stronger Wind Wizard second phase. Reply as Aang with one short battle line about breath, air, movement, or the Avatar current. Keep it playful but focused.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.56,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            magicProjectile("air_cutter", spellId = "elemental_wizards_rpg:wind_air_cutter", duration = 18, hitTick = 10, damage = 1.45, cooldown = 16, recovery = 10, weight = 8, min = 3.5, max = 12.0, speed = 0.9, impactRadius = 0.55, particle = "more_rpg_classes:wind_vacuum", impactParticle = "minecraft:poof", animationId = "spell_engine:one_handed_projectile_side_charge", releaseAnimationId = "spell_engine:one_handed_projectile_side_release", castParticle = "minecraft:cloud", releaseParticle = "minecraft:sweep_attack", castSoundId = "minecraft:entity.breeze.idle_air", releaseSoundId = "minecraft:entity.breeze.shoot", impactSoundId = "minecraft:entity.breeze.wind_burst", knockback = 0.45),
            magicProjectile("double_air_cutter", spellId = "elemental_wizards_rpg:wind_air_cutter", duration = 22, hitTick = 13, damage = 1.05, cooldown = 28, recovery = 12, weight = 5, min = 4.0, max = 12.0, speed = 0.86, impactRadius = 0.5, particle = "more_rpg_classes:wind_vacuum", impactParticle = "minecraft:poof", animationId = "spell_engine:one_handed_projectile_charge", releaseAnimationId = "spell_engine:one_handed_projectile_release", castParticle = "minecraft:cloud", releaseParticle = "minecraft:sweep_attack", castSoundId = "minecraft:entity.breeze.idle_air", releaseSoundId = "minecraft:entity.breeze.shoot", impactSoundId = "minecraft:entity.breeze.wind_burst", knockback = 0.35, count = 2, spreadDegrees = 9.0),
            area("wind_gust", "forcemaster_rpg:straight_punch", duration = 20, hitTick = 10, damage = 1.8, radius = 4.5, cooldown = 26, recovery = 12, weight = 6, min = 0.0, max = 5.0, spellId = "elemental_wizards_rpg:wind_gust", knockback = 1.25, arc = 110.0, impactParticle = "minecraft:cloud", releaseParticle = "more_rpg_classes:wind_vacuum", castParticle = "minecraft:cloud", releaseSoundId = "minecraft:entity.breeze.shoot", impactSoundId = "minecraft:entity.breeze.wind_burst"),
            area("spiral_gust", "spell_engine:one_handed_area_release_ground_left_to_right", duration = 24, hitTick = 12, damage = 1.55, radius = 4.2, cooldown = 34, recovery = 14, weight = 5, min = 0.0, max = 4.7, spellId = "elemental_wizards_rpg:wind_gust", knockback = 1.05, impactParticle = "minecraft:sweep_attack", releaseParticle = "more_rpg_classes:wind_vacuum", castParticle = "minecraft:cloud", releaseSoundId = "minecraft:entity.breeze.shoot", impactSoundId = "minecraft:entity.breeze.wind_burst"),
            area("updraft", "spell_engine:one_handed_sky_charge", duration = 30, hitTick = 16, damage = 1.4, radius = 5.4, cooldown = 64, recovery = 18, weight = 4, min = 0.0, max = 6.0, spellId = "elemental_wizards_rpg:improved_wind_updraft", knockback = 1.5, impactParticle = "minecraft:cloud", releaseParticle = "more_rpg_classes:wind_vacuum", castParticle = "minecraft:cloud", releaseSoundId = "minecraft:entity.breeze.shoot", impactSoundId = "minecraft:entity.breeze.wind_burst", statusEffectId = "minecraft:levitation", statusEffectTicks = 22, hazardRadius = 3.8, hazardTicks = 50, hazardIntervalTicks = 12, hazardDamage = 0.35, hazardParticle = "minecraft:cloud", minPhaseIndex = 1),
            support("avatar_current", "more_rpg_classes:sky_cast_one_handed", duration = 28, hitTick = 15, cooldown = 92, recovery = 18, weight = 2, min = 0.0, max = 10.0, spellId = "elemental_wizards_rpg:elemental_avatar", absorption = 4.0, absorptionTicks = 90, castParticle = "minecraft:cloud", supportParticle = "more_rpg_classes:wind_vacuum", castSoundId = "minecraft:entity.breeze.idle_air", releaseSoundId = "minecraft:entity.breeze.wind_burst", impactSoundId = "minecraft:entity.breeze.wind_burst", releaseAnimationId = "spell_engine:one_handed_area_release", minPhaseIndex = 1),
            area("avatar_burst", "spell_engine:two_handed_channeling", duration = 36, hitTick = 21, damage = 2.5, radius = 5.8, cooldown = 86, recovery = 22, weight = 3, min = 0.0, max = 6.2, spellId = "elemental_wizards_rpg:elemental_avatar", knockback = 1.75, impactParticle = "minecraft:sweep_attack", releaseParticle = "more_rpg_classes:wind_vacuum", castParticle = "minecraft:cloud", releaseSoundId = "minecraft:entity.breeze.wind_burst", impactSoundId = "minecraft:entity.breeze.wind_burst", releaseAnimationId = "spell_engine:one_handed_area_release", minPhaseIndex = 1),
            roll("air_roll", "combat_roll:roll", duration = 12, cooldown = 38, distance = 3.4, direction = "side", weight = 2, supportParticle = "minecraft:cloud", spellId = "elemental_wizards_rpg:wind_gust"),
            dodge("air_step", animationId = "spell_engine:dodge", duration = 10, cooldown = 28, distance = 3.2, direction = "side", weight = 3, supportParticle = "more_rpg_classes:wind_vacuum", releaseSoundId = "minecraft:entity.breeze.wind_burst", spellId = "elemental_wizards_rpg:wind_gust"),
        ),
    )

    private fun defaultArcaneWizard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "arcane_wizard",
        displayName = "Arcane Wizard",
        health = 88.0,
        damage = 3.0,
        attackStartDistance = 13.0,
        offenseChainMin = 2,
        offenseChainRandom = 1,
        offenseChainRecoveryTicks = 7,
        approachAnimationId = "spell_engine:one_handed_projectile_charge",
        strafeAnimationId = "spell_engine:one_handed_projectile_charge",
        guardAnimationId = "spell_engine:two_handed_channeling",
        parryAnimationId = "spell_engine:one_handed_area_release",
        recoveryAnimationId = "spell_engine:one_handed_projectile_charge",
        recoveryHitsAllowed = 4,
        guardRollWeight = 0,
        guardDodgeWeight = 3,
        guardParryWeight = 2,
        guardDodgeAnimationId = "spell_engine:one_handed_area_release",
        guardDodgeTicks = 10,
        guardDodgeIframeTicks = 10,
        guardDodgeDistance = 5.0,
        guardDodgeDirection = "back",
        hoverHeight = 1.0,
        parrySpellId = "wizards:arcane_blast",
        parryParticle = "spell_engine:magic_arcane_burst",
        parrySoundId = "wizards:arcane_blast_impact",
        parryDamage = 0.8,
        parryKnockback = 1.1,
        phases = mutableListOf(
            phase(
                id = "arcane_sequence",
                displayName = "Arcane Sequence",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.0,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 7,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.5,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "invoked_geometry",
                displayName = "Invoked Geometry",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.15,
                speedMultiplier = 1.18,
                offenseChainMin = 3,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 6,
                transitionFallback = "The pattern is complete. Now follow the light.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster arcane spellcaster second phase. Reply as Invoker with one short precise battle line about arcane geometry, spellcraft, or light.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.55,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            magicProjectile(
                "arcane_bolt",
                spellId = "wizards:arcane_bolt",
                duration = 18,
                hitTick = 10,
                damage = 1.4,
                cooldown = 14,
                recovery = 12,
                weight = 8,
                min = 4.0,
                max = 14.0,
                speed = 0.85,
                impactRadius = 0.45,
                particle = "spell_engine:magic_spell_ascend",
                impactParticle = "spell_engine:magic_arcane_burst",
                castParticle = "spell_engine:magic_spell_ascend",
                releaseParticle = "spell_engine:magic_arcane_burst",
                castSoundId = "spell_engine:generic_arcane_casting",
                releaseSoundId = "wizards:arcane_shoot_small",
                impactSoundId = "wizards:arcane_missile_impact",
            ),
            magicProjectile(
                "arcane_blast",
                spellId = "wizards:arcane_blast",
                duration = 26,
                hitTick = 16,
                damage = 2.6,
                cooldown = 28,
                recovery = 18,
                weight = 5,
                min = 5.0,
                max = 13.0,
                speed = 0.62,
                impactRadius = 0.8,
                particle = "spell_engine:magic_arcane_float",
                impactParticle = "spell_engine:magic_arcane_burst",
                castParticle = "spell_engine:magic_arcane_float",
                releaseParticle = "spell_engine:magic_arcane_burst",
                releaseSoundId = "wizards:arcane_blast_release",
                impactSoundId = "wizards:arcane_blast_impact",
                knockback = 0.85,
            ),
            magicProjectile(
                "arcane_missile",
                spellId = "wizards:arcane_missile",
                duration = 32,
                hitTick = 20,
                damage = 1.2,
                cooldown = 44,
                recovery = 22,
                weight = 5,
                min = 5.0,
                max = 14.0,
                speed = 0.72,
                impactRadius = 0.55,
                particle = "spell_engine:magic_arcane_float",
                impactParticle = "spell_engine:magic_arcane_burst",
                animationId = "spell_engine:two_handed_channeling",
                releaseAnimationId = "spell_engine:one_handed_projectile_release",
                castParticle = "spell_engine:magic_arcane_float",
                releaseParticle = "spell_engine:magic_arcane_burst",
                castSoundId = "wizards:arcane_beam_casting",
                releaseSoundId = "wizards:arcane_missile_release",
                impactSoundId = "wizards:arcane_missile_impact",
                count = 3,
                spreadDegrees = 8.0,
                minPhaseIndex = 1,
            ),
            beam(
                "arcane_beam",
                spellId = "wizards:arcane_beam",
                duration = 42,
                hitTicks = listOf(20, 25, 30, 35),
                damage = 0.8,
                cooldown = 74,
                recovery = 24,
                weight = 4,
                min = 5.0,
                max = 14.0,
                impactRadius = 0.8,
                particle = "spell_engine:magic_arcane_float",
                impactParticle = "spell_engine:magic_arcane_burst",
                castParticle = "spell_engine:magic_arcane_float",
                releaseParticle = "spell_engine:magic_arcane_burst",
                castSoundId = "wizards:arcane_beam_start",
                releaseSoundId = "wizards:arcane_beam_release",
                impactSoundId = "wizards:arcane_beam_impact",
                minPhaseIndex = 1,
            ),
            dodge(
                "arcane_blink",
                animationId = "spell_engine:one_handed_area_release",
                duration = 10,
                cooldown = 34,
                distance = 5.0,
                direction = "side",
                weight = 3,
                supportParticle = "minecraft:portal",
                releaseSoundId = "minecraft:entity.enderman.teleport",
            ),
        ),
    )

    private fun defaultEarthWizard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "earth_wizard",
        displayName = "Earth Wizard",
        health = 88.0,
        damage = 3.2,
        attackStartDistance = 10.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 10,
        approachAnimationId = "more_rpg_classes:two_handed_ground_channeling",
        strafeAnimationId = "more_rpg_classes:two_handed_ground_channeling",
        guardAnimationId = "more_rpg_classes:two_handed_ground_channeling",
        parryAnimationId = "forcemaster_rpg:stonehand_cast",
        recoveryAnimationId = "more_rpg_classes:two_handed_ground_channeling",
        recoveryHitsAllowed = 4,
        guardRollWeight = 0,
        guardDodgeWeight = 1,
        guardParryWeight = 3,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeTicks = 10,
        guardDodgeIframeTicks = 8,
        guardDodgeDistance = 2.0,
        guardDodgeDirection = "side",
        hoverHeight = 0.0,
        parrySpellId = "forcemaster_rpg:stonehand",
        parryParticle = "more_rpg_classes:stone_particle",
        parrySoundId = "forcemaster_rpg:stonehand_cast",
        parryDamage = 0.8,
        parryKnockback = 1.1,
        phases = mutableListOf(
            phase(
                id = "rooted_stance",
                displayName = "Rooted Stance",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 0.96,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 10,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.5,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "seismic_mastery",
                displayName = "Seismic Mastery",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.18,
                speedMultiplier = 1.1,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 8,
                transitionFallback = "Plant your feet. The whole ground is listening now.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a stronger earthbending second phase. Reply as Toph with one short blunt battle line about stance, stone, or feeling the player through the ground.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.56,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            magicProjectile("stone_throw", spellId = "elemental_wizards_rpg:terra_stone_throw", duration = 21, hitTick = 12, damage = 2.0, cooldown = 24, recovery = 16, weight = 7, min = 3.5, max = 10.5, speed = 0.68, impactRadius = 0.7, particle = "more_rpg_classes:stone_particle", impactParticle = "more_rpg_classes:stone_explosion", animationId = "spell_engine:one_handed_throw_charge", releaseAnimationId = "spell_engine:one_handed_throw_release_instant", castParticle = "more_rpg_classes:stone_particle", releaseParticle = "more_rpg_classes:stone_particle", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact1", knockback = 0.55),
            magicProjectile("side_stone_throw", spellId = "elemental_wizards_rpg:terra_stone_throw", duration = 23, hitTick = 13, damage = 1.9, cooldown = 28, recovery = 16, weight = 5, min = 3.0, max = 10.5, speed = 0.64, impactRadius = 0.65, particle = "more_rpg_classes:stone_particle", impactParticle = "more_rpg_classes:stone_explosion", animationId = "spell_engine:one_handed_projectile_side_charge", releaseAnimationId = "spell_engine:one_handed_projectile_side_release", castParticle = "more_rpg_classes:stone_particle", releaseParticle = "more_rpg_classes:stone_particle", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact1", knockback = 0.45),
            magicProjectile("stone_spear", spellId = "elemental_wizards_rpg:terra_stone_spear", duration = 27, hitTick = 15, damage = 3.0, cooldown = 42, recovery = 22, weight = 4, min = 4.0, max = 11.0, speed = 0.56, impactRadius = 0.9, particle = "more_rpg_classes:stone_particle", impactParticle = "more_rpg_classes:stone_explosion", animationId = "forcemaster_rpg:straight_punch", releaseAnimationId = "more_rpg_classes:two_handed_ground_release", castParticle = "more_rpg_classes:stone_particle", releaseParticle = "more_rpg_classes:stone_explosion", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "minecraft:block.pointed_dripstone.land", knockback = 0.95),
            area("impale", "more_rpg_classes:one_hand_groundsmash", duration = 29, hitTick = 16, damage = 2.4, radius = 3.4, cooldown = 54, recovery = 22, weight = 4, min = 0.0, max = 4.0, spellId = "elemental_wizards_rpg:terra_impale", knockback = 0.25, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "more_rpg_classes:stone_particle", castParticle = "more_rpg_classes:stone_particle", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact2", releaseAnimationId = "more_rpg_classes:two_handed_ground_release", statusEffectId = "minecraft:slowness", statusEffectTicks = 50, statusEffectAmplifier = 1),
            area("earthquake", "more_rpg_classes:two_handed_ground_release", duration = 34, hitTick = 18, damage = 2.6, radius = 5.8, cooldown = 70, recovery = 27, weight = 3, min = 0.0, max = 5.9, spellId = "elemental_wizards_rpg:terra_earthquake", knockback = 1.15, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "more_rpg_classes:stone_particle", castParticle = "more_rpg_classes:stone_particle", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact2", releaseAnimationId = "spell_engine:dual_handed_ground_release"),
            area("ground_ripple", "spell_engine:dual_handed_ground_release", duration = 26, hitTick = 13, damage = 1.7, radius = 4.8, cooldown = 50, recovery = 20, weight = 4, min = 0.0, max = 5.0, spellId = "elemental_wizards_rpg:terra_earthquake", knockback = 0.75, impactParticle = "more_rpg_classes:stone_particle", releaseParticle = "more_rpg_classes:stone_particle", castParticle = "more_rpg_classes:stone_particle", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact1", releaseAnimationId = "more_rpg_classes:two_handed_ground_release"),
            area("drip_circle", "more_rpg_classes:two_handed_jump_release", duration = 36, hitTick = 20, damage = 1.8, radius = 4.4, cooldown = 78, recovery = 26, weight = 4, min = 0.0, max = 4.9, spellId = "elemental_wizards_rpg:improved_terra_drip_circle", knockback = 0.55, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "more_rpg_classes:stone_particle", castParticle = "more_rpg_classes:stone_particle", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact2", releaseAnimationId = "more_rpg_classes:two_handed_ground_release", hazardRadius = 3.8, hazardTicks = 70, hazardIntervalTicks = 16, hazardDamage = 0.45, hazardParticle = "more_rpg_classes:stone_particle", minPhaseIndex = 1),
            area("stone_pillars", "witcher_rpg:sign_cast_ground", duration = 32, hitTick = 18, damage = 2.2, radius = 4.2, cooldown = 66, recovery = 24, weight = 4, min = 0.0, max = 4.8, spellId = "elemental_wizards_rpg:terra_drip_circle", knockback = 0.85, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "more_rpg_classes:stone_particle", castParticle = "more_rpg_classes:stone_particle", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact2", releaseAnimationId = "more_rpg_classes:two_handed_ground_release", minPhaseIndex = 1),
            magicProjectile("shattering_stone", spellId = "elemental_wizards_rpg:terra_shattering_stone", duration = 31, hitTick = 17, damage = 1.25, cooldown = 58, recovery = 22, weight = 4, min = 4.0, max = 10.5, speed = 0.62, impactRadius = 0.65, particle = "more_rpg_classes:stone_particle", impactParticle = "more_rpg_classes:stone_explosion", animationId = "spell_engine:one_handed_projectile_side_charge", releaseAnimationId = "spell_engine:one_handed_projectile_side_release", castParticle = "more_rpg_classes:stone_particle", releaseParticle = "more_rpg_classes:stone_explosion", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "more_rpg_classes:earth_magic_cast1", impactSoundId = "more_rpg_classes:earth_magic_impact1", knockback = 0.45, count = 3, spreadDegrees = 12.0, minPhaseIndex = 1),
            area("burstcrack", "forcemaster_rpg:burstcrack_cast", duration = 28, hitTick = 16, damage = 2.2, radius = 3.6, cooldown = 56, recovery = 22, weight = 3, min = 0.0, max = 4.0, spellId = "forcemaster_rpg:burstcrack", knockback = 1.0, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "forcemaster_rpg:ground_punch", castParticle = "more_rpg_classes:stone_particle", castSoundId = "forcemaster_rpg:burstcrack_cast", releaseSoundId = "forcemaster_rpg:burstcrack_release", impactSoundId = "more_rpg_classes:earth_magic_impact2", releaseAnimationId = "forcemaster_rpg:burstcrack_release"),
            area("stone_jab", "forcemaster_rpg:one_handed_knuckle_attack_2", duration = 20, hitTick = 10, damage = 1.6, radius = 2.8, cooldown = 38, recovery = 16, weight = 3, min = 0.0, max = 3.1, spellId = "forcemaster_rpg:stonehand", knockback = 0.8, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "more_rpg_classes:stone_particle", castParticle = "more_rpg_classes:stone_particle", castSoundId = "forcemaster_rpg:stonehand_cast", releaseSoundId = "forcemaster_rpg:stonehand_cast", impactSoundId = "more_rpg_classes:earth_magic_impact1", releaseAnimationId = "forcemaster_rpg:one_handed_knuckle_attack_4"),
            support("stone_flesh", "more_rpg_classes:sky_cast_one_handed", duration = 28, hitTick = 16, cooldown = 96, recovery = 20, weight = 2, min = 0.0, max = 9.0, spellId = "elemental_wizards_rpg:terra_stone_flesh", absorption = 5.0, absorptionTicks = 110, castParticle = "more_rpg_classes:stone_particle", supportParticle = "elemental_wizards_rpg:stone_flesh", castSoundId = "more_rpg_classes:earth_magic_cast1", releaseSoundId = "elemental_wizards_rpg:stone_flesh", impactSoundId = "elemental_wizards_rpg:stone_flesh", releaseAnimationId = "more_rpg_classes:two_handed_ground_release"),
            dodge("rooted_step", animationId = "spell_engine:dodge", duration = 10, cooldown = 54, distance = 1.9, direction = "side", weight = 1, supportParticle = "more_rpg_classes:stone_particle"),
        ),
    )

    private fun defaultPriest(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "priest",
        displayName = "Priest",
        health = 82.0,
        damage = 2.5,
        attackStartDistance = 10.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 9,
        approachAnimationId = "",
        approachAnimationSource = NpcBossAnimationSources.NATURAL,
        strafeAnimationId = "",
        strafeAnimationSource = NpcBossAnimationSources.NATURAL,
        guardAnimationId = "",
        guardAnimationSource = NpcBossAnimationSources.NATURAL,
        parryAnimationId = "spell_engine:one_handed_healing_release",
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
                damageMultiplier = 0.9,
                speedMultiplier = 0.95,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 9,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.48,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "phase_2",
                displayName = "Phase 2",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.1,
                speedMultiplier = 1.12,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 7,
                transitionFallback = "Mercy is not weakness. Stand with discipline.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a firmer priest second phase. Reply as Pope Leo with one short pastoral battle line about disciplined mercy, courage, or restraint.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.54,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            magicProjectile("holy_shock", spellId = "paladins:holy_shock", duration = 26, hitTick = 17, damage = 2.1, cooldown = 28, recovery = 18, weight = 6, min = 4.0, max = 11.0, speed = 0.52, impactRadius = 0.65, particle = "spell_engine:magic_holy_float", impactParticle = "spell_engine:magic_holy_burst", releaseSoundId = "spell_engine:generic_healing_release", impactSoundId = "paladins:holy_shock_damage"),
            area("judgement_burst", "spell_engine:one_handed_area_release", duration = 25, hitTick = 15, damage = 2.0, radius = 3.0, cooldown = 52, recovery = 20, weight = 3, min = 0.0, max = 3.4, spellId = "paladins:judgement", knockback = 0.85, impactParticle = "spell_engine:magic_holy_burst", releaseParticle = "spell_engine:area_circle_1", impactSoundId = "paladins:judgement_impact"),
            support("mercy_prayer", "spell_engine:one_handed_healing_charge", duration = 32, hitTick = 22, cooldown = 78, recovery = 24, weight = 2, min = 2.0, max = 10.0, spellId = "paladins:heal", selfHeal = 6.0, healCapRatio = 0.75, maxHealUses = 2, castParticle = "spell_engine:magic_spark_float", supportParticle = "spell_engine:magic_heal_ascend", releaseSoundId = "spell_engine:generic_healing_release", impactSoundId = "paladins:holy_shock_heal"),
            support("priest_absorption", "spell_engine:one_handed_healing_charge", duration = 28, hitTick = 18, cooldown = 90, recovery = 18, weight = 2, min = 1.0, max = 9.0, spellId = "paladins:barrier", absorption = 4.0, absorptionTicks = 100, castParticle = "spell_engine:magic_spark_float", supportParticle = "spell_engine:shield_small", releaseSoundId = "paladins:holy_barrier_activate", impactSoundId = "paladins:holy_barrier_activate"),
            roll("sanctuary_step", "spell_engine:dodge", duration = 12, cooldown = 40, distance = 3.2, direction = "back", weight = 3, supportParticle = "spell_engine:magic_holy_decelerate"),
        ),
    )

    private fun defaultBerserker(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "berserker",
        displayName = "Berserker",
        health = 105.0,
        damage = 4.5,
        attackStartDistance = 5.0,
        offenseChainMin = 1,
        offenseChainRandom = 0,
        offenseChainRecoveryTicks = 14,
        approachAnimationId = "simplyswords:pose_two_handed_dragable",
        strafeAnimationId = "simplyswords:pose_two_handed_dragable",
        guardAnimationId = "simplyswords:pose_two_handed_dragable",
        parryAnimationId = "bettercombat:two_handed_slam",
        recoveryAnimationId = "simplyswords:pose_two_handed_dragable",
        recoveryHitsAllowed = 4,
        guardRollWeight = 0,
        guardDodgeWeight = 1,
        guardParryWeight = 4,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeDistance = 2.2,
        guardDodgeDirection = "side",
        parryDamage = 1.1,
        parryKnockback = 1.0,
        parryParticle = "more_rpg_classes:rage_particle",
        parrySoundId = "simplyswords:dark_sword_parry",
        phases = mutableListOf(
            phase(
                id = "blood_oath",
                displayName = "Blood Oath",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 0.94,
                offenseChainMin = 1,
                offenseChainRandom = 0,
                offenseChainRecoveryTicks = 14,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.55,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "death_defiance",
                displayName = "Death Defiance",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.2,
                speedMultiplier = 1.08,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 12,
                transitionFallback = "Blood and darkness. One more run.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a heavier, more frenzied Berserker second phase. Reply as Zagreus with one short battle line: witty, controlled, dangerous, not cruel.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.6,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            melee("blood_sweep", "simplyswords:dragable_slash_upward", duration = 31, hitTick = 17, damage = 3.9, range = 4.1, arc = 75.0, cooldown = 32, recovery = 28, weight = 6, spellId = "berserker_rpg:bloody_strike", impactParticle = "spell_engine:dripping_blood", releaseParticle = "more_rpg_classes:rage_particle", releaseSoundId = "simplyswords:elemental_sword_earth_attack_01", impactSoundId = "berserker_rpg:bloody_strike", knockback = 0.65),
            melee("red_line_chop", "bettercombat:two_handed_slam", duration = 36, hitTick = 21, damage = 5.2, range = 4.0, arc = 55.0, cooldown = 44, recovery = 34, weight = 4, spellId = "berserker_rpg:bloody_strike", impactParticle = "more_rpg_classes:blood_drop", releaseParticle = "crimson_spore", releaseSoundId = "simplyswords:elemental_sword_earth_attack_02", impactSoundId = "simplyswords:object_impact_thud", knockback = 0.95),
            melee("decapitate", "berserker_rpg:decapitate_charge", duration = 44, hitTick = 28, damage = 6.3, range = 4.3, arc = 65.0, cooldown = 78, recovery = 40, weight = 3, spellId = "berserker_rpg:decapitate", releaseAnimationId = "berserker_rpg:decapitate_release", castParticle = "crimson_spore", releaseParticle = "spell_engine:dripping_blood", impactParticle = "more_rpg_classes:blood_drop", releaseSoundId = "berserker_rpg:decapitate_swing", impactSoundId = "berserker_rpg:decapitate_impact", knockback = 1.25),
            area("wild_rage", "more_rpg_classes:two_handed_roar", duration = 25, hitTick = 13, damage = 1.0, radius = 3.1, cooldown = 64, recovery = 24, weight = 3, min = 0.0, max = 4.0, spellId = "berserker_rpg:wild_rage", knockback = 1.0, impactParticle = "more_rpg_classes:rage_particle", releaseParticle = "spell_engine:magic_stripe_float", releaseSoundId = "berserker_rpg:wild_rage", impactSoundId = "berserker_rpg:wild_rage"),
            area("rumbling_swing", "berserker_rpg:rumbling_swing", duration = 38, hitTick = 21, damage = 4.6, radius = 4.2, cooldown = 58, recovery = 32, weight = 4, min = 0.0, max = 5.2, spellId = "berserker_rpg:rumbling_swing", knockback = 1.35, arc = 130.0, impactParticle = "electric_spark", releaseParticle = "electric_spark", releaseSoundId = "minecraft:entity.player.attack.sweep", impactSoundId = "minecraft:entity.lightning_bolt.impact", minPhaseIndex = 1),
            area("nordic_storm", "berserker_rpg:nordic_storm", duration = 44, hitTick = 24, damage = 3.5, radius = 3.6, cooldown = 68, recovery = 34, weight = 3, min = 0.0, max = 4.4, spellId = "berserker_rpg:nordic_storm", knockback = 0.7, impactParticle = "spell_engine:frost_shard", releaseParticle = "sweep_attack", castParticle = "spell_engine:frost_shard", releaseSoundId = "spell_engine:generic_frost_impact", impactSoundId = "minecraft:item.axe.strip", statusEffectId = "minecraft:slowness", statusEffectTicks = 30, minPhaseIndex = 1),
            roll("death_defiance_step", "spell_engine:dodge", duration = 12, cooldown = 46, distance = 2.4, direction = "side", weight = 2, supportParticle = "more_rpg_classes:rage_particle"),
        ),
    )

    private fun defaultForcemaster(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "forcemaster",
        displayName = "Forcemaster",
        health = 100.0,
        damage = 3.2,
        attackStartDistance = 7.0,
        offenseChainMin = 3,
        offenseChainRandom = 1,
        offenseChainRecoveryTicks = 6,
        approachAnimationId = "",
        approachAnimationSource = NpcBossAnimationSources.NATURAL,
        strafeAnimationId = "",
        strafeAnimationSource = NpcBossAnimationSources.NATURAL,
        guardAnimationId = "forcemaster_rpg:stonehand_cast",
        parryAnimationId = "forcemaster_rpg:stonehand_cast",
        recoveryAnimationId = "",
        recoveryAnimationSource = NpcBossAnimationSources.NATURAL,
        recoveryHitsAllowed = 3,
        guardRollWeight = 0,
        guardDodgeWeight = 2,
        guardParryWeight = 3,
        guardDodgeAnimationId = "spell_engine:dodge",
        guardDodgeTicks = 10,
        guardDodgeIframeTicks = 8,
        guardDodgeDistance = 2.4,
        guardDodgeDirection = "side",
        parrySpellId = "forcemaster_rpg:stonehand",
        parryParticle = "more_rpg_classes:stone_particle",
        parrySoundId = "forcemaster_rpg:stonehand_cast",
        parryDamage = 0.9,
        parryKnockback = 1.15,
        phases = mutableListOf(
            phase(
                id = "street_boxer",
                displayName = "Street Boxer",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.22,
                offenseChainMin = 3,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 6,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.54,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "hextech_berserker",
                displayName = "Hextech Berserker",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.16,
                speedMultiplier = 1.38,
                offenseChainMin = 4,
                offenseChainRandom = 2,
                offenseChainRecoveryTicks = 5,
                transitionFallback = "Good. Now we stop playing nice.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster, harder Forcemaster boxer second phase. Reply as Vi with one short blunt battle line about fists, pressure, or keeping guard up.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.6,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            NpcBossMoveDefinition(
                id = "jab_cross",
                kind = NpcBossMoveKinds.MELEE,
                animationId = "forcemaster_rpg:one_handed_knuckle_attack_1",
                releaseAnimationId = "forcemaster_rpg:one_handed_knuckle_attack_2",
                spellId = "forcemaster_rpg:belial_smashing",
                durationTicks = 16,
                hitTicks = mutableListOf(6, 11),
                cooldownTicks = 12,
                recoveryTicks = 9,
                damage = 1.05,
                range = 2.7,
                arcDegrees = 90.0,
                knockback = 0.35,
                weight = 8,
                maxDistance = 3.1,
                castParticle = "minecraft:crit",
                releaseParticle = "minecraft:sweep_attack",
                impactParticle = "minecraft:crit",
                releaseSoundId = "minecraft:entity.player.attack.sweep",
                impactSoundId = "minecraft:entity.player.attack.strong",
            ),
            NpcBossMoveDefinition(
                id = "hook_chain",
                kind = NpcBossMoveKinds.MELEE,
                animationId = "forcemaster_rpg:one_handed_knuckle_attack_3",
                releaseAnimationId = "forcemaster_rpg:one_handed_knuckle_attack_4",
                spellId = "forcemaster_rpg:belial_smashing",
                durationTicks = 20,
                hitTicks = mutableListOf(8, 14),
                cooldownTicks = 17,
                recoveryTicks = 11,
                damage = 1.25,
                range = 2.9,
                arcDegrees = 120.0,
                knockback = 0.55,
                weight = 7,
                maxDistance = 3.3,
                castParticle = "minecraft:crit",
                releaseParticle = "minecraft:sweep_attack",
                impactParticle = "minecraft:sweep_attack",
                releaseSoundId = "minecraft:entity.player.attack.sweep",
                impactSoundId = "minecraft:entity.player.attack.strong",
            ),
            melee("straight_punch", "forcemaster_rpg:straight_punch", duration = 18, hitTick = 9, damage = 2.35, range = 3.2, arc = 65.0, cooldown = 22, recovery = 13, weight = 6, spellId = "forcemaster_rpg:belial_smashing", castParticle = "minecraft:crit", releaseParticle = "minecraft:sweep_attack", impactParticle = "more_rpg_classes:stone_particle", releaseSoundId = "minecraft:entity.player.attack.sweep", impactSoundId = "minecraft:entity.player.attack.knockback", knockback = 1.0),
            area("body_breaker", "forcemaster_rpg:one_handed_knuckle_attack_2", duration = 19, hitTick = 10, damage = 1.75, radius = 2.6, cooldown = 28, recovery = 13, weight = 5, min = 0.0, max = 3.0, spellId = "forcemaster_rpg:stonehand", knockback = 0.65, arc = 90.0, impactParticle = "more_rpg_classes:stone_particle", releaseParticle = "minecraft:crit", castParticle = "minecraft:crit", releaseSoundId = "minecraft:entity.player.attack.sweep", impactSoundId = "minecraft:entity.player.attack.crit", statusEffectId = "minecraft:weakness", statusEffectTicks = 45),
            area("burstcrack", "forcemaster_rpg:burstcrack_cast", duration = 28, hitTick = 16, damage = 2.45, radius = 3.7, cooldown = 52, recovery = 20, weight = 4, min = 0.0, max = 4.2, spellId = "forcemaster_rpg:burstcrack", knockback = 1.05, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "forcemaster_rpg:ground_punch", castParticle = "more_rpg_classes:stone_particle", castSoundId = "forcemaster_rpg:burstcrack_cast", releaseSoundId = "forcemaster_rpg:burstcrack_release", impactSoundId = "minecraft:entity.generic.explode", releaseAnimationId = "forcemaster_rpg:burstcrack_release"),
            support("stonehand", "forcemaster_rpg:stonehand_cast", duration = 24, hitTick = 13, cooldown = 76, recovery = 12, weight = 2, min = 0.0, max = 6.0, spellId = "forcemaster_rpg:stonehand", absorption = 4.0, absorptionTicks = 70, castParticle = "more_rpg_classes:stone_particle", supportParticle = "more_rpg_classes:stone_particle", castSoundId = "forcemaster_rpg:stonehand_cast", releaseSoundId = "forcemaster_rpg:stonehand_cast", impactSoundId = "forcemaster_rpg:stonehand_cast", releaseAnimationId = "forcemaster_rpg:stonehand_cast"),
            area("belial_smashing", "forcemaster_rpg:fist_rush", duration = 30, hitTick = 17, damage = 3.2, radius = 3.8, cooldown = 64, recovery = 22, weight = 4, min = 0.0, max = 4.6, spellId = "forcemaster_rpg:belial_smashing", knockback = 1.35, arc = 115.0, impactParticle = "more_rpg_classes:stone_explosion", releaseParticle = "minecraft:sweep_attack", castParticle = "more_rpg_classes:rage_particle", releaseSoundId = "minecraft:entity.player.attack.sweep", impactSoundId = "minecraft:entity.generic.explode", minPhaseIndex = 1),
            melee("asal", "forcemaster_rpg:asal_cast", duration = 38, hitTick = 25, damage = 4.0, range = 3.5, arc = 75.0, cooldown = 90, recovery = 26, weight = 2, spellId = "forcemaster_rpg:asal", releaseAnimationId = "forcemaster_rpg:asal_release", castParticle = "more_rpg_classes:rage_particle", releaseParticle = "more_rpg_classes:stone_explosion", impactParticle = "more_rpg_classes:stone_explosion", castSoundId = "forcemaster_rpg:stonehand_cast", releaseSoundId = "minecraft:entity.player.attack.strong", impactSoundId = "minecraft:entity.generic.explode", knockback = 1.6, minPhaseIndex = 1),
            dodge("weave_step", animationId = "spell_engine:dodge", duration = 10, cooldown = 26, distance = 2.4, direction = "side", weight = 3, supportParticle = "minecraft:crit", releaseSoundId = "minecraft:entity.player.attack.sweep", spellId = "forcemaster_rpg:stonehand"),
            dodge("pressure_step", animationId = "spell_engine:dodge", duration = 10, cooldown = 30, distance = 2.1, direction = "forward", weight = 2, supportParticle = "more_rpg_classes:rage_particle", releaseSoundId = "minecraft:entity.player.attack.sweep", spellId = "forcemaster_rpg:belial_smashing"),
        ),
    )

    private fun defaultWitcher(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "witcher",
        displayName = "Witcher",
        health = 94.0,
        damage = 3.5,
        attackStartDistance = 7.5,
        offenseChainMin = 2,
        offenseChainRandom = 1,
        offenseChainRecoveryTicks = 9,
        recoveryAnimationId = NpcBossMovesetDefinition.DEFAULT_RECOVERY_ANIMATION,
        recoveryHitsAllowed = 4,
        guardRollAnimationId = "witcher_rpg:witcher_reflexes",
        guardDodgeAnimationId = "witcher_rpg:witcher_reflexes",
        guardDodgeDistance = 3.0,
        guardDodgeDirection = "side",
        phases = mutableListOf(
            phase(
                id = "signs_and_steel",
                displayName = "Signs And Steel",
                startsAtHealthRatio = 1.0,
                damageMultiplier = 1.0,
                speedMultiplier = 1.08,
                offenseChainMin = 2,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 9,
                musicId = "cataclysm:enderguardian_music_1",
                musicVolume = 0.52,
                musicRepeatTicks = 3000,
            ),
            phase(
                id = "mutagen_focus",
                displayName = "Mutagen Focus",
                startsAtHealthRatio = 0.5,
                damageMultiplier = 1.18,
                speedMultiplier = 1.24,
                offenseChainMin = 3,
                offenseChainRandom = 1,
                offenseChainRecoveryTicks = 7,
                transitionFallback = "Enough. Watch the signs.",
                transitionLlmPrompt = "The duel has reached half health and you are entering a faster Witcher second phase with stronger signs, Yrden, Axii, Rend, and Whirl. Reply as Geralt with one short dry battle line.",
                musicId = "cataclysm:maledictus_music",
                musicVolume = 0.58,
                musicRepeatTicks = 3000,
            ),
        ),
        moves = mutableListOf(
            melee("fast_attack_1", "witcher_rpg:fast_attack_witcher_1", duration = 17, hitTick = 7, damage = 2.4, range = 2.8, arc = 95.0, cooldown = 13, recovery = 20, weight = 5, spellId = "witcher_rpg:fast_attack"),
            melee("fast_attack_2", "witcher_rpg:fast_attack_witcher_2", duration = 17, hitTick = 7, damage = 2.4, range = 2.8, arc = 95.0, cooldown = 13, recovery = 20, weight = 5, spellId = "witcher_rpg:fast_attack"),
            melee("fast_attack_3", "witcher_rpg:fast_attack_witcher_3", duration = 18, hitTick = 8, damage = 2.6, range = 2.9, arc = 95.0, cooldown = 16, recovery = 22, weight = 4, spellId = "witcher_rpg:fast_attack"),
            melee("strong_attack", "witcher_rpg:strong_attack_witcher_1", duration = 27, hitTick = 14, damage = 3.6, range = 3.1, arc = 85.0, cooldown = 34, recovery = 30, weight = 3, spellId = "witcher_rpg:strong_attack"),
            melee("rend", "witcher_rpg:rend_cast", duration = 36, hitTick = 24, damage = 4.6, range = 4.1, arc = 55.0, cooldown = 72, recovery = 42, weight = 3, spellId = "witcher_rpg:rend", releaseAnimationId = "witcher_rpg:rend_release", castParticle = "crimson_spore", releaseParticle = "crimson_spore", impactParticle = "sweep_attack", releaseSoundId = "witcher_rpg:rend_spell", minPhaseIndex = 1),
            area("aard", "witcher_rpg:sign_cast_short", duration = 20, hitTick = 11, damage = 1.4, radius = 7.2, cooldown = 25, recovery = 18, weight = 7, spellId = "witcher_rpg:aard", knockback = 1.45, arc = 90.0, impactParticle = "more_rpg_classes:wind_vacuum", releaseParticle = "witcher_rpg:aard_sign_cast", castParticle = "witcher_rpg:aard_sign_cast", releaseSoundId = "witcher_rpg:aard_sign", impactSoundId = "witcher_rpg:aard_sign"),
            area("igni", "witcher_rpg:sign_cast_long", duration = 30, hitTick = 16, damage = 2.0, radius = 5.8, cooldown = 32, recovery = 22, weight = 6, spellId = "witcher_rpg:igni", knockback = 0.2, arc = 90.0, impactParticle = "spell_engine:flame_medium_a", releaseParticle = "witcher_rpg:igni_sign_cast", castParticle = "witcher_rpg:igni_sign_cast", releaseSoundId = "witcher_rpg:igni_sign", impactSoundId = "witcher_rpg:igni_sign", fireTicks = 80),
            support("quen", "witcher_rpg:sign_cast_short", duration = 24, hitTick = 13, cooldown = 58, recovery = 18, weight = 3, min = 0.0, max = 7.5, spellId = "witcher_rpg:quen", absorption = 5.0, absorptionTicks = 110, castParticle = "witcher_rpg:quen_sign_cast", supportParticle = "spell_engine:electric_arc_a", releaseSoundId = "witcher_rpg:quen_sign", impactSoundId = "witcher_rpg:quen_sign", releaseAnimationId = "witcher_rpg:sign_cast_short"),
            area("aard_sweep", "witcher_rpg:sign_cast_ground", duration = 26, hitTick = 15, damage = 1.2, radius = 4.2, cooldown = 52, recovery = 22, weight = 4, spellId = "witcher_rpg:aard_sweep", knockback = 1.25, impactParticle = "more_rpg_classes:wind_vacuum", releaseParticle = "witcher_rpg:aard_sign_cast", castParticle = "witcher_rpg:aard_sign_cast", releaseSoundId = "witcher_rpg:aard_sign", impactSoundId = "witcher_rpg:aard_sign", minPhaseIndex = 1),
            area("yrden", "witcher_rpg:sign_cast_ground", duration = 30, hitTick = 18, damage = 0.8, radius = 6.0, cooldown = 66, recovery = 24, weight = 5, spellId = "witcher_rpg:yrden", knockback = 0.0, impactParticle = "spell_engine:ground_glow", releaseParticle = "witcher_rpg:yrden_sign_cast", castParticle = "witcher_rpg:yrden_sign_cast", releaseSoundId = "witcher_rpg:yrden_sign", impactSoundId = "witcher_rpg:yrden_sign", statusEffectId = "minecraft:slowness", statusEffectTicks = 35, hazardRadius = 3.2, hazardTicks = 120, hazardIntervalTicks = 16, hazardDamage = 0.55, hazardParticle = "witcher_rpg:yrden_cloud", minPhaseIndex = 1),
            magicProjectile("axii", spellId = "witcher_rpg:axii", duration = 28, hitTick = 17, damage = 1.0, cooldown = 54, recovery = 22, weight = 4, min = 3.0, max = 9.5, speed = 0.6, impactRadius = 0.9, particle = "witcher_rpg:axii_sign_cast", impactParticle = "witcher_rpg:axii_sign_cast", statusEffectId = "minecraft:weakness", statusEffectTicks = 70, statusEffectAmplifier = 0, animationId = "witcher_rpg:sign_cast_long", releaseAnimationId = "witcher_rpg:sign_cast_short", castParticle = "witcher_rpg:axii_sign_cast", releaseParticle = "witcher_rpg:axii_sign_cast", releaseSoundId = "witcher_rpg:axii_sign", impactSoundId = "witcher_rpg:axii_sign", minPhaseIndex = 1),
            area("normal_spin", "witcher_rpg:witcher_normal_spin", duration = 25, hitTick = 12, damage = 2.8, radius = 2.7, cooldown = 42, recovery = 28, weight = 3, spellId = "witcher_rpg:whirl", impactParticle = "sweep_attack", releaseSoundId = "witcher_rpg:whirl"),
            area("whirl", "witcher_rpg:witcher_whirl", duration = 34, hitTick = 13, damage = 3.4, radius = 3.1, cooldown = 62, recovery = 34, weight = 3, spellId = "witcher_rpg:whirl", impactParticle = "sweep_attack", releaseSoundId = "witcher_rpg:whirl", impactSoundId = "witcher_rpg:whirl", minPhaseIndex = 1),
            roll("reflexes", "witcher_rpg:witcher_reflexes", duration = 14, cooldown = 38, distance = 3.0, direction = "side", weight = 3, supportParticle = "witcher_rpg:quen_sign_cast"),
        ),
    )

    private fun melee(
        id: String,
        animationId: String,
        duration: Int,
        hitTick: Int,
        damage: Double,
        range: Double,
        arc: Double,
        cooldown: Int,
        recovery: Int,
        weight: Int,
        spellId: String = "",
        releaseAnimationId: String = "",
        castParticle: String = "",
        releaseParticle: String = "",
        impactParticle: String = "",
        castSoundId: String = "",
        releaseSoundId: String = "",
        impactSoundId: String = "",
        knockback: Double = 0.35,
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.MELEE, animationId = animationId, releaseAnimationId = releaseAnimationId, spellId = spellId, durationTicks = duration, hitTicks = mutableListOf(hitTick), damage = damage, range = range, arcDegrees = arc, cooldownTicks = cooldown, recoveryTicks = recovery, weight = weight, maxDistance = range + 0.4, knockback = knockback, castParticle = castParticle, releaseParticle = releaseParticle, impactParticle = impactParticle, castSoundId = castSoundId, releaseSoundId = releaseSoundId, impactSoundId = impactSoundId, minPhaseIndex = minPhaseIndex, maxPhaseIndex = maxPhaseIndex)

    private fun area(
        id: String,
        animationId: String,
        duration: Int,
        hitTick: Int,
        damage: Double,
        radius: Double,
        cooldown: Int,
        recovery: Int,
        weight: Int,
        min: Double = 0.0,
        max: Double = radius + 0.8,
        spellId: String = "",
        knockback: Double = 0.35,
        arc: Double = 360.0,
        impactParticle: String = "",
        releaseParticle: String = "",
        castParticle: String = "",
        castSoundId: String = "",
        releaseSoundId: String = "",
        impactSoundId: String = "",
        releaseAnimationId: String = "",
        statusEffectId: String = "",
        statusEffectTicks: Int = 0,
        statusEffectAmplifier: Int = 0,
        fireTicks: Int = 0,
        hazardRadius: Double = 0.0,
        hazardTicks: Int = 0,
        hazardIntervalTicks: Int = 10,
        hazardDamage: Double = 0.0,
        hazardParticle: String = "",
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.AREA, animationId = animationId, releaseAnimationId = releaseAnimationId, durationTicks = duration, hitTicks = mutableListOf(hitTick), damage = damage, range = radius, arcDegrees = arc, areaRadius = radius, cooldownTicks = cooldown, recoveryTicks = recovery, weight = weight, minDistance = min, maxDistance = max, spellId = spellId, knockback = knockback, impactParticle = impactParticle, releaseParticle = releaseParticle, castParticle = castParticle, castSoundId = castSoundId, releaseSoundId = releaseSoundId, impactSoundId = impactSoundId, statusEffectId = statusEffectId, statusEffectTicks = statusEffectTicks, statusEffectAmplifier = statusEffectAmplifier, fireTicks = fireTicks, hazardRadius = hazardRadius, hazardTicks = hazardTicks, hazardIntervalTicks = hazardIntervalTicks, hazardDamage = hazardDamage, hazardParticle = hazardParticle, minPhaseIndex = minPhaseIndex, maxPhaseIndex = maxPhaseIndex)

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
        spellId: String = "",
        animationId: String = "spell_engine:archery_pull",
        releaseAnimationId: String = "spell_engine:archery_release",
        projectileParticle: String = "",
        impactParticle: String = "",
        impactRadius: Double = 0.4,
        castParticle: String = "",
        releaseParticle: String = "",
        castSoundId: String = "",
        releaseSoundId: String = "",
        impactSoundId: String = "",
        statusEffectId: String = "",
        statusEffectTicks: Int = 0,
        statusEffectAmplifier: Int = 0,
        fireTicks: Int = 0,
        hazardRadius: Double = 0.0,
        hazardTicks: Int = 0,
        hazardIntervalTicks: Int = 10,
        hazardDamage: Double = 0.0,
        hazardParticle: String = "",
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition = NpcBossMoveDefinition(
        id = id,
        kind = NpcBossMoveKinds.PROJECTILE,
        animationId = animationId,
        releaseAnimationId = releaseAnimationId,
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
        projectileSpeed = speed,
        projectileInaccuracy = inaccuracy,
        projectileCount = count,
        projectileSpreadDegrees = spreadDegrees,
        projectileParticle = projectileParticle,
        impactParticle = impactParticle,
        impactRadius = impactRadius,
        castParticle = castParticle,
        releaseParticle = releaseParticle,
        castSoundId = castSoundId,
        releaseSoundId = releaseSoundId,
        impactSoundId = impactSoundId,
        statusEffectId = statusEffectId,
        statusEffectTicks = statusEffectTicks,
        statusEffectAmplifier = statusEffectAmplifier,
        fireTicks = fireTicks,
        hazardRadius = hazardRadius,
        hazardTicks = hazardTicks,
        hazardIntervalTicks = hazardIntervalTicks,
        hazardDamage = hazardDamage,
        hazardParticle = hazardParticle,
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
        fireTicks: Int = 0,
        animationId: String = "spell_engine:one_handed_projectile_charge",
        releaseAnimationId: String = "spell_engine:one_handed_projectile_release",
        castParticle: String = "",
        releaseParticle: String = "",
        castSoundId: String = "",
        releaseSoundId: String = "",
        impactSoundId: String = "",
        knockback: Double = 0.35,
        count: Int = 1,
        spreadDegrees: Double = 0.0,
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition = NpcBossMoveDefinition(
        id = id,
        kind = NpcBossMoveKinds.PROJECTILE,
        animationId = animationId,
        releaseAnimationId = releaseAnimationId,
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
        projectileCount = count,
        projectileSpreadDegrees = spreadDegrees,
        projectileParticle = particle,
        impactParticle = impactParticle,
        impactRadius = impactRadius,
        statusEffectId = statusEffectId,
        statusEffectTicks = statusEffectTicks,
        statusEffectAmplifier = statusEffectAmplifier,
        fireTicks = fireTicks,
        castParticle = castParticle,
        releaseParticle = releaseParticle,
        castSoundId = castSoundId,
        releaseSoundId = releaseSoundId,
        impactSoundId = impactSoundId,
        knockback = knockback,
        minPhaseIndex = minPhaseIndex,
        maxPhaseIndex = maxPhaseIndex,
    )

    private fun beam(
        id: String,
        spellId: String,
        duration: Int,
        hitTicks: List<Int>,
        damage: Double,
        cooldown: Int,
        recovery: Int,
        weight: Int,
        min: Double,
        max: Double,
        impactRadius: Double,
        particle: String,
        impactParticle: String,
        castParticle: String = "",
        releaseParticle: String = "",
        castSoundId: String = "",
        releaseSoundId: String = "",
        impactSoundId: String = "",
        fireTicks: Int = 0,
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition = NpcBossMoveDefinition(
        id = id,
        kind = NpcBossMoveKinds.BEAM,
        animationId = "spell_engine:two_handed_channeling",
        releaseAnimationId = "spell_engine:one_handed_projectile_release",
        spellId = spellId,
        durationTicks = duration,
        hitTicks = hitTicks.toMutableList(),
        damage = damage,
        range = max,
        arcDegrees = 70.0,
        cooldownTicks = cooldown,
        recoveryTicks = recovery,
        weight = weight,
        minDistance = min,
        maxDistance = max,
        projectileType = "magic",
        projectileParticle = particle,
        impactParticle = impactParticle,
        impactRadius = impactRadius,
        castParticle = castParticle,
        releaseParticle = releaseParticle,
        castSoundId = castSoundId,
        releaseSoundId = releaseSoundId,
        impactSoundId = impactSoundId,
        fireTicks = fireTicks,
        knockback = 0.25,
        minPhaseIndex = minPhaseIndex,
        maxPhaseIndex = maxPhaseIndex,
    )

    private fun support(
        id: String,
        animationId: String,
        duration: Int,
        hitTick: Int,
        cooldown: Int,
        recovery: Int,
        weight: Int,
        min: Double,
        max: Double,
        spellId: String,
        selfHeal: Double = 0.0,
        healCapRatio: Double = 1.0,
        maxHealUses: Int = 0,
        absorption: Double = 0.0,
        absorptionTicks: Int = 0,
        castParticle: String = "",
        supportParticle: String = "",
        releaseSoundId: String = "",
        impactSoundId: String = "",
        castSoundId: String = "",
        releaseAnimationId: String = "spell_engine:one_handed_healing_release",
        minPhaseIndex: Int = 0,
        maxPhaseIndex: Int = 99,
    ): NpcBossMoveDefinition = NpcBossMoveDefinition(
        id = id,
        kind = NpcBossMoveKinds.SUPPORT,
        animationId = animationId,
        releaseAnimationId = releaseAnimationId,
        spellId = spellId,
        durationTicks = duration,
        hitTicks = mutableListOf(hitTick),
        cooldownTicks = cooldown,
        recoveryTicks = recovery,
        damage = 0.0,
        range = max,
        arcDegrees = 360.0,
        weight = weight,
        minDistance = min,
        maxDistance = max,
        selfHealAmount = selfHeal,
        selfHealCapHealthRatio = healCapRatio,
        selfHealMaxUsesPerPhase = maxHealUses,
        absorptionAmount = absorption,
        absorptionTicks = absorptionTicks,
        castParticle = castParticle,
        supportParticle = supportParticle,
        castSoundId = castSoundId,
        releaseSoundId = releaseSoundId,
        impactSoundId = impactSoundId,
        minPhaseIndex = minPhaseIndex,
        maxPhaseIndex = maxPhaseIndex,
    )

    private fun roll(id: String, animationId: String, duration: Int, cooldown: Int, distance: Double, direction: String, weight: Int, supportParticle: String = "", spellId: String = ""): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.ROLL, animationId = animationId, spellId = spellId, durationTicks = duration, hitTicks = mutableListOf(), damage = 0.0, cooldownTicks = cooldown, recoveryTicks = 0, weight = weight, minDistance = 0.0, maxDistance = 3.5, rollDistance = distance, rollDirection = direction, iframeStartTick = 0, iframeEndTick = duration.coerceAtLeast(1), supportParticle = supportParticle)

    private fun dodge(id: String, animationId: String, duration: Int, cooldown: Int, distance: Double, direction: String, weight: Int, supportParticle: String = "", releaseSoundId: String = "", spellId: String = ""): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.DODGE, animationId = animationId, releaseAnimationId = animationId, spellId = spellId, durationTicks = duration, hitTicks = mutableListOf(), damage = 0.0, cooldownTicks = cooldown, recoveryTicks = 0, weight = weight, minDistance = 0.0, maxDistance = 14.0, rollDistance = distance, rollDirection = direction, iframeStartTick = 0, iframeEndTick = duration.coerceAtLeast(1), supportParticle = supportParticle, releaseSoundId = releaseSoundId)

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
    const val BEAM = "beam"
    const val SUPPORT = "support"
    const val ROLL = "roll"
    const val DODGE = "dodge"
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
    @SerializedName("guard_roll_weight") var guardRollWeight: Int = 1,
    @SerializedName("guard_dodge_animation_id") var guardDodgeAnimationId: String = DEFAULT_DODGE_ANIMATION,
    @SerializedName("guard_dodge_animation_source") var guardDodgeAnimationSource: String = NpcBossAnimationSources.PLAYERLIKE,
    @SerializedName("guard_dodge_ticks") var guardDodgeTicks: Int = 12,
    @SerializedName("guard_dodge_iframe_ticks") var guardDodgeIframeTicks: Int = 10,
    @SerializedName("guard_dodge_distance") var guardDodgeDistance: Double = 2.4,
    @SerializedName("guard_dodge_direction") var guardDodgeDirection: String = "back",
    @SerializedName("guard_dodge_weight") var guardDodgeWeight: Int = 1,
    @SerializedName("guard_parry_weight") var guardParryWeight: Int = 1,
    @SerializedName("hover_height") var hoverHeight: Double = 0.0,
    @SerializedName("guard_min_ticks") var guardMinTicks: Int = 60,
    @SerializedName("guard_random_ticks") var guardRandomTicks: Int = 60,
    @SerializedName("guard_taunt_min_ticks") var guardTauntMinTicks: Int = 40,
    @SerializedName("guard_taunt_random_ticks") var guardTauntRandomTicks: Int = 40,
    @SerializedName("recovery_hits_allowed") var recoveryHitsAllowed: Int = 1,
    @SerializedName("attack_phase_damage_multiplier") var attackPhaseDamageMultiplier: Double = 0.65,
    @SerializedName("attack_windup_damage_multiplier") var attackWindupDamageMultiplier: Double = 0.0,
    @SerializedName("attack_active_damage_multiplier") var attackActiveDamageMultiplier: Double = 0.25,
    @SerializedName("attack_late_damage_multiplier") var attackLateDamageMultiplier: Double = 0.5,
    @SerializedName("attack_windup_pressure_multiplier") var attackWindupPressureMultiplier: Double = 1.5,
    @SerializedName("attack_active_pressure_multiplier") var attackActivePressureMultiplier: Double = 1.25,
    @SerializedName("anti_spam_pressure_threshold") var antiSpamPressureThreshold: Double = 3.25,
    @SerializedName("anti_spam_reactive_guard_cooldown_ticks") var antiSpamReactiveGuardCooldownTicks: Int = 70,
    @SerializedName("parry_spell_id") var parrySpellId: String = "",
    @SerializedName("parry_particle") var parryParticle: String = "",
    @SerializedName("parry_sound_id") var parrySoundId: String = "",
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
        guardRollWeight = guardRollWeight.coerceIn(0, 20)
        guardDodgeAnimationId = cleanBossPlayerlikeAnimation(guardDodgeAnimationId, DEFAULT_DODGE_ANIMATION)
        guardDodgeAnimationSource = NpcBossAnimationSources.PLAYERLIKE
        guardDodgeTicks = guardDodgeTicks.coerceIn(1, 40)
        guardDodgeIframeTicks = guardDodgeIframeTicks.coerceIn(0, guardDodgeTicks)
        guardDodgeDistance = guardDodgeDistance.coerceIn(0.0, 8.0)
        guardDodgeDirection = cleanEvadeDirection(guardDodgeDirection)
        guardDodgeWeight = guardDodgeWeight.coerceIn(0, 20)
        guardParryWeight = guardParryWeight.coerceIn(0, 20)
        if (guardRollWeight + guardDodgeWeight + guardParryWeight <= 0) guardParryWeight = 1
        hoverHeight = hoverHeight.coerceIn(0.0, 4.0)
        guardMinTicks = guardMinTicks.coerceIn(10, 20 * 30)
        guardRandomTicks = guardRandomTicks.coerceIn(0, 20 * 30)
        guardTauntMinTicks = guardTauntMinTicks.coerceIn(10, 20 * 30)
        guardTauntRandomTicks = guardTauntRandomTicks.coerceIn(0, 20 * 30)
        recoveryHitsAllowed = recoveryHitsAllowed.coerceIn(0, 10)
        attackPhaseDamageMultiplier = attackPhaseDamageMultiplier.coerceIn(0.1, 1.0)
        attackWindupDamageMultiplier = attackWindupDamageMultiplier.coerceIn(0.0, 1.0)
        attackActiveDamageMultiplier = attackActiveDamageMultiplier.coerceIn(0.0, 1.0)
        attackLateDamageMultiplier = attackLateDamageMultiplier.coerceIn(0.0, 1.0)
        attackWindupPressureMultiplier = attackWindupPressureMultiplier.coerceIn(0.0, 4.0)
        attackActivePressureMultiplier = attackActivePressureMultiplier.coerceIn(0.0, 4.0)
        antiSpamPressureThreshold = antiSpamPressureThreshold.coerceIn(1.0, 10.0)
        antiSpamReactiveGuardCooldownTicks = antiSpamReactiveGuardCooldownTicks.coerceIn(10, 20 * 10)
        parrySpellId = cleanAnimation(parrySpellId, "")
        parryParticle = cleanAnimation(parryParticle, "")
        parrySoundId = cleanAnimation(parrySoundId, "")
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
    @SerializedName("fire_ticks") var fireTicks: Int = 0,
    @SerializedName("hazard_radius") var hazardRadius: Double = 0.0,
    @SerializedName("hazard_ticks") var hazardTicks: Int = 0,
    @SerializedName("hazard_interval_ticks") var hazardIntervalTicks: Int = 10,
    @SerializedName("hazard_damage") var hazardDamage: Double = 0.0,
    @SerializedName("hazard_particle") var hazardParticle: String = "",
    @SerializedName("cast_particle") var castParticle: String = "",
    @SerializedName("release_particle") var releaseParticle: String = "",
    @SerializedName("support_particle") var supportParticle: String = "",
    @SerializedName("cast_sound_id") var castSoundId: String = "",
    @SerializedName("release_sound_id") var releaseSoundId: String = "",
    @SerializedName("impact_sound_id") var impactSoundId: String = "",
    @SerializedName("self_heal_amount") var selfHealAmount: Double = 0.0,
    @SerializedName("self_heal_cap_health_ratio") var selfHealCapHealthRatio: Double = 1.0,
    @SerializedName("self_heal_max_uses_per_phase") var selfHealMaxUsesPerPhase: Int = 0,
    @SerializedName("absorption_amount") var absorptionAmount: Double = 0.0,
    @SerializedName("absorption_ticks") var absorptionTicks: Int = 0,
    @SerializedName("min_phase_index") var minPhaseIndex: Int = 0,
    @SerializedName("max_phase_index") var maxPhaseIndex: Int = 99,
) {
    fun normalized(baseDamage: Double): NpcBossMoveDefinition = apply {
        id = NpcBossMovesets.normalizeId(id)
        kind = when (kind.trim().lowercase()) {
            NpcBossMoveKinds.AREA, "spell" -> NpcBossMoveKinds.AREA
            NpcBossMoveKinds.PROJECTILE, "ranged", "arrow", "bow" -> NpcBossMoveKinds.PROJECTILE
            NpcBossMoveKinds.BEAM, "ray", "channel", "channeled" -> NpcBossMoveKinds.BEAM
            NpcBossMoveKinds.SUPPORT, "heal", "shield", "buff" -> NpcBossMoveKinds.SUPPORT
            NpcBossMoveKinds.DODGE, "blink", "teleport" -> NpcBossMoveKinds.DODGE
            NpcBossMoveKinds.ROLL, "evade" -> NpcBossMoveKinds.ROLL
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
        if (kind == NpcBossMoveKinds.BEAM && animationId == NpcBossMovesetDefinition.DEFAULT_COUNTER_ANIMATION) {
            animationId = "spell_engine:two_handed_channeling"
        }
        animationSource = NpcBossAnimationSources.PLAYERLIKE
        releaseAnimationId = releaseAnimationId.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
            .ifBlank {
                when (kind) {
                    NpcBossMoveKinds.PROJECTILE -> "spell_engine:archery_release"
                    NpcBossMoveKinds.BEAM -> "spell_engine:one_handed_projectile_release"
                    else -> animationId
                }
            }
        releaseAnimationSource = NpcBossAnimationSources.PLAYERLIKE
        spellId = spellId.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
        durationTicks = durationTicks.coerceIn(1, 20 * 10)
        hitTicks = hitTicks.map { tick -> tick.coerceIn(0, durationTicks) }.distinct().sorted().toMutableList()
        if (kind != NpcBossMoveKinds.ROLL && kind != NpcBossMoveKinds.DODGE && hitTicks.isEmpty()) hitTicks = mutableListOf((durationTicks / 2).coerceAtLeast(1))
        if (kind == NpcBossMoveKinds.ROLL || kind == NpcBossMoveKinds.DODGE) hitTicks = mutableListOf()
        cooldownTicks = cooldownTicks.coerceIn(0, 20 * 60)
        recoveryTicks = recoveryTicks.coerceIn(0, 20 * 10)
        damage = if (kind == NpcBossMoveKinds.ROLL || kind == NpcBossMoveKinds.DODGE || kind == NpcBossMoveKinds.SUPPORT) 0.0 else damage.takeIf { it > 0.0 }?.coerceIn(0.0, 1000.0) ?: baseDamage
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
        fireTicks = fireTicks.coerceIn(0, 20 * 20)
        hazardRadius = hazardRadius.coerceIn(0.0, 16.0)
        hazardTicks = hazardTicks.coerceIn(0, 20 * 20)
        hazardIntervalTicks = hazardIntervalTicks.coerceIn(4, 20 * 10)
        hazardDamage = hazardDamage.coerceIn(0.0, 1000.0)
        hazardParticle = cleanParticleId(hazardParticle, "")
        castParticle = cleanParticleId(castParticle, "")
        releaseParticle = cleanParticleId(releaseParticle, "")
        supportParticle = cleanParticleId(supportParticle, "")
        castSoundId = cleanParticleId(castSoundId, "")
        releaseSoundId = cleanParticleId(releaseSoundId, "")
        impactSoundId = cleanParticleId(impactSoundId, "")
        selfHealAmount = selfHealAmount.coerceIn(0.0, 1000.0)
        selfHealCapHealthRatio = selfHealCapHealthRatio.coerceIn(0.0, 1.0)
        selfHealMaxUsesPerPhase = selfHealMaxUsesPerPhase.coerceIn(0, 20)
        absorptionAmount = absorptionAmount.coerceIn(0.0, 1000.0)
        absorptionTicks = absorptionTicks.coerceIn(0, 20 * 60)
        minPhaseIndex = minPhaseIndex.coerceIn(0, 99)
        maxPhaseIndex = maxPhaseIndex.coerceIn(minPhaseIndex, 99)
    }

    private fun cleanParticleId(value: String, fallback: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9_.:/-]+"), "_")
        .trim('_')
        .ifBlank { fallback }
}
