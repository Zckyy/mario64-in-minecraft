package dev.mario64mc.mixin;

import dev.mario64mc.MarioController;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Integrated server only: Mario's body (radius 50, height ~160 units) is not Steve's 0.6 x 1.8 box, so on SM64 ramps
 * the server would call his moves "moved wrongly" and snap him back. Treat the Mario player as noClip while the move
 * packet is checked; SM64 already did the collision.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    private void mario64$trustMario(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (MarioController.isMarioPlayer(player.getUuid())) player.noClip = true;
    }
}
