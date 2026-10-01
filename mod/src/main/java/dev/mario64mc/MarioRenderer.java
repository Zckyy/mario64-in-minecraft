package dev.mario64mc;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import dev.mario64mc.sm64.LibSM64;

/**
 * Draws libsm64's per-frame Mario mesh. SM64 shades Mario as mix(vertexColor, texture, texture.a), so we draw it twice:
 * once flat-coloured, then the ROM texture atlas alpha-blended on top with identical geometry (decals: eyes, cap, buttons).
 */
public final class MarioRenderer {
    static final Identifier ATLAS = Identifier.of("mario64", "atlas");
    static final Identifier WHITE = Identifier.of("mario64", "white");

    private MarioRenderer() {}

    static void uploadTexture(byte[] rgba) {
        NativeImage img = new NativeImage(LibSM64.TEXTURE_WIDTH, LibSM64.TEXTURE_HEIGHT, true);
        for (int y = 0; y < LibSM64.TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < LibSM64.TEXTURE_WIDTH; x++) {
                int i = (y * LibSM64.TEXTURE_WIDTH + x) * 4;
                img.setColorArgb(x, y, (rgba[i + 3] & 255) << 24 | (rgba[i] & 255) << 16 | (rgba[i + 1] & 255) << 8 | (rgba[i + 2] & 255));
            }
        }
        NativeImage white = new NativeImage(16, 16, true);
        white.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
        var tm = MinecraftClient.getInstance().getTextureManager();
        tm.registerTexture(ATLAS, new NativeImageBackedTexture(img));
        tm.registerTexture(WHITE, new NativeImageBackedTexture(white));
    }

    static void render(WorldRenderContext ctx) {
        MarioController m = MarioController.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!m.active || m.curTris == 0 || mc.world == null || ctx.matrixStack() == null) return;

        MatrixStack ms = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(m.ox - cam.x, m.oy - cam.y, m.oz - cam.z);
        ms.scale(1f / WorldCollision.SCALE, 1f / WorldCollision.SCALE, 1f / WorldCollision.SCALE);
        MatrixStack.Entry e = ms.peek();
        int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(m.renderX, m.renderY + 0.8, m.renderZ));

        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        RenderLayer base = RenderLayer.getEntityCutoutNoCull(WHITE);
        emit(imm.getBuffer(base), e, light, m, true);
        imm.draw(base);
        RenderLayer decal = RenderLayer.getEntityTranslucent(ATLAS);
        emit(imm.getBuffer(decal), e, light, m, false);
        imm.draw(decal);
        ms.pop();
    }

    private static void emit(VertexConsumer vc, MatrixStack.Entry e, int light, MarioController m, boolean colourPass) {
        boolean lerp = m.prevTris == m.curTris;
        float a = m.alpha;
        float[] cur = m.curPos, prev = m.prevPos, n = m.curNorm, c = m.curCol, uv = m.curUv;
        for (int t = 0; t < m.curTris; t++) {
            // libsm64 marks untextured triangles with uv (1,1); with GL_REPEAT that samples texel (0,0), so skip them
            if (!colourPass && uv[t * 6] == 1f && uv[t * 6 + 1] == 1f && uv[t * 6 + 2] == 1f && uv[t * 6 + 3] == 1f) continue;
            float push = colourPass ? 0f : 0.35f; // lift decals off the base surface (SM64 units) to avoid z-fighting
            for (int k = 0; k < 4; k++) {
                int v = t * 3 + Math.min(k, 2); // quads mode: repeat the 3rd vertex
                int i = v * 3;
                float x = cur[i], y = cur[i + 1], z = cur[i + 2];
                if (lerp) {
                    x = prev[i] + (x - prev[i]) * a;
                    y = prev[i + 1] + (y - prev[i + 1]) * a;
                    z = prev[i + 2] + (z - prev[i + 2]) * a;
                }
                vc.vertex(e, x + n[i] * push, y + n[i + 1] * push, z + n[i + 2] * push);
                if (colourPass) vc.color(c[i], c[i + 1], c[i + 2], 1f).texture(0.5f, 0.5f);
                else vc.color(1f, 1f, 1f, 1f).texture(uv[v * 2], uv[v * 2 + 1]);
                vc.overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, n[i], n[i + 1], n[i + 2]);
            }
        }
    }
}
