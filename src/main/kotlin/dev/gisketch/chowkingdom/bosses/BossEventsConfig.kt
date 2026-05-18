package dev.gisketch.chowkingdom.bosses

import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.config.TomlConfigIO
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object BossEventsConfig {
    private var config = BossEventsDefinition()
    private var settings = BossEventsSettings()

    private val eventsFile: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("bosses").resolve("events").resolve("server_bosses.toml")

    private val settingsFile: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("bosses").resolve("settings.toml")

    fun load() {
        eventsFile.parent.createDirectories()
        settingsFile.parent.createDirectories()
        if (!eventsFile.exists()) TomlConfigIO.write(eventsFile, defaultEvents())
        if (!settingsFile.exists()) TomlConfigIO.write(settingsFile, BossEventsSettings())
        config = TomlConfigIO.read(eventsFile, BossEventsDefinition::class.java, ::defaultEvents).normalized()
        settings = TomlConfigIO.read(settingsFile, BossEventsSettings::class.java, ::BossEventsSettings).normalized()
    }

    fun settings(): BossEventsSettings = settings

    fun entries(): List<BossEventEntry> = config.entries

    fun entry(id: String): BossEventEntry? {
        val clean = id.trim().lowercase()
        return config.entries.firstOrNull { it.id == clean || it.entityId == clean }
    }

    fun entryForEntity(entityId: String): BossEventEntry? = config.entries.firstOrNull { it.entityId == entityId.trim().lowercase() }

    fun firstUnlockedUncleared(cleared: (String) -> Boolean, unlocked: (String) -> Boolean): BossEventEntry? =
        config.entries.firstOrNull { entry -> unlocked(entry.id) && !cleared(entry.id) }

    fun nextAfter(entry: BossEventEntry): BossEventEntry? = config.entries.firstOrNull { it.order > entry.order }

    fun setRequiredPlayers(target: String, requiredPlayers: Int): List<BossEventEntry> {
        val clean = target.trim().lowercase()
        val value = requiredPlayers.coerceIn(1, 50)
        val targets = if (clean == "all") config.entries else listOfNotNull(entry(clean))
        if (targets.isEmpty()) return emptyList()
        targets.forEach { entry -> entry.requiredPlayers = value }
        config = config.normalized()
        TomlConfigIO.write(eventsFile, config)
        return targets
    }

    private fun defaultEvents(): BossEventsDefinition {
        val thresholds = listOf(50_000L, 100_000L, 175_000L, 275_000L, 400_000L, 550_000L, 725_000L, 925_000L, 1_150_000L, 1_400_000L, 1_700_000L, 2_050_000L, 2_450_000L, 2_900_000L, 3_400_000L, 4_000_000L, 4_700_000L, 5_500_000L, 6_500_000L)
        val bosses = listOf(
            Triple("minecells:coniunctivius", "Coniunctivius", "First dungeon skill-check boss"),
            Triple("block_factorys_bosses:sandworm", "Sandworm", "Early overworld boss"),
            Triple("block_factorys_bosses:yeti", "Yeti", "Early-mid cold biome boss"),
            Triple("cataclysm:netherite_monstrosity", "Netherite Monstrosity", "First true heavy boss"),
            Triple("fdbosses:chesed", "Chesed", "First Qliphoth awakening"),
            Triple("block_factorys_bosses:underworld_knight", "Underworld Knight", "Nether duel boss"),
            Triple("minecraft:wither", "Wither", "Vanilla power gate / Nether Star"),
            Triple("cataclysm:the_harbinger", "The Harbinger", "Wither/Nether Star progression boss"),
            Triple("block_factorys_bosses:infernal_dragon", "Infernal Dragon", "Big fire and nether escalation"),
            Triple("minecraft:ender_dragon", "Ender Dragon", "Main vanilla milestone"),
            Triple("cataclysm:ender_guardian", "Ender Guardian", "Post-End guardian fight"),
            Triple("fdbosses:malkuth", "Malkuth", "Second Qliphoth awakening"),
            Triple("block_factorys_bosses:kraken", "Kraken", "Ocean raid boss / side expedition"),
            Triple("cataclysm:ancient_remnant", "Ancient Remnant", "Desert ruins ancient boss"),
            Triple("cataclysm:the_leviathan", "The Leviathan", "Deep ocean Cataclysm boss"),
            Triple("cataclysm:scylla", "Scylla", "Late-game magic/electric boss"),
            Triple("cataclysm:maledictus", "Maledictus", "Endgame duel boss"),
            Triple("fdbosses:geburah", "Geburah", "Final Qliphoth breach"),
            Triple("cataclysm:ignis", "Ignis", "Final superboss / server capstone"),
        )
        return BossEventsDefinition(
            entries = bosses.mapIndexed { index, boss ->
                val order = index + 1
                val displayName = boss.second
                BossEventEntry(
                    id = boss.first,
                    order = order,
                    entityId = boss.first,
                    displayName = displayName,
                    description = boss.third,
                    lore = "Finn marked this contract as ${boss.third.lowercase()}.",
                    locationHint = locationHintFor(boss.first),
                    accessHint = accessHintFor(boss.first),
                    fightTips = fightTipsFor(boss.first),
                    thresholdChowcoins = thresholds[index],
                    iconItem = iconFor(boss.first),
                    requiredPlayers = 8,
                    finnUnlockAnnouncement = "Finn slams a contract on the board: $displayName is open. Shipments hit {total_shipped_chowcoins} Chowcoins. Adventure time.",
                    finnLockedWarning = "Finn has not opened the $displayName contract yet. The town needs {threshold} shipped Chowcoins first.",
                    finnContractDialog = "This contract is for {boss}. {location_hint} {access_hint} Bring a crew of {required_players}; I want everyone coming back with all their bones where they started.",
                    finnLockedContractDialog = "I can feel something moving past the edge of the map, but the town is not ready. Keep the shipping routes alive until we reach {threshold} Chowcoins, then I will pin the next contract.",
                    finnClaimDialog = "You helped clear $displayName, {player}. That was brave, loud, and exactly what the town needed. Claim your contract reward.",
                    finnClaimUnavailableDialog = "{boss} is still on the board. Scout it, gather the crew, and come back when the deed is done.",
                    finnClearBroadcast = "$displayName is down. Finn is counting the crew and readying rewards.",
                )
            }.toMutableList(),
        )
    }

    private fun locationHintFor(entityId: String): String = when {
        entityId == "minecraft:wither" -> "Build the Wither where the town agrees it is safe."
        entityId == "minecraft:ender_dragon" -> "The trail points toward the End."
        "nether" in entityId || "harbinger" in entityId || "ignis" in entityId -> "The heat around this one smells like Nether work."
        "leviathan" in entityId || "kraken" in entityId -> "Start looking near deep water and ocean routes."
        "yeti" in entityId -> "Cold biomes are the best place to start asking questions."
        "ancient_remnant" in entityId || "sandworm" in entityId -> "Dry ruins and desert roads are the first clue."
        else -> "Follow the mod's normal structure, summon, or arena path."
    }

    private fun accessHintFor(entityId: String): String = when {
        entityId.startsWith("fdbosses:") -> "Qliphoth bosses are configured directly because they are not in c:bosses."
        entityId.startsWith("cataclysm:") -> "Cataclysm bosses usually come from their own structures or summon path."
        entityId.startsWith("block_factorys_bosses:") -> "Bosses'Rise handles the native encounter rules."
        entityId.startsWith("minecells:") -> "Mine Cells handles the native dungeon route."
        else -> "Use the normal Minecraft progression for this boss."
    }

    private fun fightTipsFor(entityId: String): String = when {
        entityId == "minecraft:wither" -> "Bring blast protection, milk, ranged damage, and room to move."
        entityId == "minecraft:ender_dragon" -> "Break crystals first, watch the void, and keep slow fall or water ready."
        "leviathan" in entityId || "kraken" in entityId -> "Prepare water breathing, mobility, and ranged pressure."
        "ignis" in entityId || "infernal" in entityId -> "Fire resistance and clean spacing matter more than greed."
        "yeti" in entityId -> "Cold fights punish bad footing. Bring food, ranged backup, and escapes."
        else -> "Bring roles that can revive, pressure, and survive. Do not go alone."
    }

    private fun iconFor(entityId: String): String = when {
        entityId == "minecraft:wither" -> "minecraft:nether_star"
        entityId == "minecraft:ender_dragon" -> "minecraft:dragon_head"
        "nether" in entityId || "ignis" in entityId -> "minecraft:blaze_powder"
        "leviathan" in entityId || "kraken" in entityId -> "minecraft:heart_of_the_sea"
        else -> "minecraft:diamond_sword"
    }
}

class BossEventsDefinition(
    var entries: MutableList<BossEventEntry> = mutableListOf(),
) {
    fun normalized(): BossEventsDefinition = apply {
        entries = entries.mapIndexedNotNull { index, entry ->
            entry.normalized(index + 1).takeIf { it.id.isNotBlank() && it.entityId.isNotBlank() }
        }.distinctBy { it.id }.sortedBy { it.order }.toMutableList()
    }
}

class BossEventsSettings(
    var enabled: Boolean = true,
    @SerializedName("events_webhook_url") var eventsWebhookUrl: String = "",
    @SerializedName("finn_npc_id") var finnNpcId: String = "finn",
    @SerializedName("participation_radius") var participationRadius: Double = 96.0,
    @SerializedName("participation_window_ticks") var participationWindowTicks: Long = 20L * 60L * 10L,
    @SerializedName("locked_warning_cooldown_ticks") var lockedWarningCooldownTicks: Long = 20L * 30L,
) {
    fun normalized(): BossEventsSettings = apply {
        eventsWebhookUrl = eventsWebhookUrl.trim()
        finnNpcId = finnNpcId.trim().lowercase().ifBlank { "finn" }
        participationRadius = participationRadius.coerceIn(8.0, 256.0)
        participationWindowTicks = participationWindowTicks.coerceIn(20L * 30L, 20L * 60L * 30L)
        lockedWarningCooldownTicks = lockedWarningCooldownTicks.coerceIn(20L * 5L, 20L * 60L * 10L)
    }
}

class BossEventEntry(
    var id: String = "",
    var order: Int = 0,
    @SerializedName("entity_id") var entityId: String = "",
    @SerializedName("display_name") var displayName: String = "",
    var description: String = "",
    var lore: String = "",
    @SerializedName("location_hint") var locationHint: String = "",
    @SerializedName("access_hint") var accessHint: String = "",
    @SerializedName("fight_tips") var fightTips: String = "",
    @SerializedName("threshold_chowcoins") var thresholdChowcoins: Long = 0L,
    @SerializedName("icon_item") var iconItem: String = "minecraft:diamond_sword",
    @SerializedName("required_players") var requiredPlayers: Int = 8,
    @SerializedName("first_clear_xp") var firstClearXp: Int = 750,
    @SerializedName("first_clear_chowcoins") var firstClearChowcoins: Long = 2_500L,
    @SerializedName("helper_xp") var helperXp: Int = 500,
    @SerializedName("helper_chowcoins") var helperChowcoins: Long = 1_500L,
    @SerializedName("finn_unlock_announcement") var finnUnlockAnnouncement: String = "",
    @SerializedName("finn_locked_warning") var finnLockedWarning: String = "",
    @SerializedName("finn_contract_dialog") var finnContractDialog: String = "",
    @SerializedName("finn_locked_contract_dialog") var finnLockedContractDialog: String = "",
    @SerializedName("finn_claim_dialog") var finnClaimDialog: String = "",
    @SerializedName("finn_claim_unavailable_dialog") var finnClaimUnavailableDialog: String = "",
    @SerializedName("finn_clear_broadcast") var finnClearBroadcast: String = "",
) {
    fun normalized(fallbackOrder: Int): BossEventEntry = apply {
        id = cleanId(id)
        entityId = cleanId(entityId).ifBlank { id }
        order = order.takeIf { it > 0 } ?: fallbackOrder
        displayName = displayName.trim().ifBlank { id.substringAfter(':').replace('_', ' ').replaceFirstChar { it.titlecase() } }
        description = description.trim()
        lore = lore.trim().ifBlank { description }
        locationHint = locationHint.trim().ifBlank { "Finn is still collecting field notes for this contract." }
        accessHint = accessHint.trim().ifBlank { "Use the boss mod's native encounter path." }
        fightTips = fightTips.trim().ifBlank { "Bring a crew, food, revives, and an exit plan." }
        thresholdChowcoins = thresholdChowcoins.coerceAtLeast(0L)
        iconItem = cleanId(iconItem).ifBlank { "minecraft:diamond_sword" }
        requiredPlayers = requiredPlayers.coerceIn(1, 50)
        firstClearXp = firstClearXp.coerceAtLeast(0)
        firstClearChowcoins = firstClearChowcoins.coerceAtLeast(0L)
        helperXp = helperXp.coerceAtLeast(0)
        helperChowcoins = helperChowcoins.coerceAtLeast(0L)
        finnUnlockAnnouncement = finnUnlockAnnouncement.trim().ifBlank { "Finn opened a new boss contract: {boss}." }
        finnLockedWarning = finnLockedWarning.trim().ifBlank { "Finn has not opened {boss} yet. Ship more Chowcoins first." }
        finnContractDialog = finnContractDialog.trim().ifBlank { "This contract is for {boss}. {location_hint} {access_hint} Bring {required_players} credited players if you want me to count it." }
        finnLockedContractDialog = finnLockedContractDialog.trim().ifBlank { "I am still tracking the next threat. The town needs {threshold} shipped Chowcoins before I pin the contract." }
        finnClaimDialog = finnClaimDialog.trim().ifBlank { "You cleared {boss}, {player}. Claim your contract reward." }
        finnClaimUnavailableDialog = finnClaimUnavailableDialog.trim().ifBlank { "{boss} is not cleared for you yet. Finish the contract, then come back." }
        finnClearBroadcast = finnClearBroadcast.trim().ifBlank { "{boss} is defeated. Talk to Finn to claim." }
    }

    private fun cleanId(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
}
