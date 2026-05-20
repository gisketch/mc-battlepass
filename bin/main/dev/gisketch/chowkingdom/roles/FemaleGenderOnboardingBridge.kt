package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.UUID

object FemaleGenderOnboardingBridge {
    private val noop = AutoCloseable {}
    private var warned = false
    private val api: ReflectionApi? by lazy { loadApi() }

    fun available(): Boolean = api != null

    fun preview(playerId: UUID, choice: FemaleGenderChoice): AutoCloseable {
        val api = api ?: return noop
        val config = api.config(playerId) ?: return noop
        val snapshot = api.snapshot(config) ?: return noop
        if (!api.apply(config, choice)) return noop
        return AutoCloseable { api.apply(config, snapshot) }
    }

    fun save(playerId: UUID, choice: FemaleGenderChoice) {
        val api = api ?: return
        val config = api.config(playerId) ?: return
        if (!api.apply(config, choice)) return
        runCatching {
            if (api.save != null) {
                api.save.invoke(config)
            } else {
                api.staticSave?.invoke(null, config)
            }
            api.syncToServer(config)
        }.onFailure(::warnOnce)
    }

    fun applyCached(playerId: UUID, choice: FemaleGenderChoice) {
        val api = api ?: return
        val config = api.config(playerId) ?: return
        api.apply(config, choice)
    }

    private fun loadApi(): ReflectionApi? = runCatching {
        val wildfireGender = Class.forName("com.wildfire.main.WildfireGender")
        val playerConfig = Class.forName("com.wildfire.main.entitydata.PlayerConfig")
        val gender = Class.forName("com.wildfire.main.Gender")
        val serverboundSyncPacket = Class.forName("com.wildfire.main.networking.ServerboundSyncPacket")
        val packetDistributor = Class.forName("net.neoforged.neoforge.network.PacketDistributor")
        ReflectionApi(
            getOrAddPlayerById = wildfireGender.getMethod("getOrAddPlayerById", UUID::class.java),
            femaleGender = enumValue(gender, "FEMALE"),
            maleGender = enumValue(gender, "MALE"),
            getGender = playerConfig.getMethod("getGender"),
            getBustSize = playerConfig.getMethod("getBustSize"),
            hasBreastPhysics = playerConfig.getMethod("hasBreastPhysics"),
            showBreastsInArmor = playerConfig.getMethod("showBreastsInArmor"),
            getBounceMultiplier = playerConfig.getMethod("getBounceMultiplier"),
            getFloppiness = playerConfig.getMethod("getFloppiness"),
            updateGender = playerConfig.getMethod("updateGender", gender),
            updateBustSize = playerConfig.getMethod("updateBustSize", java.lang.Float.TYPE),
            updateBreastPhysics = playerConfig.getMethod("updateBreastPhysics", java.lang.Boolean.TYPE),
            updateShowBreastsInArmor = playerConfig.getMethod("updateShowBreastsInArmor", java.lang.Boolean.TYPE),
            updateBounceMultiplier = playerConfig.getMethod("updateBounceMultiplier", java.lang.Float.TYPE),
            updateFloppiness = playerConfig.getMethod("updateFloppiness", java.lang.Float.TYPE),
            save = playerConfig.methods.firstOrNull { method -> method.name == "save" && method.parameterCount == 0 },
            staticSave = playerConfig.methods.firstOrNull { method -> method.name == "saveGenderInfo" && method.parameterCount == 1 },
            serverboundSyncPacket = serverboundSyncPacket.getConstructor(playerConfig),
            sendToServer = packetDistributor.methods.firstOrNull { method -> method.name == "sendToServer" && method.parameterCount == 1 },
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
        ChowKingdomMod.LOGGER.warn("Female Gender onboarding integration failed; CKDM will keep its stored choice only.", exception)
    }

    private data class ReflectionApi(
        val getOrAddPlayerById: Method,
        val femaleGender: Any,
        val maleGender: Any,
        val getGender: Method,
        val getBustSize: Method,
        val hasBreastPhysics: Method,
        val showBreastsInArmor: Method,
        val getBounceMultiplier: Method,
        val getFloppiness: Method,
        val updateGender: Method,
        val updateBustSize: Method,
        val updateBreastPhysics: Method,
        val updateShowBreastsInArmor: Method,
        val updateBounceMultiplier: Method,
        val updateFloppiness: Method,
        val save: Method?,
        val staticSave: Method?,
        val serverboundSyncPacket: Constructor<*>,
        val sendToServer: Method?,
    ) {
        fun config(playerId: UUID): Any? = runCatching { getOrAddPlayerById.invoke(null, playerId) }.getOrNull()

        fun snapshot(config: Any): FemaleGenderSnapshot? = runCatching {
            FemaleGenderSnapshot(
                gender = getGender.invoke(config),
                bustSize = (getBustSize.invoke(config) as Number).toFloat(),
                physics = hasBreastPhysics.invoke(config) as Boolean,
                showInArmor = showBreastsInArmor.invoke(config) as Boolean,
                bounce = (getBounceMultiplier.invoke(config) as Number).toFloat(),
                floppy = (getFloppiness.invoke(config) as Number).toFloat(),
            )
        }.getOrElse {
            warnOnce(it)
            null
        }

        fun apply(config: Any, choice: FemaleGenderChoice): Boolean = runCatching {
            updateGender.invoke(config, if (normalizeBodyModel(choice.bodyModel) == BODY_MODEL_GIRL) femaleGender else maleGender)
            updateBustSize.invoke(config, normalizeFemaleGenderBustSize(choice.bustSize).toFloat())
            updateBreastPhysics.invoke(config, choice.physics)
            updateShowBreastsInArmor.invoke(config, choice.showInArmor)
            updateBounceMultiplier.invoke(config, normalizeFemaleGenderBounce(choice.bounce).toFloat())
            updateFloppiness.invoke(config, normalizeFemaleGenderFloppy(choice.floppy).toFloat())
            true
        }.getOrElse {
            warnOnce(it)
            false
        }

        fun apply(config: Any, snapshot: FemaleGenderSnapshot): Boolean = runCatching {
            updateGender.invoke(config, snapshot.gender)
            updateBustSize.invoke(config, snapshot.bustSize)
            updateBreastPhysics.invoke(config, snapshot.physics)
            updateShowBreastsInArmor.invoke(config, snapshot.showInArmor)
            updateBounceMultiplier.invoke(config, snapshot.bounce)
            updateFloppiness.invoke(config, snapshot.floppy)
            true
        }.getOrElse {
            warnOnce(it)
            false
        }

        fun syncToServer(config: Any) {
            val sendToServer = sendToServer ?: return
            val packet = serverboundSyncPacket.newInstance(config)
            sendToServer.invoke(null, packet)
        }
    }

    private data class FemaleGenderSnapshot(
        val gender: Any?,
        val bustSize: Float,
        val physics: Boolean,
        val showInArmor: Boolean,
        val bounce: Float,
        val floppy: Float,
    )
}
