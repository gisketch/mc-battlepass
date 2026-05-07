package dev.gisketch.chowkingdom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Invoker("<init>")
    static GuiGraphics chowkingdom$create(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        throw new AssertionError();
    }
}
