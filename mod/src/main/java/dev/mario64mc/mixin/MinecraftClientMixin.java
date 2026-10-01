package dev.mario64mc.mixin;

import dev.mario64mc.MarioController;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Left click is Mario's B button (punch / kick / dive), not block breaking. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void mario64$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MarioController.INSTANCE.active) cir.setReturnValue(false);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void mario64$noBreaking(boolean breaking, CallbackInfo ci) {
        if (MarioController.INSTANCE.active) ci.cancel();
    }
}
