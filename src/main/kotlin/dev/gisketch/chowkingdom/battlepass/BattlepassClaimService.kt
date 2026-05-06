package dev.gisketch.chowkingdom.battlepass

import dev.gisketch.chowkingdom.wallets.ChowcoinNetwork
import dev.gisketch.chowkingdom.wallets.ChowcoinStore
import dev.gisketch.chowkingdom.snackbar.SnackbarIcons
import dev.gisketch.chowkingdom.snackbar.SnackbarNetwork
import dev.gisketch.chowkingdom.snackbar.SnackbarNotification
import dev.gisketch.chowkingdom.snackbar.SnackbarSounds
import dev.gisketch.chowkingdom.snackbar.SnackbarType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object BattlepassClaimService {
    fun claim(player: ServerPlayer, passId: String, tierXp: Int): Boolean {
        val pass = BattlepassPassRegistry.get(passId) ?: run {
            player.displayClientMessage(Component.literal("Unknown battlepass pass."), true)
            return false
        }
        val tier = pass.progression.firstOrNull { progression -> progression.xp == tierXp }

        if (tier == null) {
            player.displayClientMessage(Component.literal("Unknown tier $tierXp for ${pass.displayName}."), true)
            return false
        }
        if (BattlepassXpStore.isClaimed(player, pass.id, tier.xp)) {
            player.displayClientMessage(Component.literal("Already claimed ${pass.displayName} ${tier.xp} XP reward."), true)
            return false
        }

        val currentXp = BattlepassXpStore.getXp(player, pass.id)
        if (currentXp < tier.xp) {
            player.displayClientMessage(Component.literal("${tier.xp - currentXp} XP needed for ${pass.displayName} ${tier.xp} XP reward."), true)
            return false
        }

        tier.rewards.forEach { reward -> giveReward(player, reward) }
        BattlepassXpStore.markClaimed(player, pass.id, tier.xp)
        SnackbarNetwork.send(player, SnackbarNotification.item(rewardIcon(tier.rewards.firstOrNull()), "BATTLEPASS REWARD CLAIMED", "${pass.displayName} ${tier.xp} XP reward", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return true
    }

    fun claimAll(player: ServerPlayer, passId: String): Int {
        val pass = BattlepassPassRegistry.get(passId) ?: run {
            player.displayClientMessage(Component.literal("Unknown battlepass pass."), true)
            return 0
        }
        val currentXp = BattlepassXpStore.getXp(player, pass.id)
        val claimableTiers = pass.progression
            .sortedBy { tier -> tier.xp }
            .filter { tier -> currentXp >= tier.xp && !BattlepassXpStore.isClaimed(player, pass.id, tier.xp) }

        if (claimableTiers.isEmpty()) {
            player.displayClientMessage(Component.literal("No ${pass.displayName} rewards ready to claim."), true)
            return 0
        }

        claimableTiers.forEach { tier ->
            tier.rewards.forEach { reward -> giveReward(player, reward) }
            BattlepassXpStore.markClaimed(player, pass.id, tier.xp)
        }
        SnackbarNetwork.send(player, SnackbarNotification.item(SnackbarIcons.BATTLEPASS, "BATTLEPASS REWARDS CLAIMED", "Claimed ${claimableTiers.size} ${pass.displayName} reward(s)", SnackbarType.SUCCESS, SnackbarSounds.REWARD))
        return claimableTiers.size
    }

    private fun giveReward(player: ServerPlayer, reward: BattlepassRewardDefinition) {
        if (isChowcoinReward(reward)) {
            ChowcoinStore.add(player, chowcoinAmount(reward))
            ChowcoinNetwork.syncTo(player)
            return
        }
        if (reward.type != "item") return
        val item = runCatching { ResourceLocation.parse(reward.item) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) }
            ?: Items.AIR
        if (item == Items.AIR) return

        val stack = ItemStack(item, reward.quantity.coerceAtLeast(1))
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
    }

    private fun isChowcoinReward(reward: BattlepassRewardDefinition): Boolean {
        if (reward.type.equals("chowcoin", ignoreCase = true) || reward.type.equals("chowcoins", ignoreCase = true)) return true
        return reward.type.equals("currency", ignoreCase = true) && reward.data["currency"]?.equals("chowcoin", ignoreCase = true) == true
    }

    private fun chowcoinAmount(reward: BattlepassRewardDefinition): Long = reward.data["amount"]?.toLongOrNull() ?: reward.quantity.toLong()

    private fun rewardIcon(reward: BattlepassRewardDefinition?): String {
        if (reward == null) return SnackbarIcons.BATTLEPASS
        if (isChowcoinReward(reward)) return "minecraft:gold_ingot"
        return reward.item.takeIf(String::isNotBlank) ?: SnackbarIcons.BATTLEPASS
    }
}