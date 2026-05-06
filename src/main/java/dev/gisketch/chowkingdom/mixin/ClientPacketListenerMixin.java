package dev.gisketch.chowkingdom.mixin;

import dev.gisketch.chowkingdom.client.ChowDeathScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(
        method = "handlePlayerCombatKill",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"),
        cancellable = true
    )
    private void chowkingdom$openCombatKillScreenDirectly(ClientboundPlayerCombatKillPacket packet, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity killed = minecraft.level == null ? null : minecraft.level.getEntity(packet.playerId());
        if (killed != minecraft.player || minecraft.player == null || !minecraft.player.shouldShowDeathScreen()) return;
        boolean hardcore = minecraft.level != null && minecraft.level.getLevelData().isHardcore();
        chowkingdom$installScreenDirectly(minecraft, new ChowDeathScreen(packet.message(), hardcore));
        callback.cancel();
    }

    private static void chowkingdom$installScreenDirectly(Minecraft minecraft, Screen screen) {
        minecraft.screen = screen;
        screen.added();
        screen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        minecraft.mouseHandler.releaseMouse();
        KeyMapping.releaseAll();
        minecraft.noRender = false;
    }
}
