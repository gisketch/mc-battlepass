package dev.gisketch.chowkingdom.npc

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class TownCenterBlock(properties: Properties) : Block(properties) {
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.SUCCESS
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(Component.literal("Only operators can set the NPC town center."), true)
            return InteractionResult.SUCCESS
        }
        NpcStore.setTownCenter(pos)
        serverPlayer.displayClientMessage(Component.literal("NPC town center set at ${pos.toShortString()} with radius ${NpcStore.townCenterRadius()}. Use /npc plaza radius <blocks> to change it."), true)
        return InteractionResult.SUCCESS
    }
}