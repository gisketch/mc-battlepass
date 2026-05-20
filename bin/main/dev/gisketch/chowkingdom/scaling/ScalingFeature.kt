package dev.gisketch.chowkingdom.scaling

import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.bosses.BossEventsConfig
import dev.gisketch.chowkingdom.bosses.BossEventsFeature
import dev.gisketch.chowkingdom.shipping.ShippingBinStore
import dev.gisketch.chowkingdom.shops.SellerData
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import dev.gisketch.chowkingdom.npc.ChowNpcEntity
import dev.gisketch.chowkingdom.npc.NpcPokemonCompanions
import dev.gisketch.chowkingdom.tech.TechLicenseFeature
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import kotlin.math.sqrt

object ScalingFeature {
    private val HEALTH_MODIFIER = ResourceLocation.parse("${ChowKingdomMod.MOD_ID}:progressive_health")
    private const val STATE_TAG = "CkdmScaling"
    private val tracked: MutableMap<java.util.UUID, TrackedScalingEntity> = linkedMapOf()
    private val nextDangerWarningAt: MutableMap<String, Long> = linkedMapOf()
    private var cachedShippingTotal = 0L
    private var nextShippingRefreshAt = 0L

    fun register() {
        ScalingConfig.load()
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onEntityJoinLevel)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ::onLivingDamage)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onPlayerTick)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    fun invalidateShippingCache() {
        nextShippingRefreshAt = 0L
    }

    private fun onServerStarted(event: net.neoforged.neoforge.event.server.ServerStartedEvent) {
        ScalingConfig.load()
        tracked.clear()
        refreshShippingTotal(event.server, force = true)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        refreshShippingTotal((event.entity as? ServerPlayer)?.server ?: return, force = true)
    }

    private fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        if (event.level.isClientSide) return
        val entity = event.entity as? LivingEntity ?: return
        applyScaling(entity, "join")
    }

    private fun onLivingDamage(event: LivingDamageEvent.Pre) {
        val target = event.entity
        if (!target.level().isClientSide) applyScaling(target, "damage_target")
        val attacker = event.source.entity as? LivingEntity ?: return
        if (attacker.level().isClientSide) return
        applyScaling(attacker, "damage_attacker")
        val multiplier = attacker.persistentData.getCompound(STATE_TAG).getDouble("DamageMultiplier")
        if (multiplier > 1.0001) event.newDamage = (event.newDamage * multiplier.toFloat()).coerceAtLeast(0.0f)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val now = event.server.overworld().gameTime
        refreshShippingTotal(event.server)
        tracked.entries.removeIf { (_, trackedEntity) ->
            val entity = trackedEntity.entity
            if (!entity.isAlive || entity.isRemoved || entity.level().isClientSide) return@removeIf true
            if (now >= trackedEntity.nextReapplyAt) {
                applyScaling(entity, "reapply")
            }
            false
        }
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val settings = ScalingConfig.settings()
        if (!settings.enabled || !settings.warningEnabled || player.level().gameTime % settings.warningIntervalTicks != 0L) return
        warnDangerBand(player)
    }

    private fun applyScaling(entity: LivingEntity, reason: String) {
        val breakdown = evaluate(entity)
        if (!breakdown.eligible || (!breakdown.appliesHealth() && !breakdown.appliesDamage())) {
            removeHealthModifier(entity)
            entity.persistentData.remove(STATE_TAG)
            tracked.remove(entity.uuid)
            return
        }
        if (breakdown.appliesHealth()) applyHealth(entity, breakdown)
        writeState(entity, breakdown, reason)
        val interval = if (breakdown.mode == "boss") ScalingConfig.settings().bossReapplyTicks else ScalingConfig.settings().mobReapplyTicks
        tracked[entity.uuid] = TrackedScalingEntity(entity, entity.level().gameTime + interval)
    }

    private fun evaluate(entity: LivingEntity): ScalingBreakdown {
        val settings = ScalingConfig.settings()
        val entityId = entityId(entity)
        if (!settings.enabled) return ScalingBreakdown(false, "none", entityId, "scaling disabled")
        val bossEntry = BossEventsConfig.entryForEntity(entityId)
        exclusionReason(entity, entityId, bossEntry != null)?.let { reason ->
            return ScalingBreakdown(false, if (bossEntry != null) "boss" else "mob", entityId, reason)
        }
        val level = entity.level() as? ServerLevel ?: return ScalingBreakdown(false, "none", entityId, "not server level")
        val shippingTotal = refreshShippingTotal(level.server)
        if (bossEntry != null) {
            val bosses = ScalingConfig.bosses()
            val participants = nearbyPlayers(entity, bosses.participantRadius)
            return ScalingRules.bossBreakdown(
                bosses,
                bosses.overrideFor(bossEntry.id, entityId),
                entityId,
                bossEntry.id,
                shippingTotal,
                participants,
            )
        }
        val mobs = ScalingConfig.mobs()
        if (!mobs.enabled) return ScalingBreakdown(false, "mob", entityId, "mob scaling disabled")
        if (entity !is Monster && entity.type.category != MobCategory.MONSTER) {
            return ScalingBreakdown(false, "mob", entityId, "not hostile monster")
        }
        val dimensionId = level.dimension().location().toString()
        val dimension = ScalingRules.dimension(mobs, dimensionId) ?: DimensionScalingEntry(dimensionId, true, 1.0, 1.0)
        if (!dimension.enabled) return ScalingBreakdown(false, "mob", entityId, "dimension disabled")
        val distance = distanceFromAnchor(level, entity)
        val band = ScalingRules.distanceBand(mobs, distance)
        val players = nearbyPlayers(entity, mobs.nearbyPlayerRadius)
        val safe = distance <= mobs.safeRadius
        val healthMultiplier = ScalingRules.mobHealthMultiplier(mobs, shippingTotal, dimension, band, players, safe)
        val damageMultiplier = ScalingRules.mobDamageMultiplier(mobs, shippingTotal, dimension, band, players, safe)
        return ScalingBreakdown(
            eligible = healthMultiplier > 1.0001 || damageMultiplier > 1.0001,
            mode = "mob",
            entityId = entityId,
            shippingTotal = shippingTotal,
            distance = distance,
            bandId = band.id,
            participantCount = players,
            shippingHealthMultiplier = if (mobs.shippingScalingEnabled && !safe) ScalingRules.valueAt(mobs.shippingHealth, shippingTotal, mobs.interpolation) else 1.0,
            dimensionHealthMultiplier = dimension.healthMultiplier,
            distanceHealthMultiplier = band.healthMultiplier,
            playerHealthMultiplier = ScalingRules.playerHealthMultiplier(mobs.playerCount, players),
            healthMultiplier = healthMultiplier,
            damageMultiplier = damageMultiplier,
            maxTotalHealthMultiplier = mobs.maxHealthMultiplier,
        )
    }

    private fun applyHealth(entity: LivingEntity, breakdown: ScalingBreakdown) {
        val attribute = entity.getAttribute(Attributes.MAX_HEALTH) ?: return
        val previousMax = entity.maxHealth.coerceAtLeast(1.0f)
        val previousHealth = entity.health.coerceIn(0.0f, previousMax)
        val ratio = if (previousMax > 0.0f) previousHealth / previousMax else 1.0f
        attribute.removeModifier(HEALTH_MODIFIER)
        val nativeMax = attribute.value.coerceAtLeast(1.0)
        val cappedDesired = (nativeMax * breakdown.healthMultiplier + breakdown.flatHealth)
            .coerceAtMost(nativeMax * breakdown.maxTotalHealthMultiplier.coerceAtLeast(1.0))
            .coerceAtLeast(nativeMax)
        val delta = cappedDesired - nativeMax
        if (delta > 0.0001) {
            attribute.addOrUpdateTransientModifier(
                AttributeModifier(
                    HEALTH_MODIFIER,
                    delta,
                    AttributeModifier.Operation.ADD_VALUE,
                ),
            )
        }
        val newMax = entity.maxHealth.coerceAtLeast(1.0f)
        entity.setHealth((newMax * ratio).coerceIn(1.0f.coerceAtMost(newMax), newMax))
    }

    private fun removeHealthModifier(entity: LivingEntity) {
        val attribute = entity.getAttribute(Attributes.MAX_HEALTH) ?: return
        if (!attribute.hasModifier(HEALTH_MODIFIER)) return
        val previousMax = entity.maxHealth.coerceAtLeast(1.0f)
        val ratio = (entity.health / previousMax).coerceIn(0.0f, 1.0f)
        attribute.removeModifier(HEALTH_MODIFIER)
        val newMax = entity.maxHealth.coerceAtLeast(1.0f)
        entity.setHealth((newMax * ratio).coerceIn(1.0f.coerceAtMost(newMax), newMax))
    }

    private fun writeState(entity: LivingEntity, breakdown: ScalingBreakdown, reason: String) {
        entity.persistentData.put(
            STATE_TAG,
            CompoundTag().also { tag ->
                tag.putString("Mode", breakdown.mode)
                tag.putString("EntityId", breakdown.entityId)
                tag.putString("Band", breakdown.bandId)
                tag.putString("Reason", reason)
                tag.putLong("ShippingTotal", breakdown.shippingTotal)
                tag.putInt("ParticipantCount", breakdown.participantCount)
                tag.putDouble("Distance", breakdown.distance)
                tag.putDouble("HealthMultiplier", breakdown.healthMultiplier)
                tag.putDouble("FlatHealth", breakdown.flatHealth)
                tag.putDouble("DamageMultiplier", breakdown.damageMultiplier)
                tag.putDouble("AppliedMaxHealth", entity.maxHealth.toDouble())
            },
        )
    }

    private fun exclusionReason(entity: LivingEntity, entityId: String, configuredBoss: Boolean): String? {
        val exclusions = ScalingConfig.exclusions()
        if (exclusions.excludePlayers && entity is Player) return "player"
        if (exclusions.excludeNpcs && entity is ChowNpcEntity) return "ckdm npc"
        if (exclusions.excludeVendorSellers && SellerData.isSeller(entity)) return "vendor seller"
        if (exclusions.excludeCobblemon && (entityId.substringBefore(':') == "cobblemon" || entity.javaClass.name.contains("cobblemon", ignoreCase = true))) return "cobblemon"
        if (exclusions.excludeCobblemon && NpcPokemonCompanions.isCompanion(entity)) return "npc pokemon companion"
        if (exclusions.excludeTamed && entity is TamableAnimal && entity.isTame) return "tamed"
        if (exclusions.excludeOwned && ownerUuid(entity) != null) return "owned"
        if (entityId in exclusions.entityIds) return "entity id excluded"
        val namespace = entityId.substringBefore(':')
        if (namespace in exclusions.namespaces) return "namespace excluded"
        if (exclusions.classNameContains.any { entity.javaClass.name.contains(it, ignoreCase = true) }) return "class excluded"
        if (exclusions.persistentDataKeys.any { entity.persistentData.contains(it) }) return "persistent data excluded"
        if (matchesExcludedTag(entity, exclusions.entityTags)) return "entity tag excluded"
        if (!configuredBoss && exclusions.excludePassiveNonBosses && entity.type.category != MobCategory.MONSTER && entity !is Monster) return "passive"
        return null
    }

    private fun matchesExcludedTag(entity: LivingEntity, tags: List<String>): Boolean =
        tags.any { tagId ->
            runCatching {
                entity.type.`is`(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(tagId)))
            }.getOrDefault(false)
        }

    private fun ownerUuid(entity: Entity): java.util.UUID? =
        runCatching { entity.javaClass.getMethod("getOwnerUUID").invoke(entity) as? java.util.UUID }.getOrNull()

    private fun nearbyPlayers(entity: LivingEntity, radius: Double): Int {
        val level = entity.level() as? ServerLevel ?: return 1
        val radiusSqr = radius * radius
        return level.players().count { player ->
            player.isAlive && !player.isSpectator && player.distanceToSqr(entity) <= radiusSqr
        }.coerceAtLeast(1)
    }

    private fun distanceFromAnchor(level: ServerLevel, entity: LivingEntity): Double {
        val dimension = level.dimension().location().toString()
        val anchors = ScalingConfig.mobs().anchors.filter { it.dimension == dimension }
        val candidates = if (anchors.isNotEmpty()) {
            anchors.map { it.x to it.z }
        } else {
            val spawn = level.sharedSpawnPos
            listOf((spawn.x + 0.5) to (spawn.z + 0.5))
        }
        return candidates.minOf { (x, z) ->
            val dx = entity.x - x
            val dz = entity.z - z
            sqrt(dx * dx + dz * dz)
        }
    }

    private fun refreshShippingTotal(server: MinecraftServer, force: Boolean = false): Long {
        val now = server.overworld().gameTime
        if (force || now >= nextShippingRefreshAt) {
            cachedShippingTotal = ShippingBinStore.totalChowcoinsSold()
            nextShippingRefreshAt = now + ScalingConfig.settings().cacheRefreshTicks
        }
        return cachedShippingTotal
    }

    private fun warnDangerBand(player: ServerPlayer) {
        val mobs = ScalingConfig.mobs()
        if (!mobs.enabled) return
        val level = player.level() as? ServerLevel ?: return
        val dimension = ScalingRules.dimension(mobs, level.dimension().location().toString()) ?: return
        if (!dimension.enabled) return
        val distance = distanceFromAnchor(level, player)
        if (distance <= mobs.safeRadius) return
        val band = ScalingRules.distanceBand(mobs, distance)
        val shipping = if (mobs.shippingScalingEnabled) ScalingRules.valueAt(mobs.shippingHealth, refreshShippingTotal(player.server), mobs.interpolation) else 1.0
        val effective = (shipping * band.healthMultiplier * dimension.healthMultiplier).coerceAtLeast(1.0)
        if (effective < 1.25) return
        val key = "${player.stringUUID}:${level.dimension().location()}:${band.id}"
        val now = level.gameTime
        if ((nextDangerWarningAt[key] ?: 0L) > now) return
        nextDangerWarningAt[key] = now + ScalingConfig.settings().warningCooldownTicks
        SnackbarNetwork.send(
            player,
            SnackbarNotification.item(
                "minecraft:iron_sword",
                "DANGER RISES",
                "This area is ${String.format(java.util.Locale.US, "%.1fx", effective)} baseline danger.",
                SnackbarType.GENERIC,
                SnackbarSounds.GENERIC,
            ),
        )
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("ck").then(root()))
        event.dispatcher.register(Commands.literal("chowkingdom").then(root()))
    }

    private fun root() = Commands.literal("scaling")
        .then(Commands.literal("status").requires { it.hasPermission(2) }.executes(::status))
        .then(Commands.literal("reload").requires { it.hasPermission(2) }.executes(::reload))
        .then(Commands.literal("refresh").requires { it.hasPermission(2) }.executes(::refreshLoaded))
        .then(Commands.literal("refresh-loaded").requires { it.hasPermission(2) }.executes(::refreshLoaded))
        .then(Commands.literal("inspect").requires { it.hasPermission(2) }.executes(::inspect))
        .then(
            Commands.literal("debug")
                .requires { it.hasPermission(2) && ScalingConfig.settings().debugCommands }
                .then(Commands.literal("total").then(Commands.argument("amount", LongArgumentType.longArg(0L)).executes(::debugTotal))),
        )

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val total = refreshShippingTotal(context.source.server, force = true)
        val mobBase = ScalingRules.valueAt(ScalingConfig.mobs().shippingHealth, total, ScalingConfig.mobs().interpolation)
        val bossFlat = ScalingRules.valueAt(ScalingConfig.bosses().shippingFlatHealth, total, ScalingConfig.bosses().interpolation)
        context.source.sendSuccess(
            {
                Component.literal("Scaling: enabled=${ScalingConfig.settings().enabled} total=$total mob_base=${fmt(mobBase)} boss_flat=${fmt(bossFlat)} tracked=${tracked.size}")
                    .withStyle(ChatFormatting.GREEN)
            },
            false,
        )
        return 1
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        ScalingConfig.load()
        invalidateShippingCache()
        tracked.values.toList().forEach { applyScaling(it.entity, "reload") }
        context.source.sendSuccess({ Component.literal("Reloaded CKDM scaling configs.").withStyle(ChatFormatting.GREEN) }, true)
        return 1
    }

    private fun refreshLoaded(context: CommandContext<CommandSourceStack>): Int {
        refreshShippingTotal(context.source.server, force = true)
        var checked = 0
        var scaled = 0
        context.source.server.allLevels.forEach { level ->
            level.allEntities.forEach { entity ->
                val living = entity as? LivingEntity ?: return@forEach
                checked += 1
                applyScaling(living, "command_refresh")
                if (living.persistentData.contains(STATE_TAG, CompoundTag.TAG_COMPOUND.toInt())) scaled += 1
            }
        }
        context.source.sendSuccess(
            {
                Component.literal("Refreshed scaling for loaded entities: checked=$checked scaled=$scaled.").withStyle(ChatFormatting.GREEN)
            },
            true,
        )
        return scaled.coerceAtLeast(1)
    }

    private fun debugTotal(context: CommandContext<CommandSourceStack>): Int {
        val amount = LongArgumentType.getLong(context, "amount")
        val total = ShippingBinStore.debugSetTotalChowcoinsSold(amount)
        refreshShippingTotal(context.source.server, force = true)
        BossEventsFeature.checkShippingUnlocks(context.source.server)
        TechLicenseFeature.checkShippingUnlocks(context.source.server)
        context.source.sendSuccess({ Component.literal("Set total shipped Chowcoins to $total for scaling debug.").withStyle(ChatFormatting.YELLOW) }, true)
        return 1
    }

    private fun inspect(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val target = findLookTarget(player)
        if (target == null) {
            context.source.sendFailure(Component.literal("No living entity in view within ${ScalingConfig.settings().inspectRange.toInt()} blocks."))
            return 0
        }
        applyScaling(target, "inspect")
        val breakdown = evaluate(target)
        val state = target.persistentData.getCompound(STATE_TAG)
        val lines = listOf(
            "Scaling inspect ${target.displayName?.string ?: target.type.description.string} (${entityId(target)})",
            "eligible=${breakdown.eligible} mode=${breakdown.mode} reason=${breakdown.excludedReason.ifBlank { state.getString("Reason") }}",
            "shipping=${breakdown.shippingTotal} band=${breakdown.bandId} distance=${fmt(breakdown.distance)} players=${breakdown.participantCount}",
            "health=${fmt(breakdown.healthMultiplier)} flat=${fmt(breakdown.flatHealth)} damage=${fmt(breakdown.damageMultiplier)}",
            "current=${fmt(target.health.toDouble())}/${fmt(target.maxHealth.toDouble())} applied_max=${fmt(state.getDouble("AppliedMaxHealth"))}",
        )
        lines.forEach { line -> context.source.sendSuccess({ Component.literal(line) }, false) }
        return 1
    }

    private fun findLookTarget(player: ServerPlayer): LivingEntity? {
        val range = ScalingConfig.settings().inspectRange
        val eye = player.eyePosition
        val look = player.lookAngle.normalize()
        return player.level().getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(range)) { entity ->
            entity !== player && entity.isAlive && !entity.isSpectator
        }.mapNotNull { entity ->
            val center = center(entity)
            val toTarget = center.subtract(eye)
            val distance = toTarget.length()
            if (distance <= 0.0 || distance > range) return@mapNotNull null
            val dot = toTarget.normalize().dot(look)
            if (dot < 0.92) return@mapNotNull null
            entity to (distance - dot * 4.0)
        }.minByOrNull { it.second }?.first
    }

    private fun center(entity: Entity): Vec3 {
        val box = entity.boundingBox
        return Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5)
    }

    private fun entityId(entity: LivingEntity): String = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
}

private data class TrackedScalingEntity(
    val entity: LivingEntity,
    var nextReapplyAt: Long,
)
