package dev.gisketch.chowkingdom.roles

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.event.level.BlockDropsEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.block.CropGrowEvent

internal object BotanistPerks {
    fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val level = player.level() as? ServerLevel ?: return
        if (event.placedBlock.block !is CropBlock) return
        val growthChance = RolePerks.seasonalFarmerGrowthChance(player)
        if (growthChance <= 0.0) return
        BotanistPlantingData.get(player.server).mark(level, event.pos, growthChance)
    }

    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        val level = player.level() as? ServerLevel ?: return
        BotanistPlantingData.get(level.server).remove(level, event.pos)
    }

    fun onCropGrowPre(event: CropGrowEvent.Pre) {
        val level = event.level as? ServerLevel ?: return
        val crop = event.state.block as? CropBlock ?: return
        if (crop.isMaxAge(event.state)) return
        val growthChance = BotanistPlantingData.get(level.server).growthChance(level, event.pos)
        if (growthChance <= 0.0 || level.random.nextDouble() >= growthChance) return
        if (!SereneSeasonSupport.isFavoredSeasonCrop(level, event.pos, event.state)) return
        event.setResult(CropGrowEvent.Pre.Result.GROW)
    }

    fun onBlockDrops(event: BlockDropsEvent) {
        val player = event.breaker as? ServerPlayer ?: return
        val bonusDropChance = RolePerks.configuredJobChance(player, "crop_bonus_drop_chance")
        val qualityUpgradeChance = RolePerks.configuredJobChance(player, "quality_harvest_upgrade_chance")
        val legacyQualityMultiplier = RolePerks.qualityFoodHarvestMultiplier(player)
        val canApply = bonusDropChance > 0.0 || qualityUpgradeChance > 0.0 || legacyQualityMultiplier > 1.0
        if (!canApply || !isMatureCropDrop(event)) return
        event.drops.forEach { entity -> applyDrop(entity, player, bonusDropChance, qualityUpgradeChance, legacyQualityMultiplier) }
    }

    private fun applyDrop(entity: ItemEntity, player: ServerPlayer, bonusDropChance: Double, qualityUpgradeChance: Double, legacyQualityMultiplier: Double) {
        if (bonusDropChance > 0.0 && player.random.nextDouble() < bonusDropChance) entity.item.grow(1)
        if (qualityUpgradeChance > 0.0 && player.random.nextDouble() < qualityUpgradeChance) QualityFoodRoleSupport.tryUpgradeQuality(entity.item, player)
        applyLegacyQualityHarvest(entity.item, player, legacyQualityMultiplier)
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
}
