package dev.gisketch.chowkingdom.relicroulette

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class RelicTokenItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (!level.isClientSide && player is ServerPlayer) RelicRouletteFeature.openToken(player, stack)
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltipComponents: MutableList<Component>, tooltipFlag: TooltipFlag) {
        val lock = RelicLock.read(stack)
        if (lock == null) {
            tooltipComponents += Component.literal("Battlepass relic token").withStyle(ChatFormatting.GRAY)
            tooltipComponents += Component.literal("Unclaimed tokens cannot be rolled.").withStyle(ChatFormatting.DARK_GRAY)
        } else {
            tooltipComponents += Component.literal("Right-click to open.").withStyle(ChatFormatting.GRAY)
        }
    }
}