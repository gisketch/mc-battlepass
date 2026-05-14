package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.ParrySoundFeature
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

object EpicFightStaminaBridge {
    private val api by lazy { runCatching { EpicFightApi() }.getOrNull() }
    private val attachedPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val battleModeDisabledForParaglider = ConcurrentHashMap.newKeySet<UUID>()

    fun attach(player: ServerPlayer) {
        if (!ModList.get().isLoaded("epicfight")) return
        if (!attachedPlayers.add(player.uuid)) return
        val api = api ?: run {
            attachedPlayers.remove(player.uuid)
            return
        }
        runCatching {
            val patch = api.getServerPlayerPatch.invoke(null, player) ?: run {
                attachedPlayers.remove(player.uuid)
                return
            }
            val listener = api.getEventListener.invoke(patch)
            api.removeListenersBelongTo.invoke(listener, api.identifier)
            api.registerContextAwareEvent.invoke(listener, api.comboAttackHook, api.contextAwareSubscription(::onComboAttack), api.identifier)
            api.registerContextAwareEvent.invoke(listener, api.skillCastHook, api.contextAwareSubscription(::onSkillCast), api.identifier)
            api.registerContextAwareEvent.invoke(listener, api.skillConsumeHook, api.contextAwareSubscription(::onSkillConsume), api.identifier)
            api.registerContextAwareEvent.invoke(listener, api.takeDamageIncomeHook, api.contextAwareSubscription(::onTakeDamageIncome), api.identifier)
            api.registerEvent.invoke(listener, api.startActionHook, api.defaultSubscription(::onStartAction), api.identifier)
        }.onFailure { exception ->
            attachedPlayers.remove(player.uuid)
            ChowKingdomMod.LOGGER.debug("Failed to attach Epic Fight stamina listeners", exception)
        }
    }

    fun detach(player: ServerPlayer) {
        attachedPlayers.remove(player.uuid)
        battleModeDisabledForParaglider.remove(player.uuid)
        val api = api ?: return
        runCatching {
            val patch = api.getServerPlayerPatch.invoke(null, player) ?: return
            val listener = api.getEventListener.invoke(patch)
            api.removeListenersBelongTo.invoke(listener, api.identifier)
        }
    }

    fun reset() {
        attachedPlayers.clear()
        battleModeDisabledForParaglider.clear()
    }

    fun updateParaglidingBattleMode(player: ServerPlayer, paragliding: Boolean, restoreAfterParagliding: Boolean) {
        if (!ModList.get().isLoaded("epicfight")) return
        val api = api ?: return
        runCatching {
            val patch = api.getServerPlayerPatch.invoke(null, player) ?: return
            if (paragliding) {
                if (api.isEpicFightMode.invoke(patch) as? Boolean == true) {
                    api.toVanillaMode.invoke(patch, true)
                    battleModeDisabledForParaglider.add(player.uuid)
                }
                return
            }
            if (restoreAfterParagliding && battleModeDisabledForParaglider.remove(player.uuid)) {
                api.toEpicFightMode.invoke(patch, true)
            }
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to update Epic Fight paragliding battle mode", exception)
        }
    }

    fun refill(player: ServerPlayer) {
        if (!ModList.get().isLoaded("epicfight")) return
        val api = api ?: return
        runCatching {
            val patch = api.getServerPlayerPatch.invoke(null, player) ?: return
            val max = api.getMaxStamina.invoke(patch) as Number
            api.setStamina.invoke(patch, max.toFloat())
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.debug("Failed to refill Epic Fight stamina", exception)
        }
    }

    private fun onComboAttack(event: Any) {
        val api = api ?: return
        val player = api.playerFromEvent(event) ?: return
        val patch = api.getEntityPatch.invoke(event)
        val config = StaminaCompatConfig.values()
        val cost = if (api.isInAir(patch)) config.epicFightJumpAttackCost else config.epicFightBasicAttackCost
        if (!UnifiedStaminaFeature.spendEpicFightAttack(player, cost)) api.cancel.invoke(event)
    }

    private fun onSkillCast(event: Any) {
        spendForSkillEvent(event)
    }

    private fun onSkillConsume(event: Any) {
        spendForSkillEvent(event)
    }

    private fun spendForSkillEvent(event: Any) {
        val api = api ?: return
        val player = api.playerFromEvent(event) ?: return
        val config = StaminaCompatConfig.values()
        val category = api.skillCategoryName(event)
        val spent = when (category) {
            "WEAPON_INNATE" -> UnifiedStaminaFeature.spendEpicFightSkill(player, config.epicFightInnateSkillCost)
            "GUARD" -> UnifiedStaminaFeature.spendEpicFightGuard(player, config.epicFightGuardSkillCost)
            else -> true
        }
        if (!spent) api.cancel.invoke(event)
    }

    private fun onTakeDamageIncome(event: Any) {
        val api = api ?: return
        val player = api.playerFromEvent(event) ?: return
        val config = StaminaCompatConfig.values()
        val parried = api.isParried.invoke(event) as? Boolean == true
        val cost = when {
            parried -> config.epicFightParryCost
            player.isBlocking -> config.epicFightBlockCost
            else -> return
        }
        if (!UnifiedStaminaFeature.spendEpicFightBlock(player, cost)) {
            api.cancel.invoke(event)
            return
        }
        if (parried) ParrySoundFeature.play(player)
    }

    private fun onStartAction(event: Any) {
        val api = api ?: return
        val player = api.playerFromEvent(event) ?: return
        val animationClass = api.animationClassName(event)
        if (!animationClass.contains("Attack", ignoreCase = true)) return
        val patch = api.getEntityPatch.invoke(event)
        val config = StaminaCompatConfig.values()
        val cost = if (api.isInAir(patch)) config.epicFightJumpAttackCost else config.epicFightBasicAttackCost
        UnifiedStaminaFeature.spendEpicFightAttack(player, cost)
    }

    private class EpicFightApi {
        private val capabilitiesClass = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities")
        private val entityPatchClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.EntityPatch")
        private val livingEntityPatchClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch")
        private val playerPatchClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch")
        private val eventHookClass = Class.forName("yesman.epicfight.api.event.EventHook")
        private val eventClass = Class.forName("yesman.epicfight.api.event.Event")
        private val entityEventListenerClass = Class.forName("yesman.epicfight.api.event.EntityEventListener")
        private val identifierProviderClass = Class.forName("yesman.epicfight.api.event.IdentifierProvider")
        private val defaultEventSubscriptionClass = Class.forName("yesman.epicfight.api.event.subscription.DefaultEventSubscription")
        private val contextAwareEventSubscriptionClass = Class.forName("yesman.epicfight.api.event.subscription.ContextAwareEventSubscription")
        private val playerHooksClass = Class.forName("yesman.epicfight.api.event.EpicFightEventHooks\$Player")
        private val entityHooksClass = Class.forName("yesman.epicfight.api.event.EpicFightEventHooks\$Entity")
        private val animationHooksClass = Class.forName("yesman.epicfight.api.event.EpicFightEventHooks\$Animation")
        private val skillCastEventClass = Class.forName("yesman.epicfight.api.event.types.player.SkillCastEvent")
        private val skillConsumeEventClass = Class.forName("yesman.epicfight.api.event.types.player.SkillConsumeEvent")
        private val skillContainerClass = Class.forName("yesman.epicfight.skill.SkillContainer")
        private val skillClass = Class.forName("yesman.epicfight.skill.Skill")
        private val startActionEventClass = Class.forName("yesman.epicfight.api.event.types.animation.StartActionEvent")

        val getServerPlayerPatch = capabilitiesClass.getMethod("getServerPlayerPatch", ServerPlayer::class.java)
        val getEventListener = livingEntityPatchClass.getMethod("getEventListener")
        val getEntityPatch = Class.forName("yesman.epicfight.api.event.LivingEntityPatchEvent").getMethod("getEntityPatch")
        val getOriginal = entityPatchClass.getMethod("getOriginal")
        private val isInAir = playerPatchClass.getMethod("isInAir")
        val isEpicFightMode = playerPatchClass.getMethod("isEpicFightMode")
        val toVanillaMode = playerPatchClass.getMethod("toVanillaMode", java.lang.Boolean.TYPE)
        val toEpicFightMode = playerPatchClass.getMethod("toEpicFightMode", java.lang.Boolean.TYPE)
        val getMaxStamina = playerPatchClass.getMethod("getMaxStamina")
        val setStamina = playerPatchClass.getMethod("setStamina", java.lang.Float.TYPE)
        val cancel = eventClass.getMethod("cancel")
        val isParried = Class.forName("yesman.epicfight.api.event.types.entity.TakeDamageEvent\$Income").getMethod("isParried")
        val identifier = identifierProviderClass.getMethod("constant", String::class.java).invoke(null, "${ChowKingdomMod.MOD_ID}:epic_fight_stamina")
        val registerEvent = entityEventListenerClass.getMethod("registerEvent", eventHookClass, defaultEventSubscriptionClass, identifierProviderClass)
        val registerContextAwareEvent = entityEventListenerClass.getMethod("registerContextAwareEvent", eventHookClass, contextAwareEventSubscriptionClass, identifierProviderClass)
        val removeListenersBelongTo = entityEventListenerClass.getMethod("removeListenersBelongTo", identifierProviderClass)
        val comboAttackHook = playerHooksClass.getField("COMBO_ATTACK").get(null)
        val skillCastHook = playerHooksClass.getField("CAST_SKILL").get(null)
        val skillConsumeHook = playerHooksClass.getField("CONSUME_SKILL").get(null)
        val takeDamageIncomeHook = entityHooksClass.getField("TAKE_DAMAGE_INCOME").get(null)
        val startActionHook = animationHooksClass.getField("START_ACTION").get(null)

        private val getSkillContainer = skillCastEventClass.getMethod("getSkillContainer")
        private val getContainerSkill = skillContainerClass.getMethod("getSkill")
        private val getConsumedSkill = skillConsumeEventClass.getMethod("getSkill")
        private val getSkillCategory = skillClass.getMethod("getCategory")
        private val getAnimation = startActionEventClass.getMethod("getAnimation")
        private val animationAccessorGet = Class.forName("yesman.epicfight.api.asset.AssetAccessor").getMethod("get")

        fun playerFromEvent(event: Any): ServerPlayer? {
            val patch = getEntityPatch.invoke(event) ?: return null
            return getOriginal.invoke(patch) as? ServerPlayer
        }

        fun isInAir(patch: Any): Boolean = runCatching { isInAir.invoke(patch) as? Boolean == true }.getOrDefault(false)

        fun skillCategoryName(event: Any): String {
            val skill = when {
                skillCastEventClass.isInstance(event) -> getContainerSkill.invoke(getSkillContainer.invoke(event))
                skillConsumeEventClass.isInstance(event) -> getConsumedSkill.invoke(event)
                else -> null
            } ?: return ""
            return getSkillCategory.invoke(skill)?.toString()?.uppercase(Locale.ROOT).orEmpty()
        }

        fun animationClassName(event: Any): String = runCatching {
            val accessor = getAnimation.invoke(event)
            animationAccessorGet.invoke(accessor)?.javaClass?.simpleName.orEmpty()
        }.getOrDefault("")

        fun defaultSubscription(handler: (Any) -> Unit): Any = proxy(defaultEventSubscriptionClass) { method, args ->
            if (method.name == "fire" && args != null && args.isNotEmpty()) {
                args[0]?.let(handler)
                null
            } else {
                objectMethod(method, args)
            }
        }

        fun contextAwareSubscription(handler: (Any) -> Unit): Any = proxy(contextAwareEventSubscriptionClass) { method, args ->
            if (method.name == "fire" && args != null && args.isNotEmpty()) {
                args[0]?.let(handler)
                null
            } else {
                objectMethod(method, args)
            }
        }

        private fun proxy(type: Class<*>, handler: (Method, Array<Any?>?) -> Any?): Any = Proxy.newProxyInstance(
            type.classLoader,
            arrayOf(type),
            InvocationHandler { _, method, args -> handler(method, args) },
        )

        private fun objectMethod(method: Method, args: Array<Any?>?): Any? = when (method.name) {
            "toString" -> "${ChowKingdomMod.MOD_ID}:epic_fight_stamina_subscription"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args?.firstOrNull() === this
            else -> null
        }
    }
}
