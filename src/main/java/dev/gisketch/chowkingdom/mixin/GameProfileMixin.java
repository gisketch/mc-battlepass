package dev.gisketch.chowkingdom.mixin;

import com.mojang.authlib.GameProfile;
import dev.gisketch.chowkingdom.profiles.NicknameStore;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameProfile.class, remap = false)
public abstract class GameProfileMixin {
    @Shadow
    @Final
    private UUID id;

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true, remap = false)
    private void chowkingdom$nickname(CallbackInfoReturnable<String> callback) {
        String nickname = NicknameStore.nicknameFor(this.id);
        if (nickname != null) {
            callback.setReturnValue(nickname);
        }
    }
}
