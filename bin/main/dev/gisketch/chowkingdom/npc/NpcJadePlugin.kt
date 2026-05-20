package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Block
import snownee.jade.api.BlockAccessor
import snownee.jade.api.EntityAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.IEntityComponentProvider
import snownee.jade.api.IServerDataProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.JadeIds
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.ui.IElement
import snownee.jade.api.ui.IElementHelper

@WailaPlugin
class NpcJadePlugin : IWailaPlugin {
    override fun register(registration: IWailaCommonRegistration) {
        registration.registerBlockDataProvider(NpcBedJadeProvider, Block::class.java)
        registration.registerEntityDataProvider(NpcEntityJadeProvider, Entity::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(NpcBedJadeProvider, Block::class.java)
        registration.registerEntityComponent(NpcEntityJadeProvider, Entity::class.java)
    }
}

object NpcEntityJadeProvider : IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    private val UID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc_friendship")

    override fun appendServerData(data: CompoundTag, accessor: EntityAccessor) {
        val entity = accessor.entity as? ChowNpcEntity ?: return
        val definition = NpcConfig.get(entity.npcId)
        val playerId = accessor.player.stringUUID
        val friendship = NpcStore.friendshipSnapshot(entity.npcId, playerId)
        data.putString(NPC_NAME_TAG, definition?.displayName() ?: entity.customName?.string ?: entity.npcId)
        data.putString(FRIENDSHIP_LABEL_TAG, friendshipLabel(friendship.category))
        data.putString(FRIENDSHIP_ICON_TAG, friendshipIcon(friendship.category).toString())
        data.putString(FRIENDSHIP_FORMAT_TAG, friendshipFormat(friendship.category).name)
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: EntityAccessor, config: IPluginConfig) {
        if (accessor.entity !is ChowNpcEntity) return
        val data = accessor.serverData
        if (!data.contains(NPC_NAME_TAG)) return
        val name = data.getString(NPC_NAME_TAG).ifBlank { "NPC" }
        val label = data.getString(FRIENDSHIP_LABEL_TAG).ifBlank { "Neutral" }
        val icon = runCatching { ResourceLocation.parse(data.getString(FRIENDSHIP_ICON_TAG)) }.getOrDefault(NEUTRAL_ICON)
        val format = ChatFormatting.getByName(data.getString(FRIENDSHIP_FORMAT_TAG)) ?: ChatFormatting.GRAY
        if (!tooltip.replace(JadeIds.CORE_OBJECT_NAME, Component.literal(name))) {
            tooltip.add(0, Component.literal(name), JadeIds.CORE_OBJECT_NAME)
        }
        tooltip.add(friendshipElements(icon, label, format))
    }

    override fun shouldRequestData(accessor: EntityAccessor): Boolean = accessor.entity is ChowNpcEntity

    override fun getUid(): ResourceLocation = UID

    override fun getDefaultPriority(): Int = 10_000

    private fun friendshipLabel(category: NpcFriendshipCategory): String = when (category) {
        NpcFriendshipCategory.Hatred -> "Enemy"
        NpcFriendshipCategory.Enemy -> "Enemy"
        NpcFriendshipCategory.Dislike -> "Dislike"
        NpcFriendshipCategory.Neutral -> "Neutral"
        NpcFriendshipCategory.Okay -> "Friend"
        NpcFriendshipCategory.GoodFriends -> "Good Friend"
        NpcFriendshipCategory.BestFriends -> "Best Friend"
    }

    private fun friendshipElements(icon: ResourceLocation, label: String, format: ChatFormatting): List<IElement> = listOf(
        IElementHelper.get().sprite(icon, ICON_SIZE, ICON_SIZE),
        IElementHelper.get().text(Component.literal(" $label").withStyle(format)),
    )

    private fun friendshipIcon(category: NpcFriendshipCategory): ResourceLocation = when (category) {
        NpcFriendshipCategory.Hatred, NpcFriendshipCategory.Enemy, NpcFriendshipCategory.Dislike -> ANGRY_ICON
        NpcFriendshipCategory.Neutral -> NEUTRAL_ICON
        NpcFriendshipCategory.Okay, NpcFriendshipCategory.GoodFriends, NpcFriendshipCategory.BestFriends -> HEART_ICON
    }

    private fun friendshipFormat(category: NpcFriendshipCategory): ChatFormatting = when (category) {
        NpcFriendshipCategory.Hatred, NpcFriendshipCategory.Enemy -> ChatFormatting.RED
        NpcFriendshipCategory.Dislike -> ChatFormatting.GOLD
        NpcFriendshipCategory.Neutral -> ChatFormatting.GRAY
        NpcFriendshipCategory.Okay -> ChatFormatting.GREEN
        NpcFriendshipCategory.GoodFriends, NpcFriendshipCategory.BestFriends -> ChatFormatting.LIGHT_PURPLE
    }

    private const val NPC_NAME_TAG = "ChowkingdomNpcName"
    private const val FRIENDSHIP_LABEL_TAG = "ChowkingdomNpcFriendshipLabel"
    private const val FRIENDSHIP_ICON_TAG = "ChowkingdomNpcFriendshipIcon"
    private const val FRIENDSHIP_FORMAT_TAG = "ChowkingdomNpcFriendshipFormat"
    private const val ICON_SIZE = 10
    private val HEART_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc_friendship_heart")
    private val ANGRY_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc_friendship_angry")
    private val NEUTRAL_ICON = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "npc_friendship_neutral")
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
