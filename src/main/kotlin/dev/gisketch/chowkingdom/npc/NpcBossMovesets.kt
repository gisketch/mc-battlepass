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
        val loaded = Files.list(root).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) && path.extension.equals("toml", ignoreCase = true) }
                .map { path ->
                    TomlConfigIO.read(path, NpcBossMovesetDefinition::class.java, { NpcBossMovesetDefinition(id = path.nameWithoutExtension) })
                        .also { definition -> if (definition.id.isBlank()) definition.id = path.nameWithoutExtension }
                        .normalized()
                }
                .toList()
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

    private fun defaultMovesets(): List<NpcBossMovesetDefinition> = listOf(
        defaultWarrior(),
        defaultRogue(),
        defaultWizard(),
        defaultWitcher(),
    )

    private fun defaultWarrior(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "warrior",
        displayName = "Warrior",
        health = 90.0,
        damage = 4.0,
        moves = mutableListOf(
            melee("fast_slash", "bettercombat:one_handed_slash_horizontal_right", duration = 18, hitTick = 7, damage = 5.0, range = 2.8, arc = 100.0, cooldown = 16, recovery = 12, weight = 6),
            melee("stab", "bettercombat:one_handed_stab", duration = 17, hitTick = 6, damage = 6.0, range = 3.2, arc = 55.0, cooldown = 22, recovery = 14, weight = 4),
            area("slam", "bettercombat:one_handed_slam", duration = 26, hitTick = 13, damage = 8.0, radius = 2.4, cooldown = 58, recovery = 18, weight = 2),
            area("battle_shout", "spell_engine:one_handed_shout_release", duration = 20, hitTick = 10, damage = 3.0, radius = 4.0, cooldown = 90, recovery = 14, weight = 2, spellId = "spell_engine:shout"),
            roll("combat_roll_backstep", "combat_roll:roll", duration = 14, cooldown = 70, distance = 3.0, direction = "back", weight = 2),
        ),
    )

    private fun defaultRogue(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "rogue",
        displayName = "Rogue",
        health = 70.0,
        damage = 3.5,
        moves = mutableListOf(
            melee("cross_slash", "bettercombat:dual_handed_slash_cross", duration = 17, hitTick = 7, damage = 4.5, range = 2.6, arc = 95.0, cooldown = 14, recovery = 10, weight = 5),
            melee("dagger_stab", "bettercombat:dual_handed_stab", duration = 15, hitTick = 6, damage = 5.5, range = 2.9, arc = 45.0, cooldown = 18, recovery = 11, weight = 4),
            melee("uncross", "bettercombat:dual_handed_slash_uncross", duration = 18, hitTick = 8, damage = 4.0, range = 2.8, arc = 110.0, cooldown = 18, recovery = 12, weight = 3),
            roll("dodge", "spell_engine:dodge", duration = 12, cooldown = 44, distance = 3.2, direction = "side", weight = 3),
        ),
    )

    private fun defaultWizard(): NpcBossMovesetDefinition = NpcBossMovesetDefinition(
        id = "wizard",
        displayName = "Wizard",
        health = 65.0,
        damage = 3.0,
        attackStartDistance = 5.5,
        moves = mutableListOf(
            area("projectile_release", "spell_engine:one_handed_projectile_release", duration = 22, hitTick = 12, damage = 5.0, radius = 1.4, cooldown = 34, recovery = 14, weight = 5, min = 1.5, max = 6.0, spellId = "spell_engine:projectile"),
            area("ground_area", "spell_engine:one_handed_area_release_ground_left_to_right", duration = 27, hitTick = 15, damage = 7.0, radius = 3.4, cooldown = 64, recovery = 18, weight = 3, min = 0.0, max = 5.2, spellId = "spell_engine:ground_area"),
            area("weapon_cleave", "spell_engine:weapon_cleave", duration = 24, hitTick = 11, damage = 4.5, radius = 2.4, cooldown = 42, recovery = 14, weight = 2, min = 0.0, max = 3.6),
            roll("blink_dodge", "spell_engine:dodge", duration = 12, cooldown = 58, distance = 3.0, direction = "back", weight = 2),
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

    private fun roll(id: String, animationId: String, duration: Int, cooldown: Int, distance: Double, direction: String, weight: Int): NpcBossMoveDefinition =
        NpcBossMoveDefinition(id = id, kind = NpcBossMoveKinds.ROLL, animationId = animationId, durationTicks = duration, hitTicks = mutableListOf(), damage = 0.0, cooldownTicks = cooldown, recoveryTicks = 0, weight = weight, minDistance = 0.0, maxDistance = 3.5, rollDistance = distance, rollDirection = direction, iframeStartTick = 0, iframeEndTick = duration.coerceAtLeast(1))
}

object NpcBossMoveKinds {
    const val MELEE = "melee"
    const val AREA = "area"
    const val ROLL = "roll"
}

class NpcBossMovesetDefinition(
    var id: String = "warrior",
    @SerializedName("display_name") var displayName: String = "Warrior",
    var health: Double = 80.0,
    var damage: Double = 4.0,
    @SerializedName("attack_start_distance") var attackStartDistance: Double = 3.2,
    @SerializedName("approach_animation_id") var approachAnimationId: String = "running_sword",
    @SerializedName("approach_animation_source") var approachAnimationSource: String = NpcBossAnimationSources.GECKO,
    @SerializedName("strafe_animation_id") var strafeAnimationId: String = "running_sword",
    @SerializedName("strafe_animation_source") var strafeAnimationSource: String = NpcBossAnimationSources.GECKO,
    @SerializedName("guard_animation_id") var guardAnimationId: String = "guard",
    @SerializedName("guard_animation_source") var guardAnimationSource: String = NpcBossAnimationSources.GECKO,
    @SerializedName("parry_animation_id") var parryAnimationId: String = "parry",
    @SerializedName("parry_animation_source") var parryAnimationSource: String = NpcBossAnimationSources.GECKO,
    @SerializedName("hurt_animation_id") var hurtAnimationId: String = "hurt",
    @SerializedName("hurt_animation_source") var hurtAnimationSource: String = NpcBossAnimationSources.GECKO,
    @SerializedName("guard_react_ticks") var guardReactTicks: Int = 6,
    @SerializedName("guard_min_ticks") var guardMinTicks: Int = 60,
    @SerializedName("guard_random_ticks") var guardRandomTicks: Int = 60,
    @SerializedName("guard_taunt_min_ticks") var guardTauntMinTicks: Int = 40,
    @SerializedName("guard_taunt_random_ticks") var guardTauntRandomTicks: Int = 40,
    @SerializedName("recovery_hits_allowed") var recoveryHitsAllowed: Int = 1,
    @SerializedName("parry_damage") var parryDamage: Double = 0.0,
    @SerializedName("parry_knockback") var parryKnockback: Double = 0.6,
    var moves: MutableList<NpcBossMoveDefinition> = mutableListOf(),
    var balloons: NpcBossBalloonDefinition = NpcBossBalloonDefinition(),
) {
    fun normalized(): NpcBossMovesetDefinition = apply {
        id = NpcBossMovesets.normalizeId(id).ifBlank { "warrior" }
        displayName = displayName.trim().ifBlank { id.replace('_', ' ').replaceFirstChar(Char::titlecase) }
        health = health.coerceIn(1.0, 10000.0)
        damage = damage.coerceIn(0.0, 1000.0)
        attackStartDistance = attackStartDistance.coerceIn(1.0, 16.0)
        approachAnimationId = cleanAnimation(approachAnimationId, "running_sword")
        approachAnimationSource = cleanSource(approachAnimationSource)
        strafeAnimationId = cleanAnimation(strafeAnimationId, approachAnimationId)
        strafeAnimationSource = cleanSource(strafeAnimationSource)
        guardAnimationId = cleanAnimation(guardAnimationId, "guard")
        guardAnimationSource = cleanSource(guardAnimationSource)
        parryAnimationId = cleanAnimation(parryAnimationId, "parry")
        parryAnimationSource = cleanSource(parryAnimationSource)
        hurtAnimationId = cleanAnimation(hurtAnimationId, "hurt")
        hurtAnimationSource = cleanSource(hurtAnimationSource)
        guardReactTicks = guardReactTicks.coerceIn(1, 40)
        guardMinTicks = guardMinTicks.coerceIn(10, 20 * 30)
        guardRandomTicks = guardRandomTicks.coerceIn(0, 20 * 30)
        guardTauntMinTicks = guardTauntMinTicks.coerceIn(10, 20 * 30)
        guardTauntRandomTicks = guardTauntRandomTicks.coerceIn(0, 20 * 30)
        recoveryHitsAllowed = recoveryHitsAllowed.coerceIn(0, 10)
        parryDamage = parryDamage.coerceIn(0.0, 1000.0)
        parryKnockback = parryKnockback.coerceIn(0.0, 4.0)
        moves = moves.map { move -> move.normalized(damage) }.filter { move -> move.id.isNotBlank() }.toMutableList()
        if (moves.isEmpty()) moves = mutableListOf(NpcBossMoveDefinition().normalized(damage))
        balloons = balloons.normalized()
    }

    private fun cleanAnimation(value: String, fallback: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9_.:/-]+"), "_")
        .trim('_')
        .ifBlank { fallback }

    private fun cleanSource(value: String): String = when (value.trim().lowercase()) {
        NpcBossAnimationSources.PLAYERLIKE, "player_animator", "playeranimator" -> NpcBossAnimationSources.PLAYERLIKE
        else -> NpcBossAnimationSources.GECKO
    }
}

class NpcBossMoveDefinition(
    var id: String = "slash",
    var kind: String = NpcBossMoveKinds.MELEE,
    @SerializedName("animation_id") var animationId: String = "bettercombat:one_handed_slash_horizontal_right",
    @SerializedName("animation_source") var animationSource: String = NpcBossAnimationSources.PLAYERLIKE,
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
) {
    fun normalized(baseDamage: Double): NpcBossMoveDefinition = apply {
        id = NpcBossMovesets.normalizeId(id)
        kind = when (kind.trim().lowercase()) {
            NpcBossMoveKinds.AREA, "spell" -> NpcBossMoveKinds.AREA
            NpcBossMoveKinds.ROLL, "dodge", "evade" -> NpcBossMoveKinds.ROLL
            else -> NpcBossMoveKinds.MELEE
        }
        animationId = animationId.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
            .ifBlank { "bettercombat:one_handed_slash_horizontal_right" }
        animationSource = when (animationSource.trim().lowercase()) {
            NpcBossAnimationSources.GECKO -> NpcBossAnimationSources.GECKO
            else -> NpcBossAnimationSources.PLAYERLIKE
        }
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
    }
}
