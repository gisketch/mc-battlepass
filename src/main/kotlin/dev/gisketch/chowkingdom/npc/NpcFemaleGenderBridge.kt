package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.roles.BODY_MODEL_GIRL
import dev.gisketch.chowkingdom.roles.FemaleGenderChoice
import dev.gisketch.chowkingdom.roles.normalizeBodyModel
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderBounce
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderBustSize
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderFloppy
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.resources.model.ModelManager
import net.minecraft.world.entity.LivingEntity
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.UUID

object NpcFemaleGenderBridge {
    private var warned = false
    private val api: ReflectionApi? by lazy { loadApi() }

    @Suppress("UNCHECKED_CAST")
    fun <T : LivingEntity, M : HumanoidModel<T>> layer(parent: RenderLayerParent<T, M>, modelManager: ModelManager): RenderLayer<T, M>? {
        val api = api ?: return null
        return runCatching {
            api.genderLayer.newInstance(parent, modelManager) as RenderLayer<T, M>
        }.getOrElse {
            warnOnce(it)
            null
        }
    }

    fun apply(entity: ChowNpcEntity) {
        val api = api ?: return
        val config = api.config(entity.uuid) ?: return
        api.apply(config, entity.femaleGenderChoice())
    }

    private fun loadApi(): ReflectionApi? = runCatching {
        val entityConfig = Class.forName("com.wildfire.main.entitydata.EntityConfig")
        val playerConfig = Class.forName("com.wildfire.main.entitydata.PlayerConfig")
        val gender = Class.forName("com.wildfire.main.Gender")
        val genderLayer = Class.forName("com.wildfire.render.GenderLayer")
        val entityCacheField = entityConfig.getField("ENTITY_CACHE")
        @Suppress("UNCHECKED_CAST")
        val entityCache = entityCacheField.get(null) as MutableMap<UUID, Any>
        ReflectionApi(
            entityCache = entityCache,
            playerConfigConstructor = playerConfig.getConstructor(UUID::class.java),
            genderLayer = genderLayer.getConstructor(RenderLayerParent::class.java, ModelManager::class.java),
            femaleGender = enumValue(gender, "FEMALE"),
            maleGender = enumValue(gender, "MALE"),
            updateGender = playerConfig.getMethod("updateGender", gender),
            updateBustSize = playerConfig.getMethod("updateBustSize", java.lang.Float.TYPE),
            updateBreastPhysics = playerConfig.getMethod("updateBreastPhysics", java.lang.Boolean.TYPE),
            updateShowBreastsInArmor = playerConfig.getMethod("updateShowBreastsInArmor", java.lang.Boolean.TYPE),
            updateBounceMultiplier = playerConfig.getMethod("updateBounceMultiplier", java.lang.Float.TYPE),
            updateFloppiness = playerConfig.getMethod("updateFloppiness", java.lang.Float.TYPE),
        )
    }.getOrElse {
        if (it !is ClassNotFoundException) warnOnce(it)
        null
    }

    private fun enumValue(enumClass: Class<*>, name: String): Any =
        enumClass.enumConstants.first { value -> (value as Enum<*>).name == name }

    private fun warnOnce(exception: Throwable) {
        if (warned) return
        warned = true
        ChowKingdomMod.LOGGER.warn("Female Gender NPC integration failed; NPCs will render with CKDM player models only.", exception)
    }

    private data class ReflectionApi(
        val entityCache: MutableMap<UUID, Any>,
        val playerConfigConstructor: Constructor<*>,
        val genderLayer: Constructor<*>,
        val femaleGender: Any,
        val maleGender: Any,
        val updateGender: Method,
        val updateBustSize: Method,
        val updateBreastPhysics: Method,
        val updateShowBreastsInArmor: Method,
        val updateBounceMultiplier: Method,
        val updateFloppiness: Method,
    ) {
        fun config(entityId: UUID): Any? = entityCache.getOrPut(entityId) { playerConfigConstructor.newInstance(entityId) }

        fun apply(config: Any, choice: FemaleGenderChoice): Boolean = runCatching {
            updateGender.invoke(config, if (normalizeBodyModel(choice.bodyModel) == BODY_MODEL_GIRL) femaleGender else maleGender)
            updateBustSize.invoke(config, normalizeFemaleGenderBustSize(choice.bustSize).toFloat())
            updateBreastPhysics.invoke(config, true)
            updateShowBreastsInArmor.invoke(config, true)
            updateBounceMultiplier.invoke(config, normalizeFemaleGenderBounce(choice.bounce).toFloat())
            updateFloppiness.invoke(config, normalizeFemaleGenderFloppy(choice.floppy).toFloat())
            true
        }.getOrElse {
            warnOnce(it)
            false
        }
    }
}
