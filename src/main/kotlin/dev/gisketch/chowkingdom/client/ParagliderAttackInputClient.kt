package dev.gisketch.chowkingdom.client

import dev.gisketch.chowkingdom.battlepass.BattlepassClientState
import dev.gisketch.chowkingdom.compat.ParagliderStaminaBridge
import dev.gisketch.chowkingdom.compat.StaminaCompatConfig
import dev.gisketch.chowkingdom.roles.RoleClassEquipmentRules
import dev.gisketch.chowkingdom.roles.RolesClientState
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.common.NeoForge

object ParagliderAttackInputClient {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onInteractionKey)
    }

    private fun onInteractionKey(event: InputEvent.InteractionKeyMappingTriggered) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (event.isUseItem && shouldBlockUse(player.mainHandItem)) {
            event.isCanceled = true
            event.setSwingHand(false)
            return
        }
        if (event.isUseItem && isRangedWeaponUse(player.mainHandItem)) {
            val config = StaminaCompatConfig.values()
            if (config.enabled && ParagliderStaminaBridge.available(player) + 0.001 < config.rangedWeaponUseCost.coerceAtLeast(0.0)) {
                event.isCanceled = true
                event.setSwingHand(false)
            }
            return
        }
        if (!event.isAttack) return
        if (minecraft.hitResult !is EntityHitResult) return
        if (!isWeaponLike(player.mainHandItem)) return
        val config = StaminaCompatConfig.values()
        if (!config.enabled) return
        if (ParagliderStaminaBridge.available(player) + 0.001 >= config.attackCost.coerceAtLeast(0.0)) return
        event.isCanceled = true
        event.setSwingHand(false)
    }

    private fun shouldBlockUse(stack: ItemStack): Boolean {
        val playerId = BattlepassClientState.selfId() ?: Minecraft.getInstance().player?.uuid ?: return false
        return RoleClassEquipmentRules.shouldBlockWeaponUseForClasses(RolesClientState.activeClassIdsFor(playerId), stack)
    }

    private fun isWeaponLike(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (isRangedWeaponUse(stack)) return true
        var weaponLike = false
        stack.forEachModifier(EquipmentSlot.MAINHAND) { attribute, _ ->
            if (attribute == Attributes.ATTACK_DAMAGE || attribute == Attributes.ATTACK_SPEED) weaponLike = true
        }
        return weaponLike
    }

    private fun isRangedWeaponUse(stack: ItemStack): Boolean = stack.useAnimation == UseAnim.BOW || stack.useAnimation == UseAnim.CROSSBOW
}