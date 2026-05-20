package dev.gisketch.chowkingdom.npc

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.gisketch.chowkingdom.roles.BODY_MODEL_GIRL
import dev.gisketch.chowkingdom.roles.normalizeBodyModel
import dev.gisketch.chowkingdom.roles.normalizeFemaleGenderBustSize
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f

class NpcFemaleGenderLayer<M : PlayerModel<ChowNpcEntity>>(parent: RenderLayerParent<ChowNpcEntity, M>) : RenderLayer<ChowNpcEntity, M>(parent) {
    override fun render(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        entity: ChowNpcEntity,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTick: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float,
    ) {
        if (!NpcFemaleGenderBridge.available()) return

        val body = effectiveBody(entity)
        if (normalizeBodyModel(body.model) != BODY_MODEL_GIRL) return
        val bust = normalizeFemaleGenderBustSize(body.bust).toFloat()
        if (bust < MIN_RENDER_BUST_SIZE) return

        val vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)))
        val overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0f)
        val visualSize = breastVisualSize(bust)
        val depth = breastDepth(bust)

        poseStack.pushPose()
        getParentModel().body.translateAndRotate(poseStack)
        poseStack.translate(0.0f, FEMALE_GENDER_BODY_Y, FEMALE_GENDER_BODY_Z)
        poseStack.mulPose(Quaternionf().rotationXYZ((-35.0f * visualSize).toRadians(), 0.0f, 0.0f))
        renderBreast(poseStack, vertexConsumer, packedLight, overlay, left = true, depth = depth)
        renderBreast(poseStack, vertexConsumer, packedLight, overlay, left = false, depth = depth)
        poseStack.popPose()
    }
}

private fun effectiveBody(entity: ChowNpcEntity): NpcFemaleGenderBody {
    val definition = NpcConfig.get(entity.npcId)
    return if (definition == null) {
        NpcFemaleGenderBody(entity.bodyModel, entity.fgBustSize)
    } else {
        NpcFemaleGenderBody(definition.bodyModel, definition.fgBustSize)
    }
}

private fun renderBreast(poseStack: PoseStack, vertexConsumer: VertexConsumer, packedLight: Int, overlay: Int, left: Boolean, depth: Float) {
    val x1 = if (left) -4.0f else 0.0f
    val x2 = x1 + 4.0f
    val y1 = 0.0f
    val y2 = 5.0f
    val z1 = 0.0f
    val z2 = depth
    val u1 = (if (left) 20.0f else 24.0f) / SKIN_TEXTURE_SIZE
    val u2 = u1 + 4.0f / SKIN_TEXTURE_SIZE
    val v1 = 20.0f / SKIN_TEXTURE_SIZE
    val v2 = 25.0f / SKIN_TEXTURE_SIZE

    val backTopOuter = BreastVertex(x1, y1, z1, u1, v1)
    val backTopInner = BreastVertex(x2, y1, z1, u2, v1)
    val backBottomInner = BreastVertex(x2, y2, z1, u2, v2)
    val backBottomOuter = BreastVertex(x1, y2, z1, u1, v2)
    val frontTopOuter = BreastVertex(x1, y1, z2, u1, v1)
    val frontTopInner = BreastVertex(x2, y1, z2, u2, v1)
    val frontBottomInner = BreastVertex(x2, y2, z2, u2, v2)
    val frontBottomOuter = BreastVertex(x1, y2, z2, u1, v2)

    quad(poseStack, vertexConsumer, packedLight, overlay, backTopInner, backTopOuter, backBottomOuter, backBottomInner)
    quad(poseStack, vertexConsumer, packedLight, overlay, frontTopInner, backTopInner, backBottomInner, frontBottomInner)
    quad(poseStack, vertexConsumer, packedLight, overlay, backTopOuter, frontTopOuter, frontBottomOuter, backBottomOuter)
    quad(poseStack, vertexConsumer, packedLight, overlay, frontTopInner, frontTopOuter, backTopOuter, backTopInner)
    quad(poseStack, vertexConsumer, packedLight, overlay, backBottomInner, backBottomOuter, frontBottomOuter, frontBottomInner)
}

private fun quad(
    poseStack: PoseStack,
    vertexConsumer: VertexConsumer,
    packedLight: Int,
    overlay: Int,
    a: BreastVertex,
    b: BreastVertex,
    c: BreastVertex,
    d: BreastVertex,
) {
    val pose = poseStack.last()
    val normal = normalFor(a, b, c, pose.normal())
    listOf(a, b, c, d).forEach { vertex ->
        vertexConsumer.addVertex(pose.pose(), vertex.x / MODEL_UNIT, vertex.y / MODEL_UNIT, vertex.z / MODEL_UNIT)
            .setColor(WHITE)
            .setUv(vertex.u, vertex.v)
            .setOverlay(overlay)
            .setLight(packedLight)
            .setNormal(normal.x, normal.y, normal.z)
    }
}

private fun normalFor(a: BreastVertex, b: BreastVertex, c: BreastVertex, normalMatrix: Matrix3f): Vector3f {
    val ab = Vector3f(b.x - a.x, b.y - a.y, b.z - a.z)
    val ac = Vector3f(c.x - a.x, c.y - a.y, c.z - a.z)
    return ab.cross(ac).normalize().mul(normalMatrix)
}

private fun breastDepth(bust: Float): Float = when {
    bust >= 0.84f -> 5.0f
    bust >= 0.72f -> 4.0f
    else -> 3.0f
}

private fun breastVisualSize(bust: Float): Float = (bust + kotlin.math.abs(bust - 0.7f)).coerceIn(0.25f, 1.0f)

private fun Float.toRadians(): Float = (this * (Math.PI / 180.0)).toFloat()

private data class NpcFemaleGenderBody(val model: String, val bust: Double)

private data class BreastVertex(val x: Float, val y: Float, val z: Float, val u: Float, val v: Float)

private const val MODEL_UNIT = 16.0f
private const val SKIN_TEXTURE_SIZE = 64.0f
private const val FEMALE_GENDER_BODY_Y = 0.0875f
private const val FEMALE_GENDER_BODY_Z = -0.125f
private const val MIN_RENDER_BUST_SIZE = 0.02f
private const val WHITE = -1
