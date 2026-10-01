package dev.mario64mc.mixin;

import dev.mario64mc.MarioController;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Mario is active, SM64 physics owns the local player's position: skip vanilla movement. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void mario64$skipTravel(Vec3d input, CallbackInfo ci) {
        if (MarioController.INSTANCE.active && (Object) this instanceof ClientPlayerEntity) ci.cancel();
    }
}
