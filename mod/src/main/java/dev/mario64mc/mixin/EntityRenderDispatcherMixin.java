package dev.mario64mc.mixin;

import dev.mario64mc.MarioController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Steve: the libsm64 mesh is drawn instead. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mario64$hidePlayer(Entity entity, double x, double y, double z, float tickDelta, MatrixStack matrices,
                                    VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (MarioController.INSTANCE.active && entity == MinecraftClient.getInstance().player) ci.cancel();
    }
}
