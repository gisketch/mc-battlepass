package dev.gisketch.chowkingdom.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int chowkingdom_getLeftPos();

    @Accessor("topPos")
    int chowkingdom_getTopPos();
}