package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext

class NpcJobApplicationItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player as? ServerPlayer ?: return InteractionResult.sidedSuccess(level.isClientSide)
        val stack = context.itemInHand
        val npcId = NpcJobApplicationData.readNpcId(stack)
        if (npcId.isBlank()) {
            player.displayClientMessage(Component.literal("This job application has no NPC assigned."), true)
            return InteractionResult.FAIL
        }
        return if (NpcFeature.assignWorkplace(player, npcId, context.clickedPos, stack)) InteractionResult.SUCCESS else InteractionResult.FAIL
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltipComponents: MutableList<Component>, tooltipFlag: TooltipFlag) {
        val npcId = NpcJobApplicationData.readNpcId(stack)
        if (npcId.isBlank()) {
            tooltipComponents += Component.literal("Assign an NPC to a workplace.").withStyle(ChatFormatting.GRAY)
        } else {
            val definition = NpcConfig.get(npcId)
            val name = definition?.name ?: npcId
            tooltipComponents += Component.literal("For $name").withStyle(ChatFormatting.GRAY)
            tooltipComponents += Component.literal("Right-click a work block.").withStyle(ChatFormatting.DARK_GRAY)
            val workBlocks = definition?.workBlocks.orEmpty()
            if (workBlocks.isNotEmpty()) {
                tooltipComponents += Component.literal("Needs nearby:").withStyle(ChatFormatting.GRAY)
                workBlocks.forEach { requirement ->
                    tooltipComponents += Component.literal("${requirement.count} x ${requirement.label()}").withStyle(ChatFormatting.DARK_GRAY)
                }
            }
        }
    }
}

object NpcJobApplicationData {
    fun forNpc(stack: ItemStack, npcId: String): ItemStack {
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        root.put(APPLICATION_TAG, CompoundTag().also { tag ->
            tag.putString(NPC_ID_TAG, npcId)
            tag.putString(MOD_TAG, ChowKingdomMod.MOD_ID)
        })
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root)
        return stack
    }

    fun readNpcId(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (!root.contains(APPLICATION_TAG, CompoundTag.TAG_COMPOUND.toInt())) return ""
        return root.getCompound(APPLICATION_TAG).getString(NPC_ID_TAG)
    }

    private const val APPLICATION_TAG = "CkdmNpcJobApplication"
    private const val NPC_ID_TAG = "NpcId"
    private const val MOD_TAG = "Mod"
}