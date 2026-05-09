package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import java.util.UUID

internal object MasonPerks {
    private val BUILDER_REACH_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:mason_builder_reach")
    private val eyeTargets: MutableMap<UUID, ResourceLocation> = linkedMapOf()

    fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.abilities.instabuild) return
        val chance = RolePerks.configuredJobChance(player, "steady_hands")
        if (chance <= 0.0 || player.random.nextDouble() >= chance || !isSteadyHandsBlock(event.placedBlock)) return
        val item = event.placedBlock.block.asItem()
        if (item == Items.AIR) return
        if (!player.inventory.add(ItemStack(item))) player.drop(ItemStack(item), false)
    }

    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.hand != InteractionHand.MAIN_HAND || event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (!player.isCrouching || !player.mainHandItem.isEmpty || RolePerks.jobPerks(player, "masons_eye").isEmpty()) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        val targetId = BuiltInRegistries.BLOCK.getKey(event.level.getBlockState(event.pos).block)
        val previous = eyeTargets[player.uuid]
        if (previous == targetId) {
            eyeTargets.remove(player.uuid)
        } else {
            eyeTargets[player.uuid] = targetId
            highlightMatchingBlocks(player, targetId)
        }
    }

    fun onLivingDamage(player: ServerPlayer, event: LivingDamageEvent.Pre) {
        if (!event.source.`is`(DamageTypeTags.IS_EXPLOSION)) return
        val reduction = RolePerks.configuredJobMaxBonusPercent(player, "explosion_damage_reduction").coerceIn(0.0, 0.95)
        if (reduction <= 0.0) return
        event.newDamage = (event.newDamage * (1.0 - reduction).toFloat()).coerceAtLeast(0.0f)
    }

    fun onPlayerTick(player: ServerPlayer) {
        applyBuilderReach(player)
        val targetId = eyeTargets[player.uuid] ?: return
        if (player.level().gameTime % MASON_EYE_INTERVAL_TICKS == 0L) highlightMatchingBlocks(player, targetId)
    }

    private fun applyBuilderReach(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE) ?: return
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "builders_reach").coerceAtLeast(0.0)
        if (bonus <= 0.0) {
            attribute.removeModifier(BUILDER_REACH_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                BUILDER_REACH_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_VALUE,
            ),
        )
    }

    private fun highlightMatchingBlocks(player: ServerPlayer, targetId: ResourceLocation) {
        val level = player.level() as? ServerLevel ?: return
        var highlighted = 0
        val center = player.blockPosition()
        BlockPos.betweenClosed(center.offset(-MASON_EYE_RADIUS, -MASON_EYE_RADIUS, -MASON_EYE_RADIUS), center.offset(MASON_EYE_RADIUS, MASON_EYE_RADIUS, MASON_EYE_RADIUS)).forEach { pos ->
            if (highlighted >= MASON_EYE_MAX_HIGHLIGHTS) return@forEach
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block) != targetId) return@forEach
            level.sendParticles(ParticleTypes.END_ROD, pos.x + 0.5, pos.y + 0.65, pos.z + 0.5, 1, 0.25, 0.25, 0.25, 0.0)
            highlighted++
        }
    }

    private fun isSteadyHandsBlock(state: BlockState): Boolean {
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return STEADY_HANDS_BLOCK_PARTS.any(id::contains)
    }

    private const val MASON_EYE_RADIUS = 12
    private const val MASON_EYE_MAX_HIGHLIGHTS = 128
    private const val MASON_EYE_INTERVAL_TICKS = 20L
    private val STEADY_HANDS_BLOCK_PARTS = listOf("stone", "brick", "plank", "wood", "log", "glass", "terracotta", "concrete", "tile", "slab", "stairs", "wall")
}
