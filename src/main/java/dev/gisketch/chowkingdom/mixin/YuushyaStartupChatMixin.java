package dev.gisketch.chowkingdom.mixin;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class YuushyaStartupChatMixin {
    private static final String YUUSHYA_STARTUP_MESSAGE =
        "Thank you for your support and understanding of the Yuushya series. Let's work together to safeguard the creative environment!";

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void chowkingdom$hideYuushyaStartupMessage(Component message, CallbackInfo callback) {
        if (chowkingdom$isYuushyaStartupMessage(message)) {
            callback.cancel();
        }
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chowkingdom$hideYuushyaStartupMessage(
        Component message,
        MessageSignature signature,
        GuiMessageTag tag,
        CallbackInfo callback
    ) {
        if (chowkingdom$isYuushyaStartupMessage(message)) {
            callback.cancel();
        }
    }

    private static boolean chowkingdom$isYuushyaStartupMessage(Component message) {
        if (message == null) {
            return false;
        }

        String text = message.getString();
        return YUUSHYA_STARTUP_MESSAGE.equals(text) || chowkingdom$containsYuushyaWarning(text);
    }

    static boolean chowkingdom$containsYuushyaWarning(String text) {
        if (text == null) {
            return false;
        }

        String normalized = text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);

        return normalized.contains("thank you for your support")
            && normalized.contains("yuushya")
            && normalized.contains("safeguard")
            && normalized.contains("creative environment");
    }
}
