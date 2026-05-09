package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.recipes.RecipeDisablerFeature
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

internal object RoleClassEquipmentRules {
    private val WRONG_WEAPON_ATTACK_SPEED_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:wrong_weapon_attack_speed")

    fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
        val attacker = event.source.entity as? ServerPlayer ?: return
        val perks = equipmentAffinities(attacker)
        if (perks.isEmpty()) return
        val held = attacker.mainHandItem
        if (held.isEmpty || perks.any { perk -> itemAllowed(held, tagList(perk.weaponTag, perk.weaponTags), perk.weaponPatterns) }) return
        val multiplier = perks.minOf { perk -> perk.wrongWeaponDamageMultiplier.coerceIn(0.0, 1.0) }.toFloat()
        event.newDamage = (event.newDamage * multiplier).coerceAtLeast(0.0f)
        val cooldown = perks.maxOf { perk -> perk.wrongWeaponCooldownTicks.coerceAtLeast(0) }
        if (cooldown > 0) attacker.cooldowns.addCooldown(held.item, cooldown)
    }

    fun onPlayerTick(player: ServerPlayer) {
        val perks = equipmentAffinities(player)
        applyWrongWeaponAttackSpeed(player, perks)
        val armorPerks = perks.filter { perk -> perk.wrongArmorDisablesSprint }
        if (armorPerks.isNotEmpty() && player.armorSlots.any { stack -> !stack.isEmpty && !RecipeDisablerFeature.isCosmeticized(stack) && armorPerks.none { perk -> itemAllowed(stack, tagList(perk.armorTag, perk.armorTags), perk.armorPatterns) } }) {
            player.isSprinting = false
        }
    }

    fun onItemTooltip(event: ItemTooltipEvent) {
        val player = event.entity ?: return
        val classIds = RoleStore.activeClassIds(player.uuid)
        if (classIds.isEmpty()) return
        val perks = equipmentAffinities(classIds)
        if (perks.isEmpty()) return
        val stack = event.itemStack
        val className = classSubject(classIds)
        when {
            isWeaponLike(stack) && perks.none { perk -> itemAllowed(stack, tagList(perk.weaponTag, perk.weaponTags), perk.weaponPatterns) } -> {
                event.toolTip.add(Component.literal("$className cannot use this weapon well.").withStyle(ChatFormatting.RED))
                event.toolTip.add(Component.literal("Damage and attack speed are reduced.").withStyle(ChatFormatting.RED))
            }
            stack.item is ArmorItem && !RecipeDisablerFeature.isCosmeticized(stack) && perks.none { perk -> itemAllowed(stack, tagList(perk.armorTag, perk.armorTags), perk.armorPatterns) } -> {
                event.toolTip.add(Component.literal("$className cannot wear this armor well.").withStyle(ChatFormatting.RED))
                event.toolTip.add(Component.literal("Sprinting is disabled while worn.").withStyle(ChatFormatting.RED))
            }
        }
    }

    fun grantStartingItems(player: ServerPlayer) {
        RoleStore.activeClassIds(player).forEach { classId -> grantStartingItems(player, classId) }
    }

    fun grantStartingItems(player: ServerPlayer, classId: String) {
        val role = RolesConfig.roleClass(classId) ?: return
        val items = role.perks.filter { perk -> perk.type == "starting_items" }.flatMap { perk -> perk.startingItems }
        val stacks = items.mapNotNull(RoleItemStacks::fromId)
        if (stacks.isEmpty() || !RoleStore.markStartingItemsGranted(player.uuid, classId)) return
        stacks.forEach { stack -> if (!player.inventory.add(stack)) player.drop(stack, false) }
    }

    private fun equipmentAffinities(player: ServerPlayer): List<RolePerkDefinition> = RolePerks.classPerks(player, "equipment_affinity")

    private fun equipmentAffinities(classIds: Set<String>): List<RolePerkDefinition> = classIds
        .mapNotNull(RolesConfig::roleClass)
        .flatMap { role -> role.perks }
        .filter { perk -> perk.type == "equipment_affinity" }

    private fun classSubject(classIds: Set<String>): String = if (classIds.size == 1) {
        RolesConfig.roleClass(classIds.first())?.displayName?.ifBlank { classIds.first() } ?: "Your class"
    } else {
        "Your active classes"
    }

    private fun applyWrongWeaponAttackSpeed(player: ServerPlayer, perks: List<RolePerkDefinition>) {
        val attribute = player.getAttribute(Attributes.ATTACK_SPEED) ?: return
        val held = player.mainHandItem
        if (held.isEmpty || perks.isEmpty() || perks.any { perk -> itemAllowed(held, tagList(perk.weaponTag, perk.weaponTags), perk.weaponPatterns) }) {
            attribute.removeModifier(WRONG_WEAPON_ATTACK_SPEED_MODIFIER)
            return
        }
        val multiplier = perks.minOf { perk -> perk.wrongWeaponAttackSpeedMultiplier.coerceIn(0.0, 1.0) }
        if (multiplier >= 1.0) {
            attribute.removeModifier(WRONG_WEAPON_ATTACK_SPEED_MODIFIER)
            return
        }
        attribute.addOrUpdateTransientModifier(
            AttributeModifier(
                WRONG_WEAPON_ATTACK_SPEED_MODIFIER,
                multiplier - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            ),
        )
    }

    private fun isWeaponLike(stack: ItemStack): Boolean {
        var weaponLike = false
        stack.forEachModifier(EquipmentSlot.MAINHAND) { attribute, _ ->
            if (attribute == Attributes.ATTACK_DAMAGE || attribute == Attributes.ATTACK_SPEED) weaponLike = true
        }
        return weaponLike
    }

    private fun itemTag(raw: String): TagKey<Item> = TagKey.create(Registries.ITEM, ResourceLocation.parse(raw.removePrefix("#")))

    private fun tagList(single: String?, many: List<String>): List<TagKey<Item>> = (listOfNotNull(single) + many).map(::itemTag)

    private fun itemAllowed(stack: ItemStack, tags: List<TagKey<Item>>, patterns: List<String>): Boolean {
        if (tags.any { tag -> stack.`is`(tag) }) return true
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return patterns.any { pattern -> globMatches(pattern, itemId) }
    }

    private fun globMatches(pattern: String, value: String): Boolean {
        val regex = pattern.split('*').joinToString(".*") { part -> Regex.escape(part) }
        return Regex("^$regex$", RegexOption.IGNORE_CASE).matches(value)
    }
}
