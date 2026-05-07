package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.IServerDataProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig

@WailaPlugin
class NpcJadePlugin : IWailaPlugin {
    override fun register(registration: IWailaCommonRegistration) {
        registration.registerBlockDataProvider(NpcBedJadeProvider, Block::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(NpcBedJadeProvider, Block::class.java)
    }
}

object NpcBedJadeProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private val UID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc_bed")

    override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
        if (!accessor.blockState.`is`(BlockTags.BEDS)) return
        val npcId = NpcFeature.homeOwnerAtBed(accessor.level, accessor.position) ?: return
        val definition = NpcConfig.get(npcId)
        data.putString(NPC_ID_TAG, npcId)
        data.putString(NPC_NAME_TAG, definition?.name ?: npcId)
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        if (!accessor.blockState.`is`(BlockTags.BEDS)) return
        val data = accessor.serverData
        if (!data.contains(NPC_ID_TAG)) return
        val name = data.getString(NPC_NAME_TAG).ifBlank { data.getString(NPC_ID_TAG) }
        tooltip.add(Component.literal("$name's Bed").withStyle(ChatFormatting.GOLD), UID)
    }

    override fun shouldRequestData(accessor: BlockAccessor): Boolean = accessor.blockState.`is`(BlockTags.BEDS)

    override fun getUid(): ResourceLocation = UID

    private const val NPC_ID_TAG = "ChowkingdomNpcBedOwnerId"
    private const val NPC_NAME_TAG = "ChowkingdomNpcBedOwnerName"
}
