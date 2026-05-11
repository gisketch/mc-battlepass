package dev.gisketch.chowkingdom.npc

import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

data class NpcAnimationTemplate(
    val id: String,
    val animationId: String,
    val loop: Boolean,
    val durationTicks: Int,
    val weapon: ItemStack? = null,
    val speed: Float = 1.0f,
    val events: List<NpcAnimationEvent> = emptyList(),
)

data class NpcAnimationEvent(
    val tick: Int,
    val type: NpcAnimationEventType,
)

enum class NpcAnimationEventType {
    ATTACK_HIT,
}

object NpcAnimationTemplates {
    val RUN_WITH_SWORD = NpcAnimationTemplate(
        id = "run_with_sword",
        animationId = "running",
        loop = true,
        durationTicks = 16,
        weapon = ItemStack(Items.IRON_SWORD),
    )

    val BOSS_CHASE_SWORD = NpcAnimationTemplate(
        id = "boss_chase_sword",
        animationId = "running_sword",
        loop = true,
        durationTicks = 16,
        weapon = ItemStack(Items.IRON_SWORD),
    )

    val BOSS_STRAFE_SWORD = NpcAnimationTemplate(
        id = "boss_strafe_sword",
        animationId = "running_sword",
        loop = true,
        durationTicks = 16,
        weapon = ItemStack(Items.IRON_SWORD),
        speed = 0.55f,
    )

    val ATTACK_SWORD = NpcAnimationTemplate(
        id = "attack_sword",
        animationId = "attack",
        loop = false,
        durationTicks = 18,
        weapon = ItemStack(Items.IRON_SWORD),
        events = listOf(NpcAnimationEvent(tick = 5, type = NpcAnimationEventType.ATTACK_HIT)),
    )

    val GUARD_SWORD = NpcAnimationTemplate(
        id = "guard_sword",
        animationId = "guard",
        loop = true,
        durationTicks = 20,
        weapon = ItemStack(Items.IRON_SWORD),
    )

    val PARRY_SWORD = NpcAnimationTemplate(
        id = "parry_sword",
        animationId = "parry",
        loop = false,
        durationTicks = 8,
        weapon = ItemStack(Items.IRON_SWORD),
    )

    val HURT_SWORD = NpcAnimationTemplate(
        id = "hurt_sword",
        animationId = "hurt",
        loop = false,
        durationTicks = 8,
        weapon = ItemStack(Items.IRON_SWORD),
    )
}

data class NpcAnimationSnapshot(
    val customAnimation: Boolean,
    val customAnimationKey: String,
    val customAnimationSpeed: Float,
    val mainHand: ItemStack,
    val offHand: ItemStack,
)

object NpcCustomAnimationController {
    fun snapshot(entity: ChowNpcEntity): NpcAnimationSnapshot = NpcAnimationSnapshot(
        customAnimation = entity.customAnimation,
        customAnimationKey = entity.customAnimationKey,
        customAnimationSpeed = entity.customAnimationSpeed,
        mainHand = entity.mainHandItem.copy(),
        offHand = entity.offhandItem.copy(),
    )

    fun play(entity: ChowNpcEntity, template: NpcAnimationTemplate, slot: EquipmentSlot = EquipmentSlot.MAINHAND): Boolean {
        template.weapon?.let { weapon -> entity.setItemSlot(slot, weapon.copy()) }
        return entity.playCustomAnimation(template.animationId, template.speed)
    }

    fun restore(entity: ChowNpcEntity, snapshot: NpcAnimationSnapshot) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, snapshot.mainHand.copy())
        entity.setItemSlot(EquipmentSlot.OFFHAND, snapshot.offHand.copy())
        entity.restoreCustomAnimation(snapshot.customAnimation, snapshot.customAnimationKey, snapshot.customAnimationSpeed)
    }
}
