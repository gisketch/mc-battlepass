package dev.gisketch.chowkingdom.roles

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object ClassLicenses {
    val fallbackStarterUnlockOverallLevels = listOf(1, 100, 300, 500, 1000)
    val fallbackUpgradeUnlockOverallLevels = listOf(75, 150, 250, 350, 450, 550, 650, 800, 1000)

    fun starterLicenses(player: ServerPlayer): Int = maxOf(RoleStore.starterLicenses(player), levelStarterLicenses(JobLevels.overallLevel(player)))

    fun upgradeLicenses(player: ServerPlayer): Int = maxOf(RoleStore.upgradeLicenses(player), levelUpgradeLicenses(JobLevels.overallLevel(player)))

    fun canUnlock(player: ServerPlayer, role: RoleDefinition): ClassLicenseResult {
        val record = RoleStore.role(player)
        if (role.id in record.unlockedClasses || role.id in record.activeClassIds) return ClassLicenseResult.Allowed
        return if (RolesConfig.isStarterClass(role.id)) canUnlockStarter(player, record, role) else canUnlockUpgrade(player, record, role)
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

    private fun canUnlockStarter(player: ServerPlayer, record: PlayerRoleRecord, role: RoleDefinition): ClassLicenseResult {
        val used = unlockedStarterCount(record)
        val limit = starterLicenses(player)
        return if (used < limit) {
            ClassLicenseResult.Allowed
        } else {
            ClassLicenseResult.Denied(Component.literal("${player.gameProfile.name} needs another starter class license for ${role.displayName.ifBlank { role.id }} ($used/$limit used)."))
        }
    }

    private fun canUnlockUpgrade(player: ServerPlayer, record: PlayerRoleRecord, role: RoleDefinition): ClassLicenseResult {
        val prerequisites = RolesConfig.starterClassIds(role)
        val knownClasses = record.unlockedClasses + record.activeClassIds + setOf(record.classId).filter(String::isNotBlank)
        if (prerequisites.isNotEmpty() && prerequisites.none { starterId -> starterId in knownClasses }) {
            return ClassLicenseResult.Denied(Component.literal("${role.displayName.ifBlank { role.id }} needs one of: ${prerequisites.joinToString(", ")}."))
        }
        val used = unlockedUpgradeCount(record)
        val limit = upgradeLicenses(player)
        return if (used < limit) {
            ClassLicenseResult.Allowed
        } else {
            ClassLicenseResult.Denied(Component.literal("${player.gameProfile.name} needs another upgrade class license for ${role.displayName.ifBlank { role.id }} ($used/$limit used)."))
        }
    }

    private fun levelStarterLicenses(overallLevel: Int): Int = starterUnlockOverallLevels().count { level -> overallLevel >= level }

    private fun levelUpgradeLicenses(overallLevel: Int): Int = upgradeUnlockOverallLevels().count { level -> overallLevel >= level }

    private fun unlockedStarterCount(record: PlayerRoleRecord): Int = knownClassIds(record).count { classId -> RolesConfig.isStarterClass(classId) }

    private fun unlockedUpgradeCount(record: PlayerRoleRecord): Int = knownClassIds(record).count { classId -> !RolesConfig.isStarterClass(classId) }

    private fun knownClassIds(record: PlayerRoleRecord): Set<String> = (record.unlockedClasses + record.activeClassIds + setOf(record.classId)).filter(String::isNotBlank).toSet()
}

sealed class ClassLicenseResult {
    data object Allowed : ClassLicenseResult()
    data class Denied(val reason: Component) : ClassLicenseResult()
}
