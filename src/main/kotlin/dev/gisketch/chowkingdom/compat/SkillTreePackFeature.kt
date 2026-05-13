package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.AddPackFindersEvent

object SkillTreePackFeature {
    private const val PACK_PATH = "resourcepacks/ckdm_skill_tree_changes"

    fun register(modBus: IEventBus) {
        modBus.addListener(::addPackFinders)
    }

    private fun addPackFinders(event: AddPackFindersEvent) {
        event.addPackFinders(
            ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, PACK_PATH),
            PackType.SERVER_DATA,
            Component.literal("CKDM Skill Tree Changes"),
            PackSource.BUILT_IN,
            true,
            Pack.Position.TOP,
        )
    }
}
