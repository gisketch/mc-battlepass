package dev.gisketch.chowkingdom.npc

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class CampingBlock(properties: Properties) : Block(properties), EntityBlock {
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = COLLISION_SHAPE

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.ENTITYBLOCK_ANIMATED

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = CampingBlockEntity(pos, state)

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

    companion object {
        private val SHAPE: VoxelShape = Shapes.or(
            box(-16.0, 0.0, -16.0, 32.0, 4.0, 32.0),
            box(-10.0, 4.0, -16.0, 26.0, 28.0, 32.0),
        )
        private val COLLISION_SHAPE: VoxelShape = box(-16.0, 0.0, -16.0, 32.0, 24.0, 32.0)
    }
}
