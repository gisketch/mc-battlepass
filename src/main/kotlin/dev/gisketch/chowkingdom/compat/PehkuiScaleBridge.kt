package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.npc.NpcBodyScaleDefinition
import dev.gisketch.chowkingdom.roles.BodyScaleChoice
import net.minecraft.world.entity.Entity
import net.neoforged.fml.ModList

object PehkuiScaleBridge {
    private val api by lazy { runCatching { PehkuiApi() }.getOrNull() }

    fun apply(entity: Entity, scale: BodyScaleChoice) {
        apply(entity, scale.height.toFloat(), scale.weight.toFloat())
    }

    fun apply(entity: Entity, scale: NpcBodyScaleDefinition) {
        apply(entity, scale.height.toFloat(), scale.weight.toFloat())
    }

    private fun apply(entity: Entity, height: Float, weight: Float) {
        if (!ModList.get().isLoaded("pehkui")) return
        val api = api ?: return
        runCatching {
            api.setScale(entity, "HEIGHT", height)
            api.setScale(entity, "WIDTH", weight)
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to apply Pehkui scale", exception)
        }
    }

    private class PehkuiApi {
        private val scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes")
        private val scaleTypeClass = Class.forName("virtuoel.pehkui.api.ScaleType")
        private val scaleDataClass = Class.forName("virtuoel.pehkui.api.ScaleData")
        private val getScaleData = scaleTypeClass.getMethod("getScaleData", Entity::class.java)
        private val setScale = scaleDataClass.getMethod("setScale", java.lang.Float.TYPE)

        fun setScale(entity: Entity, fieldName: String, value: Float) {
            val type = scaleTypesClass.getField(fieldName).get(null)
            val data = getScaleData.invoke(type, entity)
            setScale.invoke(data, value.coerceIn(0.6f, 1.4f))
        }
    }
}