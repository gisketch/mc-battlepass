package dev.gisketch.chowkingdom.npc

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class CampingBlock(properties: Properties) : Block(properties) {
    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (NpcFeature.spawnFromCamp(level, pos) && placer is ServerPlayer) {
            placer.sendSystemMessage(Component.literal("A traveler has arrived at camp."))
        }
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (!level.isClientSide && player is ServerPlayer) {
            val spawned = NpcFeature.spawnFromCamp(level, pos)
            player.displayClientMessage(Component.literal(if (spawned) "A traveler has arrived at camp." else "No new traveler is waiting right now."), true)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
