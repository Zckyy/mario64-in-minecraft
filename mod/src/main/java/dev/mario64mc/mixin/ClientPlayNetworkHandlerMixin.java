package dev.mario64mc.mixin;

import dev.mario64mc.MarioController;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** /tp, respawn and server corrections move Mario too. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
    private void mario64$teleported(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        MarioController.INSTANCE.onServerTeleport();
    }
}
