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
import net.minecraft.world.item.UseAnim
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

internal object RoleClassEquipmentRules {
    private val WRONG_WEAPON_ATTACK_SPEED_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:wrong_weapon_attack_speed")

    fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
        val attacker = event.source.entity as? ServerPlayer ?: return
        if (!isWrongWeapon(attacker)) return
        val perks = equipmentAffinities(attacker)
        val held = attacker.mainHandItem
        val multiplier = perks.minOf { perk -> perk.wrongWeaponDamageMultiplier.coerceIn(0.0, 1.0) }.toFloat()
        event.newDamage = (event.newDamage * multiplier).coerceAtLeast(0.0f)
        val cooldown = perks.maxOf { perk -> perk.wrongWeaponCooldownTicks.coerceAtLeast(0) }
        if (cooldown > 0) attacker.cooldowns.addCooldown(held.item, cooldown)
    }

    fun onPlayerTick(player: ServerPlayer) {
        val perks = equipmentAffinities(player)
        applyWrongWeaponAttackSpeed(player, perks)
        val armorPerks = perks.filter { perk -> perk.wrongArmorDisablesSprint }
        if (armorPerks.isNotEmpty() && player.armorSlots.any { stack -> !stack.isEmpty && !RecipeDisablerFeature.isCosmeticized(stack) && !isGloballyAllowedArmor(stack) && armorPerks.none { perk -> armorAllowed(stack, perk) } }) {
            player.isSprinting = false
        }
    }

    fun onItemTooltip(event: ItemTooltipEvent) {
        val player = event.entity ?: return
        val classIds = RoleStore.activeClassIds(player.uuid)
        if (classIds.isEmpty()) return
        val stack = event.itemStack
        when {
            isWeaponLike(stack) && !isGloballyAllowedWeapon(stack) -> event.toolTip.add(classTooltipLine(classIds, stack, ActiveClassEquipment::allowsWeapon) ?: unconfiguredWeaponTooltip())
            stack.item is ArmorItem && !RecipeDisablerFeature.isCosmeticized(stack) && !isGloballyAllowedArmor(stack) -> classTooltipLine(classIds, stack, ActiveClassEquipment::allowsArmor)?.let(event.toolTip::add)
        }
    }

    fun grantStartingItems(player: ServerPlayer) {
        RoleStore.activeClassIds(player).forEach { classId -> grantStartingItems(player, classId) }
    }

    fun grantStartingItems(player: ServerPlayer, classId: String) {
        val role = RolesConfig.roleClass(classId) ?: return
        val items = role.perks.filter { perk -> perk.type == "starting_items" }.flatMap { perk -> perk.startingItems }
        val stacks = items.mapNotNull { item -> RoleItemStacks.fromId(item, "class $classId starting item") }
        if (stacks.isEmpty() || !RoleStore.markStartingItemsGranted(player.uuid, classId)) return
        stacks.forEach { stack -> if (!player.inventory.add(stack)) player.drop(stack, false) }
    }

    fun isWrongWeapon(player: ServerPlayer): Boolean {
        val perks = equipmentAffinities(player)
        if (perks.isEmpty()) return false
        val held = player.mainHandItem
        if (isGloballyAllowedWeapon(held)) return false
        return !held.isEmpty && perks.none { perk -> weaponAllowed(held, perk) }
    }

    fun shouldBlockWeaponUse(player: ServerPlayer, stack: ItemStack): Boolean {
        if (stack.isEmpty || !isWeaponLike(stack) || isGloballyAllowedWeapon(stack)) return false
        val perks = equipmentAffinities(player)
        return perks.isNotEmpty() && perks.none { perk -> weaponAllowed(stack, perk) }
    }

    fun shouldGreyOutForClasses(activeClassIds: Set<String>, stack: ItemStack): Boolean {
        if (activeClassIds.isEmpty() || stack.isEmpty) return false
        return when {
            isWeaponLike(stack) && !isGloballyAllowedWeapon(stack) -> !configuredClassEquipment().filter { entry -> entry.allowsWeapon(stack) }.any { entry -> entry.id in activeClassIds }
            stack.item is ArmorItem && !RecipeDisablerFeature.isCosmeticized(stack) && !isGloballyAllowedArmor(stack) -> !configuredClassEquipment().filter { entry -> entry.allowsArmor(stack) }.any { entry -> entry.id in activeClassIds }
            else -> false
        }
    }

    fun shouldBlockWeaponUseForClasses(activeClassIds: Set<String>, stack: ItemStack): Boolean {
        if (activeClassIds.isEmpty() || stack.isEmpty || !isWeaponLike(stack) || isGloballyAllowedWeapon(stack)) return false
        return !configuredClassEquipment().filter { entry -> entry.allowsWeapon(stack) }.any { entry -> entry.id in activeClassIds }
    }

    fun unconfiguredWeaponIds(): List<String> = BuiltInRegistries.ITEM.asSequence()
        .map { item -> BuiltInRegistries.ITEM.getKey(item).toString() to ItemStack(item) }
        .filter { (_, stack) -> !stack.isEmpty && isUnconfiguredWeapon(stack) }
        .map { (id, _) -> id }
        .sorted()
        .toList()

    private fun equipmentAffinities(player: ServerPlayer): List<RolePerkDefinition> = RolePerks.classPerks(player, "equipment_affinity")

    private fun equipmentAffinities(classIds: Set<String>): List<RolePerkDefinition> = classIds
        .mapNotNull(RolesConfig::roleClass)
        .flatMap { role -> role.perks }
        .filter { perk -> perk.type == "equipment_affinity" }

    private fun activeClassEquipment(classIds: Set<String>): List<ActiveClassEquipment> = classIds.mapNotNull { classId ->
        val role = RolesConfig.roleClass(classId) ?: return@mapNotNull null
        val perks = role.perks.filter { perk -> perk.type == "equipment_affinity" }
        if (perks.isEmpty()) return@mapNotNull null
        ActiveClassEquipment(role.id, role.displayName.ifBlank { role.id }, perks)
    }

    private fun configuredClassEquipment(): List<ActiveClassEquipment> = RolesConfig.classes().mapNotNull { role ->
        val perks = role.perks.filter { perk -> perk.type == "equipment_affinity" }
        if (perks.isEmpty()) return@mapNotNull null
        ActiveClassEquipment(role.id, role.displayName.ifBlank { role.id }, perks)
    }

    private fun classTooltipLine(activeClassIds: Set<String>, stack: ItemStack, allows: ActiveClassEquipment.(ItemStack) -> Boolean): Component? {
        val usableClasses = configuredClassEquipment().filter { entry -> entry.allows(stack) }
        if (usableClasses.isEmpty()) return null
        val playerCanUse = usableClasses.any { entry -> entry.id in activeClassIds }
        val line = Component.literal("Classes: ").withStyle(ChatFormatting.GRAY)
        usableClasses.forEachIndexed { index, entry ->
            if (index > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
            val color = when {
                !playerCanUse -> ChatFormatting.DARK_RED
                entry.id in activeClassIds -> ChatFormatting.GREEN
                else -> ChatFormatting.DARK_GRAY
            }
            line.append(Component.literal(entry.name).withStyle(color))
        }
        return line
    }

    private fun unconfiguredWeaponTooltip(): Component = Component.literal("Weapon unconfigured, ask @gisketch for help").withStyle(ChatFormatting.DARK_RED)

    private fun isUnconfiguredWeapon(stack: ItemStack): Boolean = isWeaponLike(stack) && !isGloballyAllowedWeapon(stack) && configuredClassEquipment().none { entry -> entry.allowsWeapon(stack) }

    private fun applyWrongWeaponAttackSpeed(player: ServerPlayer, perks: List<RolePerkDefinition>) {
        val attribute = player.getAttribute(Attributes.ATTACK_SPEED) ?: return
        val held = player.mainHandItem
        if (held.isEmpty || perks.isEmpty() || isGloballyAllowedWeapon(held) || perks.any { perk -> weaponAllowed(held, perk) }) {
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
        if (stack.useAnimation == UseAnim.BOW || stack.useAnimation == UseAnim.CROSSBOW) return true
        var weaponLike = false
        stack.forEachModifier(EquipmentSlot.MAINHAND) { attribute, _ ->
            if (attribute == Attributes.ATTACK_DAMAGE || attribute == Attributes.ATTACK_SPEED) weaponLike = true
        }
        return weaponLike
    }

    private fun isGloballyAllowedWeapon(stack: ItemStack): Boolean {
        val whitelist = RolesConfig.equipmentWhitelist()
        return itemAllowed(stack, whitelist.weaponTags.map(::itemTag), whitelist.weaponPatterns, whitelist.weaponExcludePatterns)
    }

    private fun isGloballyAllowedArmor(stack: ItemStack): Boolean {
        val whitelist = RolesConfig.equipmentWhitelist()
        return itemAllowed(stack, whitelist.armorTags.map(::itemTag), whitelist.armorPatterns, whitelist.armorExcludePatterns)
    }

    private fun itemTag(raw: String): TagKey<Item> = TagKey.create(Registries.ITEM, ResourceLocation.parse(raw.removePrefix("#")))

    private fun tagList(single: String?, many: List<String>): List<TagKey<Item>> = (listOfNotNull(single) + many).map(::itemTag)

    private fun weaponAllowed(stack: ItemStack, perk: RolePerkDefinition): Boolean = itemAllowed(
        stack,
        tagList(perk.weaponTag, perk.weaponTags),
        perk.weaponPatterns,
        perk.weaponExcludePatterns,
    )

    private fun armorAllowed(stack: ItemStack, perk: RolePerkDefinition): Boolean = itemAllowed(
        stack,
        tagList(perk.armorTag, perk.armorTags),
        perk.armorPatterns,
        perk.armorExcludePatterns,
    )

    private fun itemAllowed(stack: ItemStack, tags: List<TagKey<Item>>, patterns: List<String>, excludePatterns: List<String> = emptyList()): Boolean {
        val allowed = tags.any { tag -> stack.`is`(tag) } || patternAllowed(stack, patterns)
        return allowed && !patternAllowed(stack, excludePatterns)
    }

    private fun patternAllowed(stack: ItemStack, patterns: List<String>): Boolean {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return patterns.any { pattern ->
            val trimmed = pattern.trim()
            when {
                trimmed.isBlank() -> false
                trimmed.startsWith("#") -> stack.`is`(itemTag(trimmed))
                else -> globMatches(trimmed, itemId)
            }
        }
    }

    private fun globMatches(pattern: String, value: String): Boolean {
        val regex = pattern.split('*').joinToString(".*") { part -> Regex.escape(part) }
        return Regex("^$regex$", RegexOption.IGNORE_CASE).matches(value)
    }

    private data class ActiveClassEquipment(val id: String, val name: String, val perks: List<RolePerkDefinition>) {
        fun allowsWeapon(stack: ItemStack): Boolean = perks.any { perk -> weaponAllowed(stack, perk) }

        fun allowsArmor(stack: ItemStack): Boolean = perks.any { perk -> armorAllowed(stack, perk) }
    }
}
