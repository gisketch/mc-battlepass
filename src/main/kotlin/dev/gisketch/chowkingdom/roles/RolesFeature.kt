package dev.gisketch.chowkingdom.roles

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.recipes.RecipeDisablerFeature
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.CropBlock
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockDropsEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.block.CropGrowEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier
import java.util.Locale
import kotlin.math.roundToInt

object RolesFeature {
    private val MOB_EFFECTS: DeferredRegister<MobEffect> = DeferredRegister.create(Registries.MOB_EFFECT, ChowKingdomMod.MOD_ID)
    private val JOB_STATUS_EFFECTS: List<DeferredHolder<MobEffect, JobRankMobEffect>> = (0 until MAX_JOB_STATUS_EFFECTS).map { slot ->
        MOB_EFFECTS.register("job_status_${slot + 1}", Supplier { JobRankMobEffect(slot) })
    }

    private val JOB_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(RolesConfig.jobs().map { role -> role.id }, builder)
    }
    private val CLASS_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(RolesConfig.classes().map { role -> role.id }, builder)
    }
    private val WRONG_WEAPON_ATTACK_SPEED_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:wrong_weapon_attack_speed")

    fun register(modBus: IEventBus) {
        MOB_EFFECTS.register(modBus)
        RolesConfig.load()
        RoleStore.load()
        RolesNetwork.register(modBus)
        if (FMLEnvironment.dist.isClient) registerClientHooks()
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onFarmlandTrample)
        NeoForge.EVENT_BUS.addListener(::onBlockPlace)
        NeoForge.EVENT_BUS.addListener(::onBlockBreak)
        NeoForge.EVENT_BUS.addListener(::onCropGrowPre)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ::onBlockDrops)
        NeoForge.EVENT_BUS.addListener(::onLivingDamagePre)
        NeoForge.EVENT_BUS.addListener(::onPlayerTickPost)
        NeoForge.EVENT_BUS.addListener(::onItemTooltip)
    }

    fun jobStatusEffectIndex(instance: MobEffectInstance): Int? = JOB_STATUS_EFFECTS.indexOfFirst { effect -> instance.`is`(effect) }.takeIf { index -> index >= 0 }

    private fun registerClientHooks() {
        runCatching {
            val client = Class.forName("dev.gisketch.chowkingdom.roles.RolesClient")
            client.getMethod("register").invoke(client.getField("INSTANCE").get(null))
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to register roles client hooks", exception)
        }
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        RolesConfig.load()
        RoleStore.load()
        val onboardingPlayers = event.server.playerList.players.onEach { player ->
            RoleStore.ensureRecord(player)
            grantStartingItems(player)
        }.filter(RoleStore::needsOnboarding).map { player -> player.uuid }.toSet()
        RolesNetwork.syncAllPlayers(openOnboardingFor = onboardingPlayers)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        syncAndMaybeOpenOnboarding(event.entity as? ServerPlayer ?: return)
    }

    private fun syncAndMaybeOpenOnboarding(player: ServerPlayer) {
        RoleStore.ensureRecord(player)
        grantStartingItems(player)
        applyJobRankEffect(player)
        val onboardingPlayers = if (RoleStore.needsOnboarding(player)) setOf(player.uuid) else emptySet()
        RolesNetwork.syncAllPlayers(openOnboardingFor = onboardingPlayers)
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
        applyJobRankEffect(player)
        RolesNetwork.syncAllPlayers()
        return true
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("ck")
                .then(Commands.literal("onboarding").requires { source -> source.hasPermission(2) }.executes(::resetOnboarding))
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
                            Commands.literal("debug")
                                .then(
                                    Commands.literal("catch-rate")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(::debugCatchRate)),
                                )
                                .then(
                                    Commands.literal("mount-speed")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(::debugMountSpeed)),
                                ),
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

    private fun resetOnboarding(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        RoleStore.resetOnboarding(player)
        RolesNetwork.syncAllPlayers(openOnboardingFor = setOf(player.uuid))
        context.source.sendSuccess({ Component.literal("Cleared your jobs and classes. Reopened onboarding.") }, true)
        return 1
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

    private fun debugCatchRate(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val debug = JobPerkDebug.lastCatchRate(player)
        if (debug == null) {
            context.source.sendSuccess({ Component.literal("${player.gameProfile.name}: no Cobblemon catch-rate throw recorded yet.") }, false)
            return 1
        }
        val types = debug.pokemonTypes.sorted().joinToString(", ").ifBlank { "unknown" }
        val jobs = debug.activeJobIds.joinToString(", ").ifBlank { "none" }
        val perks = debug.appliedPerks.joinToString(", ") { entry ->
            val roleName = entry.roleDisplayName.ifBlank { entry.roleId }
            "$roleName:${entry.pokemonType ?: "any"} Rank ${entry.jobLevel} ${formatBonusPercent(entry.bonusPercent)} (${formatMultiplier(entry.multiplier)}x)"
        }.ifBlank { "none" }
        context.source.sendSuccess(
            {
                Component.literal(
                    "${debug.playerName}: ${debug.species} types=[$types] overallLv=${debug.overallLevel} jobRank=${debug.jobLevel} base=${formatCatchRate(debug.baseCatchRate)} final=${formatCatchRate(debug.finalCatchRate)} modifier=${formatBonusPercent(debug.multiplier - 1.0)} (${formatMultiplier(debug.multiplier)}x)",
                )
            },
            false,
        )
        context.source.sendSuccess({ Component.literal("jobs=[$jobs] perks=[$perks]") }, false)
        return 1
    }

    private fun debugMountSpeed(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        val debug = JobPerkDebug.lastMountSpeed(player)
        if (debug == null) {
            context.source.sendSuccess({ Component.literal("${player.gameProfile.name}: no Cobblemon mount-speed ride recorded yet.") }, false)
            return 1
        }
        val types = debug.pokemonTypes.sorted().joinToString(", ").ifBlank { "unknown" }
        val jobs = debug.activeJobIds.joinToString(", ").ifBlank { "none" }
        val perks = debug.appliedPerks.joinToString(", ") { entry ->
            val roleName = entry.roleDisplayName.ifBlank { entry.roleId }
            "$roleName:${entry.pokemonType ?: "any"} Rank ${entry.jobLevel} ${formatBonusPercent(entry.bonusPercent)} (${formatMultiplier(entry.multiplier)}x)"
        }.ifBlank { "none" }
        val speeds = debug.styleSpeeds.joinToString(", ") { speed ->
            "${speed.style} ${formatMountSpeed(speed.baseSpeed)}->${formatMountSpeed(speed.finalSpeed)}"
        }.ifBlank { "none" }
        context.source.sendSuccess(
            {
                Component.literal(
                    "${debug.playerName}: ${debug.species} types=[$types] overallLv=${debug.overallLevel} jobRank=${debug.jobLevel} modifier=${formatBonusPercent(debug.multiplier - 1.0)} (${formatMultiplier(debug.multiplier)}x) speeds=[$speeds]",
                )
            },
            false,
        )
        context.source.sendSuccess({ Component.literal("jobs=[$jobs] perks=[$perks]") }, false)
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
        RolesNetwork.syncAllPlayers()
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
        RolesNetwork.syncAllPlayers()
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
        RolesNetwork.syncAllPlayers()
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
        RolesNetwork.syncAllPlayers()
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
        RolesNetwork.syncAllPlayers()
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
        RolesNetwork.syncAllPlayers()
        context.source.sendSuccess({ Component.literal("Removed $classId class from ${player.gameProfile.name}.") }, true)
        return 1
    }

    private fun onFarmlandTrample(event: BlockEvent.FarmlandTrampleEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (RolePerks.jobPerks(player, "prevent_crop_trample").isNotEmpty() || RolePerks.jobPerks(player, "gentle_steps").isNotEmpty()) {
            event.isCanceled = true
        }
    }

    private fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val level = player.level() as? ServerLevel ?: return
        if (event.placedBlock.block !is CropBlock) return
        val growthChance = RolePerks.seasonalFarmerGrowthChance(player)
        if (growthChance <= 0.0) return
        BotanistPlantingData.get(player.server).mark(level, event.pos, growthChance)
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val level = event.player.level() as? ServerLevel ?: return
        val server = event.player.server ?: return
        BotanistPlantingData.get(server).remove(level, event.pos)
    }

    private fun onCropGrowPre(event: CropGrowEvent.Pre) {
        val level = event.level as? ServerLevel ?: return
        val crop = event.state.block as? CropBlock ?: return
        if (crop.isMaxAge(event.state)) return
        val growthChance = BotanistPlantingData.get(level.server).growthChance(level, event.pos)
        if (growthChance <= 0.0 || level.random.nextDouble() >= growthChance) return
        if (!SereneSeasonSupport.isFavoredSeasonCrop(level, event.pos, event.state)) return
        event.setResult(CropGrowEvent.Pre.Result.GROW)
    }

    private fun onBlockDrops(event: BlockDropsEvent) {
        val player = event.breaker as? ServerPlayer ?: return
        if (!isMatureCropDrop(event)) return
        val bonusDropChance = RolePerks.configuredJobChance(player, "crop_bonus_drop_chance")
        val qualityUpgradeChance = RolePerks.configuredJobChance(player, "quality_harvest_upgrade_chance")
        val legacyQualityMultiplier = RolePerks.qualityFoodHarvestMultiplier(player)
        if (bonusDropChance <= 0.0 && qualityUpgradeChance <= 0.0 && legacyQualityMultiplier <= 1.0) return
        event.drops.forEach { entity ->
            if (bonusDropChance > 0.0 && player.random.nextDouble() < bonusDropChance) entity.item.grow(1)
            if (qualityUpgradeChance > 0.0 && player.random.nextDouble() < qualityUpgradeChance) QualityFoodRoleSupport.tryUpgradeQuality(entity.item, player)
            applyLegacyQualityHarvest(entity.item, player, legacyQualityMultiplier)
        }
    }

    private fun isMatureCropDrop(event: BlockDropsEvent): Boolean {
        val crop = event.state.block as? CropBlock ?: return false
        return crop.isMaxAge(event.state)
    }

    private fun applyLegacyQualityHarvest(stack: ItemStack, player: ServerPlayer, multiplier: Double) {
        if (multiplier <= 1.0) return
        var remainingChance = (multiplier - 1.0).coerceIn(0.0, 10.0)
        while (remainingChance >= 1.0) {
            QualityFoodRoleSupport.tryApplyQuality(stack, player)
            remainingChance -= 1.0
        }
        if (player.random.nextDouble() < remainingChance) QualityFoodRoleSupport.tryApplyQuality(stack, player)
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
        applyJobRankEffect(player)
        val armorPerks = perks.filter { perk -> perk.wrongArmorDisablesSprint }
        if (armorPerks.isNotEmpty() && player.armorSlots.any { stack -> !stack.isEmpty && !RecipeDisablerFeature.isCosmeticized(stack) && armorPerks.none { perk -> itemAllowed(stack, tagList(perk.armorTag, perk.armorTags), perk.armorPatterns) } }) {
            player.isSprinting = false
        }
    }

    private fun applyJobRankEffect(player: ServerPlayer) {
        val jobRank = JobLevels.jobLevel(player)
        val activeJobCount = RoleStore.activeJobIds(player).size
        JOB_STATUS_EFFECTS.forEachIndexed { index, effect ->
            if (jobRank <= 0 || index >= activeJobCount) {
                player.removeEffect(effect)
                return@forEachIndexed
            }
            val amplifier = (jobRank - 1).coerceAtLeast(0)
            val current = player.getEffect(effect)
            if (current?.amplifier == amplifier && current.isInfiniteDuration) return@forEachIndexed
            player.addEffect(MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false, true))
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
            stack.item is ArmorItem && !RecipeDisablerFeature.isCosmeticized(stack) && perks.none { perk -> itemAllowed(stack, tagList(perk.armorTag, perk.armorTags), perk.armorPatterns) } -> {
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

    private fun formatCatchRate(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun formatMountSpeed(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun formatMultiplier(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun formatBonusPercent(bonusPercent: Double): String = String.format(Locale.ROOT, "%+.1f%%", bonusPercent * 100.0)

    private const val MAX_JOB_STATUS_EFFECTS = 2
}
