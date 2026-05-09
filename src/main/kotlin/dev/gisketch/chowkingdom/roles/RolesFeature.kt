package dev.gisketch.chowkingdom.roles

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent
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

    fun register(modBus: IEventBus) {
        MOB_EFFECTS.register(modBus)
        RolesConfig.load()
        RoleStore.load()
        RolesNetwork.register(modBus)
        LiveDebugHelper.register(modBus)
        RolesDebug.registerProviders()
        if (FMLEnvironment.dist.isClient) registerClientHooks()
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onFarmlandTrample)
        NeoForge.EVENT_BUS.addListener(::onBlockPlace)
        NeoForge.EVENT_BUS.addListener(::onBlockBreak)
        NeoForge.EVENT_BUS.addListener(::onCropGrowPre)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ::onBlockDrops)
        NeoForge.EVENT_BUS.addListener(::onBreakSpeed)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ::onItemFished)
        NeoForge.EVENT_BUS.addListener(::onLivingDamagePre)
        NeoForge.EVENT_BUS.addListener(::onLivingDeath)
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
            RoleClassEquipmentRules.grantStartingItems(player)
        }.filter(RoleStore::needsOnboarding).map { player -> player.uuid }.toSet()
        RolesNetwork.syncAllPlayers(openOnboardingFor = onboardingPlayers)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        syncAndMaybeOpenOnboarding(event.entity as? ServerPlayer ?: return)
    }

    private fun syncAndMaybeOpenOnboarding(player: ServerPlayer) {
        RoleStore.ensureRecord(player)
        RoleClassEquipmentRules.grantStartingItems(player)
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
        RoleClassEquipmentRules.grantStartingItems(player, roleClass.id)
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
                                )
                                .then(
                                    Commands.literal("botanist")
                                        .executes(::debugBotanist)
                                        .then(Commands.argument("player", EntityArgument.player()).executes(::debugBotanist)),
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
        return RolesDebug.toggle(context, player, "catch-rate", "Catch-rate")
    }

    private fun debugMountSpeed(context: CommandContext<CommandSourceStack>): Int {
        val player = EntityArgument.getPlayer(context, "player")
        return RolesDebug.toggle(context, player, "mount-speed", "Mount-speed")
    }

    private fun debugBotanist(context: CommandContext<CommandSourceStack>): Int {
        val player = runCatching { EntityArgument.getPlayer(context, "player") }.getOrElse { context.source.playerOrException }
        return RolesDebug.toggle(context, player, "botanist", "Botanist")
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
        RoleClassEquipmentRules.grantStartingItems(player, role.id)
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
        RoleClassEquipmentRules.grantStartingItems(player, role.id)
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
        BotanistPerks.onBlockPlace(event)
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        BotanistPerks.onBlockBreak(event)
        EngineerPerks.onBlockBreak(player, event.state)
    }

    private fun onCropGrowPre(event: CropGrowEvent.Pre) {
        BotanistPerks.onCropGrowPre(event)
    }

    private fun onBlockDrops(event: BlockDropsEvent) {
        BotanistPerks.onBlockDrops(event)
        BugScoutPerks.onBlockDrops(event)
    }

    private fun onBreakSpeed(event: PlayerEvent.BreakSpeed) {
        EngineerPerks.onBreakSpeed(event)
        DiverPerks.onBreakSpeed(event)
        MountaineerPerks.onBreakSpeed(event)
    }

    private fun onItemFished(event: ItemFishedEvent) {
        DiverPerks.onItemFished(event)
    }

    private fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
        (event.entity as? ServerPlayer)?.let { player -> MagmaScoutPerks.onLivingDamage(player, event) }
        (event.entity as? ServerPlayer)?.let { player -> FalconerPerks.onLivingDamage(player, event) }
        (event.entity as? ServerPlayer)?.let { player -> EsperPerks.onLivingDamage(player, event) }
        (event.entity as? ServerPlayer)?.let { player -> MountaineerPerks.onLivingDamage(player, event) }
        BugScoutPerks.onLivingDamage(event)
        ShadeRunnerPerks.onLivingDamage(event)
        MartialArtistPerks.onLivingDamage(event)
        ShinobiPerks.onLivingDamage(event)
        RoleClassEquipmentRules.onLivingDamagePre(event)
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        MartialArtistPerks.onLivingDeath(event)
    }

    private fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        RoleClassEquipmentRules.onPlayerTick(player)
        DiverPerks.onPlayerTick(player)
        EngineerPerks.onPlayerTick(player)
        FieldResearcherPerks.onPlayerTick(player)
        BugScoutPerks.onPlayerTick(player)
        FalconerPerks.onPlayerTick(player)
        ShadeRunnerPerks.onPlayerTick(player)
        EsperPerks.onPlayerTick(player)
        MartialArtistPerks.onPlayerTick(player)
        MountaineerPerks.onPlayerTick(player)
        ShinobiPerks.onPlayerTick(player)
        applyJobRankEffect(player)
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

    private fun onItemTooltip(event: ItemTooltipEvent) {
        RoleClassEquipmentRules.onItemTooltip(event)
    }

    private const val MAX_JOB_STATUS_EFFECTS = 2
}
