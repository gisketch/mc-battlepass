package dev.gisketch.chowkingdom.shipping

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class ShippingBinBlock(properties: Properties) : Block(properties) {
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (!level.isClientSide && player is ServerPlayer) {
            ShippingBinNetwork.syncTo(player)
            player.openMenu(SimpleMenuProvider({ containerId, playerInventory, _ ->
                ChestMenu.threeRows(containerId, playerInventory, ShippingBinStore.container(player.uuid))
            }, Component.empty()))
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}