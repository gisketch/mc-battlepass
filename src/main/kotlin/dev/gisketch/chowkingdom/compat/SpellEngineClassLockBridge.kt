package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.roles.RoleClassEquipmentRules
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.Optional

object SpellEngineClassLockBridge {
    private var registered = false
    private val api by lazy { runCatching { SpellEngineApi() }.getOrNull() }

    fun register() {
        if (registered || !ModList.get().isLoaded("spell_engine")) return
        val api = api ?: return
        runCatching {
            api.registerCastingAttemptHandler(::onCastingAttempt)
            registered = true
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to register Spell Engine class lock bridge", exception)
        }
    }

    private fun onCastingAttempt(args: Any): Any? {
        val api = api ?: return null
        val player = api.caster(args) as? ServerPlayer ?: return null
        val directStack = api.itemStack(args)
        if (RoleClassEquipmentRules.shouldBlockWeaponUse(player, directStack)) return api.noAttempt()
        val sourceStack = api.sourceStack(args, player) ?: return null
        if (RoleClassEquipmentRules.shouldBlockWeaponUse(player, sourceStack)) return api.noAttempt()
        return null
    }

    private class SpellEngineApi {
        private val spellEventsClass = Class.forName("net.spell_engine.api.spell.event.SpellEvents")
        private val castingAttemptEventClass = Class.forName("net.spell_engine.api.spell.event.SpellEvents\$CastingAttemptEvent")
        private val spellContainerSourceClass = Class.forName("net.spell_engine.internals.container.SpellContainerSource")
        private val spellCastAttemptClass = Class.forName("net.spell_engine.internals.casting.SpellCast\$Attempt")
        private val castingAttemptArgsClass = Class.forName("net.spell_engine.api.spell.event.SpellEvents\$CastingAttemptEvent\$Args")
        private val getFirstSourceOfSpell = spellContainerSourceClass.getMethod("getFirstSourceOfSpell", ResourceLocation::class.java, net.minecraft.world.entity.player.Player::class.java)
        private val noAttempt = spellCastAttemptClass.getMethod("none")
        private val caster = castingAttemptArgsClass.getMethod("caster")
        private val spell = castingAttemptArgsClass.getMethod("spell")
        private val itemStack = castingAttemptArgsClass.getMethod("itemStack")

        fun registerCastingAttemptHandler(handler: (Any) -> Any?) {
            val stagedEvent = spellEventsClass.getField("CASTING_ATTEMPT").get(null)
            val preEvent = stagedEvent.javaClass.getField("PRE").get(stagedEvent)
            val proxy = Proxy.newProxyInstance(
                castingAttemptEventClass.classLoader,
                arrayOf(castingAttemptEventClass),
                InvocationHandler { _, method, methodArgs ->
                    if (method.name == "onCastingAttempt" && methodArgs?.size == 1) handler(methodArgs[0]) else null
                },
            )
            preEvent.javaClass.getMethod("register", Any::class.java).invoke(preEvent, proxy)
        }

        fun caster(args: Any): Any? = caster.invoke(args)

        fun itemStack(args: Any): ItemStack = itemStack.invoke(args) as? ItemStack ?: ItemStack.EMPTY

        fun sourceStack(args: Any, player: ServerPlayer): ItemStack? {
            val location = spellLocation(spell.invoke(args)) ?: return null
            val source = getFirstSourceOfSpell.invoke(null, location, player) ?: return null
            return source.javaClass.getMethod("itemStack").invoke(source) as? ItemStack
        }

        fun noAttempt(): Any = noAttempt.invoke(null)

        private fun spellLocation(holder: Any?): ResourceLocation? {
            val optional = holder?.javaClass?.getMethod("unwrapKey")?.invoke(holder) as? Optional<*> ?: return null
            val key = optional.orElse(null) ?: return null
            return key.javaClass.getMethod("location").invoke(key) as? ResourceLocation
        }
    }
}