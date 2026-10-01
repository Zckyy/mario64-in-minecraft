package dev.mario64mc.mixin;

import dev.mario64mc.MarioController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Step SM64 at 30 Hz from the frame loop and move the player before the camera is placed. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void mario64$frame(RenderTickCounter counter, CallbackInfo ci) {
        MarioController.INSTANCE.frame(MinecraftClient.getInstance());
    }
}
