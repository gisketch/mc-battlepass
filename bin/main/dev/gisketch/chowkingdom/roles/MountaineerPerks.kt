package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

internal object MountaineerPerks {
    private val STEP_ASSIST_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:mountaineer_step_assist")
    private val COLDPROOF_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:mountaineer_coldproof")

    fun onLivingDamage(player: ServerPlayer, event: LivingDamageEvent.Pre) {
        if (!isFreezeDamage(event.source.getMsgId())) return
        val reduction = RolePerks.configuredJobMaxBonusPercent(player, "freeze_damage_reduction").coerceIn(0.0, 0.95)
        if (reduction <= 0.0) return
        event.newDamage = (event.newDamage * (1.0 - reduction).toFloat()).coerceAtLeast(0.0f)
    }

    fun onBreakSpeed(event: PlayerEvent.BreakSpeed) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.y < CLIMBER_MIN_Y || RolePerks.jobPerks(player, "climber").isEmpty()) return
        event.newSpeed = (event.newSpeed * CLIMBER_MINING_MULTIPLIER).coerceAtLeast(0.0f)
    }

    fun onPlayerTick(player: ServerPlayer) {
        applyStepAssist(player)
        applyColdproof(player)
    }

    private fun applyStepAssist(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.STEP_HEIGHT) ?: return
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "step_assist_lite").coerceAtLeast(0.0)
        if (bonus <= 0.0 || !isStepAssistBlock(player)) {
            attribute.removeModifier(STEP_ASSIST_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                STEP_ASSIST_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_VALUE,
            ),
        )
    }

    private fun applyColdproof(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        if (RolePerks.jobPerks(player, "coldproof").isEmpty()) {
            attribute.removeModifier(COLDPROOF_MODIFIER)
            return
        }
        player.setTicksFrozen(0)
        if (!isInPowderSnow(player)) {
            attribute.removeModifier(COLDPROOF_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                COLDPROOF_MODIFIER,
                COLDPROOF_SPEED_COMPENSATION,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }

    private fun isStepAssistBlock(player: ServerPlayer): Boolean {
        val level = player.level()
        val below = BlockPos.containing(player.x, player.y - 0.2, player.z)
        return isMountainBlock(level.getBlockState(below)) || isMountainBlock(level.getBlockState(player.blockPosition()))
    }

    private fun isInPowderSnow(player: ServerPlayer): Boolean = player.level().getBlockState(player.blockPosition()).`is`(Blocks.POWDER_SNOW)

    private fun isMountainBlock(state: BlockState): Boolean {
        if (state.`is`(Blocks.SNOW) || state.`is`(Blocks.SNOW_BLOCK) || state.`is`(Blocks.POWDER_SNOW) || state.`is`(Blocks.ICE) || state.`is`(Blocks.PACKED_ICE) || state.`is`(Blocks.BLUE_ICE)) return true
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return MOUNTAIN_BLOCK_PARTS.any(id::contains)
    }

    private fun isFreezeDamage(id: String): Boolean = id == "freeze" || id == "powder_snow"

    private const val CLIMBER_MIN_Y = 100.0
    private const val CLIMBER_MINING_MULTIPLIER = 1.10f
    private const val COLDPROOF_SPEED_COMPENSATION = 0.50
    private val MOUNTAIN_BLOCK_PARTS = listOf("snow", "ice", "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "dripstone", "basalt", "blackstone")
}
