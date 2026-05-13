package dev.gisketch.chowkingdom.npc

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList
import java.lang.reflect.Proxy
import java.util.UUID

object NpcCombatRollBridge {
    private const val COMBAT_ROLL_MOD_ID = "combat_roll"
    private const val DEFAULT_ROLL_IFRAMES = 12
    private val rollingUntil: MutableMap<UUID, Long> = linkedMapOf()
    private val rollVectors: MutableMap<UUID, Vec3> = linkedMapOf()
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        if (!ModList.get().isLoaded(COMBAT_ROLL_MOD_ID)) return
        runCatching { registerRollEvent() }
            .onFailure { error -> ChowKingdomMod.LOGGER.warn("Failed to register NPC Combat Roll bridge", error) }
    }

    fun isRolling(player: ServerPlayer): Boolean {
        val until = rollingUntil[player.uuid] ?: return false
        if (player.level().gameTime <= until) return true
        rollingUntil.remove(player.uuid)
        rollVectors.remove(player.uuid)
        return false
    }

    fun clear(player: ServerPlayer) {
        rollingUntil.remove(player.uuid)
        rollVectors.remove(player.uuid)
    }

    fun applyNpcIframes(entity: LivingEntity, ticks: Int) {
        if (ticks <= 0 || !ModList.get().isLoaded(COMBAT_ROLL_MOD_ID)) return
        runCatching {
            val invulnerableType = Class.forName("net.combat_roll.api.RollInvulnerable")
            if (!invulnerableType.isInstance(entity)) return
            invulnerableType.getMethod("setRollInvulnerableTicks", Int::class.javaPrimitiveType).invoke(entity, ticks)
        }
    }

    private fun registerRollEvent() {
        val eventsClass = Class.forName("net.combat_roll.api.event.ServerSideRollEvents")
        val listenerClass = Class.forName("net.combat_roll.api.event.ServerSideRollEvents\$PlayerStartRolling")
        val event = eventsClass.getField("PLAYER_START_ROLLING").get(null)
        val proxy = Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { _, method, args ->
            if (method.name == "onPlayerStartedRolling" && args != null && args.size >= 2) {
                onPlayerStartedRolling(args[0], args[1])
            }
            null
        }
        val register = event.javaClass.methods.firstOrNull { method -> method.name == "register" && method.parameterCount == 1 }
            ?: error("Combat Roll event register method not found")
        register.invoke(event, proxy)
    }

    private fun onPlayerStartedRolling(player: Any?, direction: Any?) {
        val serverPlayer = player as? ServerPlayer ?: return
        val duration = rollDurationTicks().coerceIn(1, 80)
        rollingUntil[serverPlayer.uuid] = serverPlayer.level().gameTime + duration
        (direction as? Vec3)?.let { vector -> rollVectors[serverPlayer.uuid] = vector }
    }

    private fun rollDurationTicks(): Int = runCatching {
        val rollManagerClass = Class.forName("net.combat_roll.internals.RollManager")
        (rollManagerClass.getMethod("rollDuration").invoke(null) as? Number)?.toInt()
    }.getOrNull() ?: DEFAULT_ROLL_IFRAMES
}
