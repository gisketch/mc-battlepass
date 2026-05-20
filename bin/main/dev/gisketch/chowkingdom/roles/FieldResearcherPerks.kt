package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

internal object FieldResearcherPerks {
    private val LUCK_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:field_researcher_luck")

    fun onPlayerTick(player: ServerPlayer) {
        val attribute = player.getAttribute(Attributes.LUCK) ?: return
        val bonus = RolePerks.configuredJobMaxBonusPercent(player, "luck_lite")
        if (bonus <= 0.0) {
            attribute.removeModifier(LUCK_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                LUCK_MODIFIER,
                bonus,
                AttributeModifier.Operation.ADD_VALUE,
            ),
        )
    }
}
