package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID

internal object DrakeTamerPerks {
    private val presenceCooldownUntilTicks: MutableMap<UUID, Long> = linkedMapOf()
    private val openedTreasureBlocks: MutableSet<String> = linkedSetOf()

    fun onLivingDamage(player: ServerPlayer, event: LivingDamageEvent.Pre) {
        val reduction = RolePerks.configuredJobMaxBonusPercent(player, "protection_lite").coerceIn(0.0, 0.95)
        if (reduction <= 0.0) return
        event.newDamage = (event.newDamage * (1.0 - reduction).toFloat()).coerceAtLeast(0.0f)
    }

    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (RolePerks.jobPerks(player, "treasure_sense").isEmpty() || !isTreasureContainer(player, event.pos)) return
        val key = treasureKey(player, event.pos)
        if (!openedTreasureBlocks.add(key)) return
        if (player.random.nextDouble() >= TREASURE_SENSE_CHANCE) return
        grantTreasureSenseReward(player)
    }

    fun mountVelocityMultiplier(player: ServerPlayer, pokemonTypes: Set<String>): Double {
        if ("dragon" !in pokemonTypes) return 1.0
        return 1.0 + RolePerks.configuredJobMaxBonusPercent(player, "dragon_mount_velocity").coerceAtLeast(0.0)
    }

    fun onDragonMount(player: ServerPlayer, pokemonTypes: Set<String>) {
        if ("dragon" !in pokemonTypes || RolePerks.jobPerks(player, "draconic_presence").isEmpty()) return
        val now = player.level().gameTime
        if (now < (presenceCooldownUntilTicks[player.uuid] ?: 0L)) return
        presenceCooldownUntilTicks[player.uuid] = now + DRACONIC_PRESENCE_COOLDOWN_TICKS
        player.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DRACONIC_PRESENCE_DURATION_TICKS, 0, false, false, true))
    }

    private fun grantTreasureSenseReward(player: ServerPlayer) {
        if (player.random.nextBoolean()) {
            val requested = player.random.nextInt(TREASURE_CHOWCOIN_MAX - TREASURE_CHOWCOIN_MIN + 1) + TREASURE_CHOWCOIN_MIN
            val granted = DrakeTamerProgressStore.addTreasureChowcoins(player, requested, TREASURE_CHOWCOIN_WEEKLY_CAP)
            if (granted <= 0) return
            ChowcoinStore.add(player, granted.toLong())
            ChowcoinNetwork.syncTo(player)
            SnackbarNetwork.send(player, SnackbarNotification.texture(SnackbarIcons.CHOWCOIN_TEXTURE, "TREASURE SENSE", "+$granted Chowcoins", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
            return
        }
        if (!DrakeTamerProgressStore.claimTreasureShard(player, TREASURE_SHARD_WEEKLY_CAP)) return
        val shard = ItemStack(Items.AMETHYST_SHARD)
        if (!player.inventory.add(shard)) player.drop(shard, false)
        SnackbarNetwork.send(player, SnackbarNotification.item("minecraft:amethyst_shard", "TREASURE SENSE", "+1 relic shard", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
    }

    private fun isTreasureContainer(player: ServerPlayer, pos: BlockPos): Boolean {
        val state = player.level().getBlockState(pos)
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return id.contains("chest") || id.contains("barrel") || id.contains("shulker_box")
    }

    private fun treasureKey(player: ServerPlayer, pos: BlockPos): String = "${player.uuid}:${player.level().dimension().location()}:${pos.x},${pos.y},${pos.z}"

    private const val TREASURE_SENSE_CHANCE = 0.03
    private const val TREASURE_CHOWCOIN_MIN = 25
    private const val TREASURE_CHOWCOIN_MAX = 75
    private const val TREASURE_CHOWCOIN_WEEKLY_CAP = 500
    private const val TREASURE_SHARD_WEEKLY_CAP = 10
    private const val DRACONIC_PRESENCE_DURATION_TICKS = 160
    private const val DRACONIC_PRESENCE_COOLDOWN_TICKS = 1200L
}
