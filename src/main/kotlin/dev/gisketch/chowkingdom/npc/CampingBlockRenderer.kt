package dev.gisketch.chowkingdom.npc

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoBlockRenderer

class CampingBlockRenderer : GeoBlockRenderer<CampingBlockEntity>(CampingBlockModel()) {
    override fun renderRecursively(
        poseStack: PoseStack,
        animatable: CampingBlockEntity,
        bone: GeoBone,
        renderType: RenderType,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        color: Int,
    ) {
        val boneTexture = when (bone.name) {
            "frame" -> FRAME_TEXTURE
            "fabric" -> FABRIC_TEXTURE
            else -> null
        }
        if (boneTexture == null) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color)
            return
        }
        val isFabric = bone.name == "fabric"
        val boneRenderType = if (isFabric) RenderType.entityCutoutNoCull(boneTexture) else RenderType.entityCutout(boneTexture)
        val boneColor = if (isFabric) LEATHER_FABRIC_COLOR else color
        super.renderRecursively(poseStack, animatable, bone, boneRenderType, bufferSource, bufferSource.getBuffer(boneRenderType), isReRender, partialTick, packedLight, packedOverlay, boneColor)
    }

    companion object {
        private val FABRIC_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/block/tent/tent.png")
        private val FRAME_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/block/tent/frame.png")
        private const val LEATHER_FABRIC_COLOR: Int = -0x1000000 or 0xA9745B
    }
}

@Suppress("OVERRIDE_DEPRECATION")
private class CampingBlockModel : GeoModel<CampingBlockEntity>() {
    override fun getModelResource(animatable: CampingBlockEntity): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "geo/block/camping_block.geo.json")

    override fun getTextureResource(animatable: CampingBlockEntity): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "textures/block/tent/tent.png")

    override fun getAnimationResource(animatable: CampingBlockEntity): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "animations/camping_block.animation.json")
}
