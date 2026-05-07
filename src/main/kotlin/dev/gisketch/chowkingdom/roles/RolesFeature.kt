package dev.gisketch.chowkingdom.roles

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.CropBlock
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockDropsEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import kotlin.math.roundToInt

object RolesFeature {
    private val JOB_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(RolesConfig.jobs().map { role -> role.id }, builder)
    }
    private val CLASS_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(RolesConfig.classes().map { role -> role.id }, builder)
    }
    private val WRONG_WEAPON_ATTACK_SPEED_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:wrong_weapon_attack_speed")

    fun register(modBus: IEventBus) {
        RolesConfig.load()
        RoleStore.load()
        RolesNetwork.register(modBus)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onFarmlandTrample)
        NeoForge.EVENT_BUS.addListener(::onBlockDrops)
        NeoForge.EVENT_BUS.addListener(::onLivingDamagePre)
        NeoForge.EVENT_BUS.addListener(::onPlayerTickPost)
        NeoForge.EVENT_BUS.addListener(::onItemTooltip)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        RolesConfig.load()
        RoleStore.load()
        event.server.playerList.players.forEach { player -> syncAndMaybeOpenOnboarding(player) }
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        syncAndMaybeOpenOnboarding(event.entity as? ServerPlayer ?: return)
    }

    private fun syncAndMaybeOpenOnboarding(player: ServerPlayer) {
        RoleStore.ensureRecord(player)
        grantStartingItems(player)
        RolesNetwork.syncTo(player, openOnboarding = RoleStore.needsOnboarding(player))
    }

    fun applyOnboardingChoice(player: ServerPlayer, jobId: String, classId: String): Boolean {
        if (!RoleStore.needsOnboarding(player)) {
            RolesNetwork.syncTo(player, openOnboarding = false)
            return false
        }
        val job = RolesConfig.job(jobId)
        val roleClass = RolesConfig.roleClass(classId)
        if (job == null || roleClass == null) {
            RolesNetwork.syncTo(player, openOnboarding = true)
            return false
        }
        RoleStore.setPrimaryRoles(player, job.id, roleClass.id)
        grantStartingItems(player, roleClass.id)
        RolesNetwork.syncTo(player, openOnboarding = false)
        return true
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("ck")
                .then(
                    Commands.literal("roles")
                        .requires { source -> source.hasPermission(2) }
                        .then(Commands.literal("reload").executes(::reloadRoles))
                        .then(Commands.literal("list").executes(::listRoles))
                        .then(
                            Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player()).executes(::getRoles)),
                        )
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.literal("job")
                                        .then(
                                            Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("job", StringArgumentType.word()).suggests(JOB_SUGGESTIONS).executes(::setJob)),
                                        ),
                                )
                                .then(
                                    Commands.literal("class")
                                        .then(
                                            Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASS_SUGGESTIONS).executes(::setClass)),
                                        ),
                                ),
                        )
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.literal("job")
                                        .then(
                                            Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("job", StringArgumentType.word()).suggests(JOB_SUGGESTIONS).executes(::addJob)),
                                        ),
                                )
                                .then(
                                    Commands.literal("class")
                                        .then(
                                            Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASS_SUGGESTIONS).executes(::addClass)),
                                        ),
                                ),
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.literal("job")
                                        .then(
                                            Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("job", StringArgumentType.word()).suggests(JOB_SUGGESTIONS).executes(::removeJob)),
                                        ),
                                )
                                .then(
                                    Commands.literal("class")
                                        .then(
                                            Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASS_SUGGESTIONS).executes(::removeClass)),
                                        ),
                                ),
                        ),
                ),
        )
    }

    private fun reloadRoles(context: CommandContext<CommandSourceStack>): Int {
        RolesConfig.load()
        RoleStore.load()
        context.source.server.playerList.players.forEach { player -> syncAndMaybeOpenOnboarding(player) }
        context.source.sendSuccess({ Component.literal("Reloaded ${RolesConfig.jobs().size} jobs and ${RolesConfig.classes().size} classes.") }, true)
        return 1
    }

    private fun listRoles(context: CommandContext<CommandSourceStack>): Int {
        val jobs = RolesConfig.jobs().joinToString(", ") { role -> role.id }
        val classes = RolesConfig.classes().joinToString(", ") { role -> role.id }
        context.source.sendSuccess({ Component.literal("Jobs: $jobs | Classes: $classes") }, false)
        return 1
    }

    private fun getRoles(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val record = RoleStore.role(player)
        val jobs = record.activeJobIds.joinToString(", ").ifBlank { record.jobId }
        val classes = record.activeClassIds.joinToString(", ").ifBlank { record.classId }
        context.source.sendSuccess({ Component.literal("${player.gameProfile.name}: jobs=[$jobs], classes=[$classes]") }, false)
        return 1
    }

    private fun setJob(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val jobId = StringArgumentType.getString(context, "job")
        val role = RolesConfig.job(jobId) ?: run {
            context.source.sendFailure(Component.literal("Unknown job: $jobId"))
            return 0
        }
        RoleStore.setJob(player, role.id)
        RolesNetwork.syncTo(player, openOnboarding = false)
        context.source.sendSuccess({ Component.literal("Set ${player.gameProfile.name} job to ${role.displayName.ifBlank { role.id }}.") }, true)
        return 1
    }

    private fun setClass(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val classId = StringArgumentType.getString(context, "class")
        val role = RolesConfig.roleClass(classId) ?: run {
            context.source.sendFailure(Component.literal("Unknown class: $classId"))
            return 0
        }
        RoleStore.setClass(player, role.id)
        grantStartingItems(player, role.id)
        RolesNetwork.syncTo(player, openOnboarding = false)
        context.source.sendSuccess({ Component.literal("Set ${player.gameProfile.name} class to ${role.displayName.ifBlank { role.id }}.") }, true)
        return 1
    }

    private fun addJob(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val jobId = StringArgumentType.getString(context, "job")
        val role = RolesConfig.job(jobId) ?: run {
            context.source.sendFailure(Component.literal("Unknown job: $jobId"))
            return 0
        }
        val changed = RoleStore.addJob(player, role.id)
        RolesNetwork.syncTo(player, openOnboarding = false)
        val message = if (changed) "Added" else "Already has"
        context.source.sendSuccess({ Component.literal("$message ${role.displayName.ifBlank { role.id }} job for ${player.gameProfile.name}.") }, true)
        return if (changed) 1 else 0
    }

    private fun addClass(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val classId = StringArgumentType.getString(context, "class")
        val role = RolesConfig.roleClass(classId) ?: run {
            context.source.sendFailure(Component.literal("Unknown class: $classId"))
            return 0
        }
        val changed = RoleStore.addClass(player, role.id)
        grantStartingItems(player, role.id)
        RolesNetwork.syncTo(player, openOnboarding = false)
        val message = if (changed) "Added" else "Already has"
        context.source.sendSuccess({ Component.literal("$message ${role.displayName.ifBlank { role.id }} class for ${player.gameProfile.name}.") }, true)
        return if (changed) 1 else 0
    }

    private fun removeJob(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val jobId = StringArgumentType.getString(context, "job")
        RolesConfig.job(jobId) ?: run {
            context.source.sendFailure(Component.literal("Unknown job: $jobId"))
            return 0
        }
        if (!RoleStore.removeJob(player, jobId)) {
            context.source.sendFailure(Component.literal("${player.gameProfile.name} does not have job $jobId active."))
            return 0
        }
        RolesNetwork.syncTo(player, openOnboarding = false)
        context.source.sendSuccess({ Component.literal("Removed $jobId job from ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun removeClass(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val classId = StringArgumentType.getString(context, "class")
        RolesConfig.roleClass(classId) ?: run {
            context.source.sendFailure(Component.literal("Unknown class: $classId"))
            return 0
        }
        if (!RoleStore.removeClass(player, classId)) {
            context.source.sendFailure(Component.literal("${player.gameProfile.name} does not have class $classId active."))
            return 0
        }
        RolesNetwork.syncTo(player, openOnboarding = false)
        context.source.sendSuccess({ Component.literal("Removed $classId class from ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun onFarmlandTrample(event: BlockEvent.FarmlandTrampleEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (RolePerks.jobPerks(player, "prevent_crop_trample").isNotEmpty()) {
            event.isCanceled = true
        }
    }

    private fun onBlockDrops(event: BlockDropsEvent) {
        val player = event.breaker as? ServerPlayer ?: return
        val multiplier = RolePerks.qualityFoodHarvestMultiplier(player)
        if (multiplier <= 1.0 || event.state.block !is CropBlock) return
        val extraChance = (multiplier - 1.0).coerceIn(0.0, 10.0)
        event.drops.forEach { entity ->
            var remainingChance = extraChance
            while (remainingChance >= 1.0) {
                QualityFoodRoleSupport.tryApplyQuality(entity.item, player)
                remainingChance -= 1.0
            }
            if (player.random.nextDouble() < remainingChance) QualityFoodRoleSupport.tryApplyQuality(entity.item, player)
        }
    }

    private fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
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

    private fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val perks = equipmentAffinities(player)
        applyWrongWeaponAttackSpeed(player, perks)
        val armorPerks = perks.filter { perk -> perk.wrongArmorDisablesSprint }
        if (armorPerks.isNotEmpty() && player.armorSlots.any { stack -> !stack.isEmpty && armorPerks.none { perk -> itemAllowed(stack, tagList(perk.armorTag, perk.armorTags), perk.armorPatterns) } }) {
            player.isSprinting = false
        }
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

    private fun onItemTooltip(event: ItemTooltipEvent) {
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
            stack.item is ArmorItem && perks.none { perk -> itemAllowed(stack, tagList(perk.armorTag, perk.armorTags), perk.armorPatterns) } -> {
                event.toolTip.add(Component.literal("$className cannot wear this armor well.").withStyle(ChatFormatting.RED))
                event.toolTip.add(Component.literal("Sprinting is disabled while worn.").withStyle(ChatFormatting.RED))
            }
        }
    }

    private fun grantStartingItems(player: ServerPlayer) {
        RoleStore.activeClassIds(player).forEach { classId -> grantStartingItems(player, classId) }
    }

    private fun grantStartingItems(player: ServerPlayer, classId: String) {
        val role = RolesConfig.roleClass(classId) ?: return
        val items = role.perks.filter { perk -> perk.type == "starting_items" }.flatMap { perk -> perk.startingItems }
        val stacks = items.mapNotNull(::stackFromId)
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

    private fun stackFromId(raw: String): ItemStack? {
        val parts = raw.split("*", limit = 2)
        val id = parts[0].trim()
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(Items.AIR)
        return item.takeIf { value -> value != Items.AIR }?.let { value -> ItemStack(value, count) }
    }
}
