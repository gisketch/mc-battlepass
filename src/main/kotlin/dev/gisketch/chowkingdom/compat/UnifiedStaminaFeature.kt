package dev.gisketch.chowkingdom.compat

import com.mojang.brigadier.context.CommandContext
import dev.gisketch.chowkingdom.roles.RoleClassEquipmentRules
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.UUID

object UnifiedStaminaFeature {
    private val attackSpendTicks: MutableMap<UUID, Long> = mutableMapOf()
    private val blockSpendTicks: MutableMap<UUID, Long> = mutableMapOf()
    private val epicFightSkillSpendTicks: MutableMap<UUID, Long> = mutableMapOf()
    private val epicFightGuardSpendTicks: MutableMap<UUID, Long> = mutableMapOf()
    private val pendingDrains: MutableMap<UUID, MutableList<PendingStaminaDrain>> = mutableMapOf()

    fun register() {
        val config = StaminaCompatConfig.load()
        ExternalStaminaConfigPatcher.apply(config)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onAttackEntity)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onLivingDamagePre)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ::onLivingDamagePost)
        NeoForge.EVENT_BUS.addListener(::onLivingIncomingDamage)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onPlayerTickPost)
    }

    fun spendEpicFightAttack(player: ServerPlayer, cost: Double): Boolean = spendOncePerTick(player, adjustedAttackCost(player, cost), attackSpendTicks)

    fun spendEpicFightSkill(player: ServerPlayer, cost: Double): Boolean = spendOncePerTick(player, cost, epicFightSkillSpendTicks)

    fun spendEpicFightGuard(player: ServerPlayer, cost: Double): Boolean = spendOncePerTick(player, cost, epicFightGuardSpendTicks)

    fun spendEpicFightBlock(player: ServerPlayer, cost: Double): Boolean = spendOncePerTick(player, cost, blockSpendTicks)

    fun giveStamina(player: ServerPlayer, amount: Double): Boolean = ParagliderStaminaBridge.give(player, amount)

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("ck")
                .then(
                    Commands.literal("stamina")
                        .requires { source -> source.hasPermission(2) }
                        .then(Commands.literal("reload").executes(::reloadStaminaConfig)),
                ),
        )
    }

    private fun reloadStaminaConfig(context: CommandContext<CommandSourceStack>): Int {
        val config = StaminaCompatConfig.load()
        ExternalStaminaConfigPatcher.apply(config)
        EpicFightStaminaBridge.reset()
        context.source.server.playerList.players.forEach(EpicFightStaminaBridge::attach)
        context.source.sendSuccess({ Component.literal("Stamina compatibility config reloaded.").withStyle(ChatFormatting.GREEN) }, true)
        return 1
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        val config = StaminaCompatConfig.load()
        ExternalStaminaConfigPatcher.apply(config)
    }

    private fun onAttackEntity(event: AttackEntityEvent) {
        val config = StaminaCompatConfig.values()
        if (!config.enabled) return
        val attacker = event.entity as? ServerPlayer ?: return
        if (!isLivingAttackTarget(event.target)) return
        if (!isWeaponLike(attacker.mainHandItem)) return
        if (!spendEpicFightAttack(attacker, config.attackCost)) event.isCanceled = true
    }

    private fun onLivingDamagePre(event: LivingDamageEvent.Pre) {
        val config = StaminaCompatConfig.values()
        if (!config.enabled) return
        handleShieldNParryAttempt(event, config)
    }

    private fun onLivingDamagePost(event: LivingDamageEvent.Pre) {
        val config = StaminaCompatConfig.values()
        if (!config.enabled) return
        val player = event.entity as? ServerPlayer ?: return
        if (event.originalDamage <= 0.0f || event.newDamage >= event.originalDamage) return
        if (ShieldNParryStaminaBridge.consumeSuccessfulParry(player, player.level().gameTime)) {
            giveStamina(player, config.shieldNParrySuccessGain)
        }
    }

    private fun onLivingIncomingDamage(event: LivingIncomingDamageEvent) {
        val config = StaminaCompatConfig.values()
        if (!config.enabled) return
        val player = event.entity as? ServerPlayer ?: return
        if (!player.isBlocking) return
        spendEpicFightBlock(player, config.blockedHitCost)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        EpicFightStaminaBridge.detach(player)
        ShieldNParryStaminaBridge.clear(player)
        CombatRollStaminaBridge.clear(player)
        attackSpendTicks.remove(player.uuid)
        blockSpendTicks.remove(player.uuid)
        epicFightSkillSpendTicks.remove(player.uuid)
        epicFightGuardSpendTicks.remove(player.uuid)
        pendingDrains.remove(player.uuid)
    }

    private fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val config = StaminaCompatConfig.values()
        if (!config.enabled) return
        val player = event.entity as? ServerPlayer ?: return
        processPendingDrains(player, config)
        CombatRollStaminaBridge.onPlayerTick(player, config)
        EpicFightStaminaBridge.attach(player)
        if (config.disableEpicFightBattleModeWhileParagliding) {
            EpicFightStaminaBridge.updateParaglidingBattleMode(
                player,
                ParagliderStaminaBridge.isParaglidingInAir(player),
                config.restoreEpicFightBattleModeAfterParagliding,
            )
        }
        if (config.refillEpicFightStamina) EpicFightStaminaBridge.refill(player)
    }

    private fun spendOncePerTick(player: ServerPlayer, cost: Double, spendTicks: MutableMap<UUID, Long>): Boolean {
        val gameTime = player.level().gameTime
        if (spendTicks[player.uuid] == gameTime) return true
        val config = StaminaCompatConfig.values()
        val spent = queueStaminaDrain(player, cost, config)
        if (spent) {
            spendTicks[player.uuid] = gameTime
            ParagliderStaminaBridge.setRecoveryDelay(player, config.paragliderRecoveryDelayTicksAfterSpend)
        }
        return spent
    }

    private fun adjustedAttackCost(player: ServerPlayer, cost: Double): Double {
        if (!RoleClassEquipmentRules.isWrongWeapon(player)) return cost
        val maxStamina = ParagliderStaminaBridge.maxStamina(player) ?: return cost
        val minCost = maxStamina * StaminaCompatConfig.values().wrongWeaponAttackMinCostPercent.coerceAtLeast(0.0)
        return maxOf(cost, minCost)
    }

    private fun queueStaminaDrain(player: ServerPlayer, cost: Double, config: StaminaCompatDefinition): Boolean {
        if (cost <= 0.0) return true
        val drainTicks = config.staminaDrainTicks.coerceAtLeast(1)
        if (drainTicks == 1) return ParagliderStaminaBridge.spend(player, cost)
        val drains = pendingDrains.getOrPut(player.uuid) { mutableListOf() }
        val pending = drains.sumOf { drain -> drain.remaining }
        if (ParagliderStaminaBridge.available(player) + 0.001 < pending + cost) return false
        drains += PendingStaminaDrain(cost, drainTicks)
        return true
    }

    private fun handleShieldNParryAttempt(event: LivingDamageEvent.Pre, config: StaminaCompatDefinition) {
        val player = event.entity as? ServerPlayer ?: return
        if (!ShieldNParryStaminaBridge.hasActiveParry(player)) return
        if (spendEpicFightBlock(player, config.shieldNParryAttemptCost)) return
        ShieldNParryStaminaBridge.clearActiveParry(player)
    }

    private fun isLivingAttackTarget(target: Entity): Boolean = target is LivingEntity && target.isAlive

    private fun isWeaponLike(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        var weaponLike = false
        stack.forEachModifier(EquipmentSlot.MAINHAND) { attribute, _ ->
            if (attribute == Attributes.ATTACK_DAMAGE || attribute == Attributes.ATTACK_SPEED) weaponLike = true
        }
        return weaponLike
    }

    private fun processPendingDrains(player: ServerPlayer, config: StaminaCompatDefinition) {
        val drains = pendingDrains[player.uuid] ?: return
        val iterator = drains.listIterator()
        while (iterator.hasNext()) {
            val drain = iterator.next()
            val chunk = drain.remaining / drain.ticksRemaining.coerceAtLeast(1)
            if (!ParagliderStaminaBridge.spend(player, chunk)) {
                iterator.remove()
                continue
            }
            drain.remaining -= chunk
            drain.ticksRemaining -= 1
            ParagliderStaminaBridge.setRecoveryDelay(player, config.paragliderRecoveryDelayTicksAfterSpend)
            if (drain.remaining <= 0.001 || drain.ticksRemaining <= 0) iterator.remove()
        }
        if (drains.isEmpty()) pendingDrains.remove(player.uuid)
    }

    private data class PendingStaminaDrain(var remaining: Double, var ticksRemaining: Int)
}