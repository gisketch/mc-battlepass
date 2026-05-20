package dev.gisketch.chowkingdom.bosses

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object BossEventsStore {
    private var data = BossEventsWorldState()
    private var loaded = false

    private val file: Path
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            val root = if (server != null) server.getWorldPath(LevelResource.ROOT).resolve("data") else FMLPaths.CONFIGDIR.get()
            val extension = if (server != null) "json" else "toml"
            return root.resolve(ChowKingdomMod.MOD_ID).resolve("bosses").resolve("state.$extension")
        }

    fun load() {
        file.parent.createDirectories()
        data = if (file.exists()) TomlConfigIO.read(file, BossEventsWorldState::class.java, ::BossEventsWorldState) else BossEventsWorldState()
        loaded = true
    }

    fun unlocked(bossId: String): Boolean {
        if (!loaded) load()
        return bossId in data.unlockedBossIds
    }

    fun introduced(bossId: String): Boolean {
        if (!loaded) load()
        return bossId in data.introducedBossIds
    }

    fun introduce(bossId: String): Boolean {
        if (!loaded) load()
        if (!data.introducedBossIds.add(bossId)) return false
        save()
        return true
    }

    fun unlock(bossId: String): Boolean {
        if (!loaded) load()
        if (!data.unlockedBossIds.add(bossId)) return false
        save()
        return true
    }

    fun clearUnlocked(bossId: String) {
        if (!loaded) load()
        data.unlockedBossIds.remove(bossId)
        data.introducedBossIds.remove(bossId)
        save()
    }

    fun clearedByAny(bossId: String): Boolean {
        if (!loaded) load()
        return data.bosses[bossId]?.clearedBy.orEmpty().isNotEmpty()
    }

    fun hasCredit(player: ServerPlayer, bossId: String): Boolean {
        if (!loaded) load()
        return player.stringUUID in data.bosses[bossId]?.credit.orEmpty()
    }

    fun hasClaimed(player: ServerPlayer, bossId: String): Boolean {
        if (!loaded) load()
        return player.stringUUID in data.bosses[bossId]?.claimed.orEmpty()
    }

    fun creditCount(bossId: String): Int {
        if (!loaded) load()
        return data.bosses[bossId]?.credit?.size ?: 0
    }

    fun clearedOrCreditedEnough(entry: BossEventEntry): Boolean {
        if (!loaded) load()
        val boss = data.bosses[entry.id] ?: return false
        return boss.clearedBy.isNotEmpty() || boss.credit.size >= entry.requiredPlayers
    }

    fun claim(player: ServerPlayer, bossId: String): Boolean {
        if (!loaded) load()
        val boss = data.bosses.getOrPut(bossId) { BossEventBossState() }
        if (player.stringUUID !in boss.credit || player.stringUUID in boss.claimed) return false
        boss.claimed.add(player.stringUUID)
        save()
        return true
    }

    fun setCredit(player: ServerPlayer, bossId: String, value: Boolean) {
        if (!loaded) load()
        val boss = data.bosses.getOrPut(bossId) { BossEventBossState() }
        if (value) boss.credit.add(player.stringUUID) else {
            boss.credit.remove(player.stringUUID)
            boss.claimed.remove(player.stringUUID)
        }
        save()
    }

    fun recordClear(entry: BossEventEntry, contributors: List<ServerPlayer>): Boolean {
        if (!loaded) load()
        val boss = data.bosses.getOrPut(entry.id) { BossEventBossState() }
        val firstClear = boss.clearedBy.isEmpty()
        boss.clearedAt = Instant.now().toString()
        contributors.forEach { player ->
            boss.credit.add(player.stringUUID)
            boss.clearedBy[player.stringUUID] = player.gameProfile.name
        }
        save()
        return firstClear
    }

    fun claimableBosses(player: ServerPlayer): List<BossEventEntry> =
        BossEventsConfig.entries().filter { entry -> hasCredit(player, entry.id) && !hasClaimed(player, entry.id) }

    fun statusLines(): List<String> {
        if (!loaded) load()
        return BossEventsConfig.entries().map { entry ->
            val boss = data.bosses[entry.id]
            val state = when {
                entry.id !in data.unlockedBossIds -> "locked"
                boss?.clearedBy.orEmpty().isNotEmpty() || (boss?.credit?.size ?: 0) >= entry.requiredPlayers -> "cleared"
                else -> "unlocked"
            }
            "${entry.order}. ${entry.id} $state introduced=${entry.id in data.introducedBossIds} threshold=${entry.thresholdChowcoins} credit=${boss?.credit?.size ?: 0} claimed=${boss?.claimed?.size ?: 0}"
        }
    }

    fun resetBoss(bossId: String) {
        if (!loaded) load()
        data.unlockedBossIds.remove(bossId)
        data.introducedBossIds.remove(bossId)
        data.bosses.remove(bossId)
        save()
    }

    private fun save() {
        TomlConfigIO.write(file, data)
    }
}

class BossEventsWorldState(
    var unlockedBossIds: MutableSet<String> = linkedSetOf(),
    var introducedBossIds: MutableSet<String> = linkedSetOf(),
    var bosses: MutableMap<String, BossEventBossState> = linkedMapOf(),
)

class BossEventBossState(
    var clearedAt: String = "",
    var clearedBy: MutableMap<String, String> = linkedMapOf(),
    var credit: MutableSet<String> = linkedSetOf(),
    var claimed: MutableSet<String> = linkedSetOf(),
)

data class BossFightRecord(
    val bossId: String,
    val entityUuid: UUID,
    val startedAtTick: Long,
    val damagedBy: MutableMap<UUID, Long> = linkedMapOf(),
)
