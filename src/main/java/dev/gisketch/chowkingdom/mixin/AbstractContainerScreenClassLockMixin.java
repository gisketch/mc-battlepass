package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.roles.RoleEquipmentOverlayClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenClassLockMixin<T extends AbstractContainerMenu> extends Screen {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    protected AbstractContainerScreenClassLockMixin(Component title) {
        super(title);
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void chowkingdom$renderClassLock(GuiGraphics guiGraphics, Slot slot, CallbackInfo callback) {
        RoleEquipmentOverlayClient.renderContainerSlotOverlay(guiGraphics, slot, slot.x, slot.y);
    }
}
