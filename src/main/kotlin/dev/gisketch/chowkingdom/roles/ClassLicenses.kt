package dev.gisketch.chowkingdom.roles

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object ClassLicenses {
    const val STARTER_CLASS_UNLOCK_COST: Long = 25_000L
    const val UPGRADE_CLASS_UNLOCK_COST: Long = 50_000L
    const val STARTER_CLASS_CHANGE_COST: Long = 50_000L
    const val UPGRADE_CLASS_CHANGE_COST: Long = 100_000L

    val fallbackStarterUnlockOverallLevels = listOf(1, 100, 300, 500, 1000)
    val fallbackUpgradeUnlockOverallLevels = listOf(75, 150, 250, 350, 450, 550, 650, 800, 1000)

    fun starterLicenses(player: ServerPlayer): Int = maxOf(RoleStore.starterLicenses(player), levelStarterLicenses(JobLevels.overallLevel(player)))

    fun upgradeLicenses(player: ServerPlayer): Int = maxOf(RoleStore.upgradeLicenses(player), levelUpgradeLicenses(JobLevels.overallLevel(player)))

    fun canUnlock(player: ServerPlayer, role: RoleDefinition): ClassLicenseResult {
        val failed = failedConditions(player, role)
        return if (failed.isEmpty()) ClassLicenseResult.Allowed else ClassLicenseResult.Denied(Component.literal(failed.joinToString(" ")))
    }

    fun failedConditions(player: ServerPlayer, role: RoleDefinition): List<String> {
        val record = RoleStore.role(player)
        if (role.id in record.unlockedClasses || role.id in record.activeClassIds) return emptyList()
        return if (RolesConfig.isStarterClass(role.id)) starterFailedConditions(player, record, role) else upgradeFailedConditions(player, record, role)
    }

    fun changeOffer(player: ServerPlayer, role: RoleDefinition): ClassChangeOffer? {
        val record = RoleStore.role(player)
        if (role.id in knownClassIds(record)) return null
        val starter = RolesConfig.isStarterClass(role.id)
        if (!starter && upgradePrerequisitesFailed(record, role)) return null
        val used = if (starter) unlockedStarterCount(record) else unlockedUpgradeCount(record)
        val limit = if (starter) starterLicenses(player) else upgradeLicenses(player)
        if (used < limit) return null
        val candidates = knownClassIds(record)
            .filter { classId -> classId != role.id && RolesConfig.isStarterClass(classId) == starter }
            .mapNotNull { classId -> RolesConfig.roleClass(classId) }
            .sortedBy { candidate -> candidate.displayName.ifBlank { candidate.id } }
            .map { candidate -> ClassChangeCandidate(candidate.id, candidate.displayName.ifBlank { candidate.id }) }
        if (candidates.isEmpty()) return null
        val cost = if (starter) STARTER_CLASS_CHANGE_COST else UPGRADE_CLASS_CHANGE_COST
        return ClassChangeOffer(cost, candidates)
    }

    fun invalidatedUpgradeClassesAfterChange(player: ServerPlayer, oldClassId: String, newClassId: String): List<ClassChangeCandidate> {
        val record = RoleStore.role(player)
        if (!RolesConfig.isStarterClass(oldClassId) || !RolesConfig.isStarterClass(newClassId)) return emptyList()
        val knownAfter = (knownClassIds(record) - oldClassId + newClassId).filter(String::isNotBlank).toSet()
        return knownAfter
            .filterNot(RolesConfig::isStarterClass)
            .mapNotNull(RolesConfig::roleClass)
            .filter { role -> upgradePrerequisitesFailed(knownAfter, role) }
            .sortedBy { role -> role.displayName.ifBlank { role.id } }
            .map { role -> ClassChangeCandidate(role.id, role.displayName.ifBlank { role.id }) }
    }

    fun starterUnlockOverallLevels(): List<Int> = RolesConfig.classLicenses().starterLicenseUnlockOverallLevels
        .filter { level -> level > 0 }
        .distinct()
        .sorted()
        .ifEmpty { fallbackStarterUnlockOverallLevels }

    fun upgradeUnlockOverallLevels(): List<Int> = RolesConfig.classLicenses().upgradeLicenseUnlockOverallLevels
        .filter { level -> level > 0 }
        .distinct()
        .sorted()
        .ifEmpty { fallbackUpgradeUnlockOverallLevels }

    private fun starterFailedConditions(player: ServerPlayer, record: PlayerRoleRecord, role: RoleDefinition): List<String> {
        val used = unlockedStarterCount(record)
        val limit = starterLicenses(player)
        if (used < limit) return emptyList()
        val nextLevel = nextStarterLicenseLevel(JobLevels.overallLevel(player))
        val next = if (nextLevel != null) "Next starter license unlocks at overall level $nextLevel." else "No more starter license unlocks are configured."
        return listOf("Need another starter class license for ${role.displayName.ifBlank { role.id }} ($used/$limit used). $next")
    }

    private fun upgradeFailedConditions(player: ServerPlayer, record: PlayerRoleRecord, role: RoleDefinition): List<String> {
        val failed = mutableListOf<String>()
        val prerequisites = RolesConfig.starterClassIds(role)
        if (upgradePrerequisitesFailed(knownClassIds(record), role)) {
            failed += "Need one prerequisite starter class for ${role.displayName.ifBlank { role.id }}: ${prerequisites.joinToString(", ")}."
        }
        val used = unlockedUpgradeCount(record)
        val limit = upgradeLicenses(player)
        if (used >= limit) {
            val nextLevel = nextUpgradeLicenseLevel(JobLevels.overallLevel(player))
            val next = if (nextLevel != null) "Next upgrade license unlocks at overall level $nextLevel." else "No more upgrade license unlocks are configured."
            failed += "Need another upgrade class license for ${role.displayName.ifBlank { role.id }} ($used/$limit used). $next"
        }
        return failed
    }

    private fun levelStarterLicenses(overallLevel: Int): Int = starterUnlockOverallLevels().count { level -> overallLevel >= level }

    private fun levelUpgradeLicenses(overallLevel: Int): Int = upgradeUnlockOverallLevels().count { level -> overallLevel >= level }

    private fun nextStarterLicenseLevel(overallLevel: Int): Int? = starterUnlockOverallLevels().firstOrNull { level -> overallLevel < level }

    private fun nextUpgradeLicenseLevel(overallLevel: Int): Int? = upgradeUnlockOverallLevels().firstOrNull { level -> overallLevel < level }

    private fun unlockedStarterCount(record: PlayerRoleRecord): Int = knownClassIds(record).count { classId -> RolesConfig.isStarterClass(classId) }

    private fun unlockedUpgradeCount(record: PlayerRoleRecord): Int = knownClassIds(record).count { classId -> !RolesConfig.isStarterClass(classId) }

    private fun knownClassIds(record: PlayerRoleRecord): Set<String> = (record.unlockedClasses + record.activeClassIds + setOf(record.classId)).filter(String::isNotBlank).toSet()

    private fun upgradePrerequisitesFailed(record: PlayerRoleRecord, role: RoleDefinition): Boolean = upgradePrerequisitesFailed(knownClassIds(record), role)

    private fun upgradePrerequisitesFailed(knownClasses: Set<String>, role: RoleDefinition): Boolean {
        val prerequisites = RolesConfig.starterClassIds(role)
        if (prerequisites.isEmpty()) return false
        return prerequisites.none { starterId -> starterId in knownClasses }
    }
}

data class ClassChangeOffer(val cost: Long, val candidates: List<ClassChangeCandidate>)

data class ClassChangeCandidate(val classId: String, val displayName: String)

sealed class ClassLicenseResult {
    data object Allowed : ClassLicenseResult()
    data class Denied(val reason: Component) : ClassLicenseResult()
}
