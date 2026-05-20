package dev.gisketch.chowkingdom.npc

import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class CampingBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(NpcFeature.CAMPING_BLOCK_ENTITY.get(), pos, state), GeoBlockEntity {
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "idle", 0) { state -> state.setAndContinue(IDLE) })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    companion object {
        private val IDLE: RawAnimation = RawAnimation.begin().thenLoop("idle")
    }
}
