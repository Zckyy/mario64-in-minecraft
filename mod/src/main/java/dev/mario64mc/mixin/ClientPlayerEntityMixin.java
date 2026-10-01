package dev.mario64mc.mixin;

import dev.mario64mc.MarioController;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Report "on ground" to the server while Mario: SM64 handles falls, so vanilla fall damage and the fly-kick stay off. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void mario64$grounded(CallbackInfo ci) {
        if (MarioController.INSTANCE.active) {
            ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
            self.setOnGround(true);
            self.fallDistance = 0;
        }
    }
}
