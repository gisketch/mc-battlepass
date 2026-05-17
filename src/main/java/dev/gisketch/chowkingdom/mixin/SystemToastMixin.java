package dev.gisketch.chowkingdom.mixin;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SystemToast.class)
public class SystemToastMixin {
    @Inject(method = "addOrUpdate", at = @At("HEAD"), cancellable = true)
    private static void chowkingdom$hideUnsecureChatToast(ToastComponent toastComponent, SystemToast.SystemToastId toastId, Component title, Component message, CallbackInfo callback) {
        if (title.getContents() instanceof TranslatableContents contents && "multiplayer.unsecureserver.toast.title".equals(contents.getKey())) {
            callback.cancel();
        }
    }
}
