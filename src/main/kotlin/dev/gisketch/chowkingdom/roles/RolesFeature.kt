package dev.gisketch.chowkingdom.roles

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.CropBlock
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
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

    fun register(modBus: IEventBus) {
        RolesConfig.load()
        RoleStore.load()
        modBus.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onFarmlandTrample)
        NeoForge.EVENT_BUS.addListener(::onBlockDrops)
        NeoForge.EVENT_BUS.addListener(::onLivingDamagePre)
        NeoForge.EVENT_BUS.addListener(::onPlayerTickPost)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        RolesConfig.load()
        RoleStore.load()
        event.server.playerList.players.forEach { player -> ensureAndGrant(player) }
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        ensureAndGrant(event.entity as? ServerPlayer ?: return)
    }

    private fun ensureAndGrant(player: ServerPlayer) {
        RoleStore.ensureDefaults(player)
        grantStartingItems(player)
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
                        ),
                ),
        )
    }

    private fun reloadRoles(context: CommandContext<CommandSourceStack>): Int {
        RolesConfig.load()
        RoleStore.load()
        context.source.server.playerList.players.forEach { player -> ensureAndGrant(player) }
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
        context.source.sendSuccess({ Component.literal("${player.gameProfile.name}: job=${record.jobId}, class=${record.classId}") }, false)
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
        grantStartingItems(player)
        context.source.sendSuccess({ Component.literal("Set ${player.gameProfile.name} class to ${role.displayName.ifBlank { role.id }}.") }, true)
        return 1
    }

    private fun onFarmlandTrample(event: BlockEvent.FarmlandTrampleEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (activeJob(player).perks.any { perk -> perk.type == "prevent_crop_trample" }) {
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
        val perk = equipmentAffinity(attacker) ?: return
        val held = attacker.mainHandItem
        if (held.isEmpty || itemAllowed(held, tagList(perk.weaponTag, perk.weaponTags), perk.weaponPatterns)) return
        event.newDamage = (event.newDamage * perk.wrongWeaponDamageMultiplier.coerceIn(0.0, 1.0).toFloat()).coerceAtLeast(0.0f)
        val cooldown = perk.wrongWeaponCooldownTicks.coerceAtLeast(0)
        if (cooldown > 0) attacker.cooldowns.addCooldown(held.item, cooldown)
    }

    private fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val perk = equipmentAffinity(player) ?: return
        if (!perk.wrongArmorDisablesSprint) return
        val tags = tagList(perk.armorTag, perk.armorTags)
        if (player.armorSlots.any { stack -> !stack.isEmpty && !itemAllowed(stack, tags, perk.armorPatterns) }) {
            player.isSprinting = false
        }
    }

    private fun grantStartingItems(player: ServerPlayer) {
        val classId = RoleStore.classId(player)
        val role = RolesConfig.roleClass(classId) ?: return
        val items = role.perks.filter { perk -> perk.type == "starting_items" }.flatMap { perk -> perk.startingItems }
        if (items.isEmpty() || !RoleStore.markStartingItemsGranted(player.uuid, classId)) return
        items.mapNotNull(::stackFromId).forEach { stack -> if (!player.inventory.add(stack)) player.drop(stack, false) }
    }

    private fun activeJob(player: ServerPlayer): RoleDefinition = RolesConfig.job(RoleStore.jobId(player)) ?: RoleDefinition()

    private fun activeClass(player: ServerPlayer): RoleDefinition = RolesConfig.roleClass(RoleStore.classId(player)) ?: RoleDefinition()

    private fun equipmentAffinity(player: ServerPlayer): RolePerkDefinition? = activeClass(player).perks.firstOrNull { perk -> perk.type == "equipment_affinity" }

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