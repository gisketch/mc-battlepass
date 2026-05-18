package dev.gisketch.chowkingdom.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class YuushyaStartupPacketMixin {
    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void chowkingdom$hideYuushyaSystemChat(ClientboundSystemChatPacket packet, CallbackInfo callback) {
        if (packet != null && YuushyaStartupChatMixin.chowkingdom$containsYuushyaWarning(packet.content().getString())) {
            callback.cancel();
        }
    }

    @Inject(method = "handleDisguisedChat", at = @At("HEAD"), cancellable = true)
    private void chowkingdom$hideYuushyaDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo callback) {
        if (packet != null && YuushyaStartupChatMixin.chowkingdom$containsYuushyaWarning(packet.message().getString())) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"), cancellable = true)
    private void chowkingdom$hideYuushyaPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo callback) {
        if (packet != null && packet.unsignedContent() != null && YuushyaStartupChatMixin.chowkingdom$containsYuushyaWarning(packet.unsignedContent().getString())) {
            callback.cancel();
        }
    }
}
