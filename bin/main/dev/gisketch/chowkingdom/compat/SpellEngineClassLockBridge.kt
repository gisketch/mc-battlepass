package dev.gisketch.chowkingdom.compat

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.roles.RoleClassEquipmentRules
import dev.gisketch.chowkingdom.roles.RoleClassSpellRules
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
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

    @JvmStatic
    fun blockBindingButton(menu: Any, player: Player, button: Int): Boolean {
        val api = api ?: return false
        val serverPlayer = player as? ServerPlayer ?: return false
        return runCatching {
            val rawSpellId = api.menuRawSpellId(menu, button) ?: return false
            val modeName = api.menuModeName(menu)
            if (modeName?.contains("BOOK", ignoreCase = true) == true) {
                val tag = api.spellBookTag(serverPlayer.level(), rawSpellId) ?: return false
                if (!RoleClassSpellRules.shouldBlockSpellTagBinding(serverPlayer, tag)) return false
                RoleClassSpellRules.sendDeniedTag(serverPlayer, tag)
                return true
            }
            val spellId = api.rawSpellLocation(serverPlayer.level(), rawSpellId) ?: return false
            if (api.menuTargetContainsSpell(menu, spellId)) return false
            if (!RoleClassSpellRules.shouldBlockSpellUse(serverPlayer, spellId)) return false
            RoleClassSpellRules.sendDeniedSpell(serverPlayer, spellId)
            true
        }.getOrElse { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to check Spell Engine binding class lock", exception)
            false
        }
    }

    private fun onCastingAttempt(args: Any): Any? {
        val api = api ?: return null
        val player = api.caster(args) as? ServerPlayer ?: return null
        val directStack = api.itemStack(args)
        if (RoleClassEquipmentRules.shouldBlockWeaponUse(player, directStack)) return api.noAttempt()
        val sourceStack = api.sourceStack(args, player)
        if (sourceStack != null && RoleClassEquipmentRules.shouldBlockWeaponUse(player, sourceStack)) return api.noAttempt()
        val spellId = api.spellLocation(args) ?: return null
        if (RoleClassSpellRules.shouldBlockSpellUse(player, spellId)) {
            RoleClassSpellRules.sendDeniedSpell(player, spellId)
            return api.noAttempt()
        }
        return null
    }

    private class SpellEngineApi {
        private val spellEventsClass = Class.forName("net.spell_engine.api.spell.event.SpellEvents")
        private val castingAttemptEventClass = Class.forName("net.spell_engine.api.spell.event.SpellEvents\$CastingAttemptEvent")
        private val spellContainerSourceClass = Class.forName("net.spell_engine.internals.container.SpellContainerSource")
        private val spellContainerHelperClass = Class.forName("net.spell_engine.api.spell.container.SpellContainerHelper")
        private val spellBindingClass = Class.forName("net.spell_engine.spellbinding.SpellBinding")
        private val spellRegistryClass = Class.forName("net.spell_engine.api.spell.registry.SpellRegistry")
        private val spellCastAttemptClass = Class.forName("net.spell_engine.internals.casting.SpellCast\$Attempt")
        private val castingAttemptArgsClass = Class.forName("net.spell_engine.api.spell.event.SpellEvents\$CastingAttemptEvent\$Args")
        private val getFirstSourceOfSpell = spellContainerSourceClass.getMethod("getFirstSourceOfSpell", ResourceLocation::class.java, net.minecraft.world.entity.player.Player::class.java)
        private val containerFromItemStack = spellContainerHelperClass.getMethod("containerFromItemStack", ItemStack::class.java)
        private val containerContains = spellContainerHelperClass.getMethod("contains", Class.forName("net.spell_engine.api.spell.container.SpellContainer"), ResourceLocation::class.java)
        private val availableSpellBookTags = spellBindingClass.getMethod("availableSpellBookTags", Level::class.java)
        private val registryFrom = spellRegistryClass.getMethod("from", Level::class.java)
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
            val location = spellHolderLocation(spell.invoke(args)) ?: return null
            val source = getFirstSourceOfSpell.invoke(null, location, player) ?: return null
            return source.javaClass.getMethod("itemStack").invoke(source) as? ItemStack
        }

        fun spellLocation(args: Any): ResourceLocation? = spellHolderLocation(spell.invoke(args))

        fun rawSpellLocation(level: Level, rawId: Int): ResourceLocation? {
            val registry = registryFrom.invoke(null, level) ?: return null
            val optional = registry.javaClass.getMethod("getHolder", Int::class.javaPrimitiveType).invoke(registry, rawId) as? Optional<*> ?: return null
            return holderLocation(optional.orElse(null))
        }

        fun spellBookTag(level: Level, rawId: Int): ResourceLocation? {
            val tags = availableSpellBookTags.invoke(null, level) as? List<*> ?: return null
            val tag = tags.getOrNull(rawId - 1) ?: return null
            return tag.javaClass.getMethod("location").invoke(tag) as? ResourceLocation
        }

        fun menuModeName(menu: Any): String? = runCatching {
            menu.javaClass.getMethod("getMode").invoke(menu).toString()
        }.getOrNull()

        fun menuRawSpellId(menu: Any, button: Int): Int? {
            val field = menu.javaClass.getDeclaredField("spellId").also { it.isAccessible = true }
            val values = field.get(menu) as? IntArray ?: return null
            return values.getOrNull(button)
        }

        fun menuTargetContainsSpell(menu: Any, spellId: ResourceLocation): Boolean {
            val items = menu.javaClass.getMethod("getItems").invoke(menu) as? List<*> ?: return false
            val stack = items.getOrNull(0) as? ItemStack ?: return false
            val container = containerFromItemStack.invoke(null, stack) ?: return false
            return containerContains.invoke(null, container, spellId) as? Boolean ?: false
        }

        fun noAttempt(): Any = noAttempt.invoke(null)

        private fun spellHolderLocation(holder: Any?): ResourceLocation? {
            return holderLocation(holder)
        }

        private fun holderLocation(holder: Any?): ResourceLocation? {
            val optional = holder?.javaClass?.getMethod("unwrapKey")?.invoke(holder) as? Optional<*> ?: return null
            val key = optional.orElse(null) ?: return null
            return key.javaClass.getMethod("location").invoke(key) as? ResourceLocation
        }
    }
}
