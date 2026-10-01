package dev.mario64mc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class Mario64Client implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MarioControls.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            MarioControls.tick(mc);
            MarioGamepad.menuButtons(mc);
            MarioMusic.tick(mc);
            MarioController.INSTANCE.clientTick(mc);
            AutoTest.clientTick(mc);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
            if (MarioController.INSTANCE.active) MarioController.INSTANCE.deactivate();
        }));
        WorldRenderEvents.AFTER_ENTITIES.register(MarioRenderer::render);
        // "Mario 64 Gamepad..." button in Options > Controls
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof net.minecraft.client.gui.screen.option.ControlsOptionsScreen) {
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(
                        net.minecraft.client.gui.widget.ButtonWidget.builder(net.minecraft.text.Text.translatable("mario64.settings.open"),
                                b -> mc.setScreen(new MarioSettingsScreen(screen))).dimensions(w - 158, 6, 150, 20).build());
            }
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> { renderPowerMeter(ctx); MarioControls.renderCard(ctx); });
    }

    /** SM64-style power meter: 8 wedges, coloured like the original (blue, green, yellow, red). */
    private static void renderPowerMeter(DrawContext ctx) {
        MarioController m = MarioController.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!m.active || mc.options.hudHidden) return;
        int wedges = Math.max(0, Math.min(8, m.health >> 8));
        int cx = ctx.getScaledWindowWidth() / 2, top = 8;
        int colour = wedges >= 7 ? 0xFF2F6BFF : wedges >= 5 ? 0xFF2FD04A : wedges >= 3 ? 0xFFFFD21F : 0xFFFF3030;
        ctx.fill(cx - 46, top - 3, cx + 46, top + 21, 0x90000000);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, "POWER", cx, top, 0xFFFFFFFF);
        for (int i = 0; i < 8; i++) {
            int x = cx - 40 + i * 10;
            ctx.fill(x, top + 11, x + 8, top + 18, i < wedges ? colour : 0xFF404040);
        }
    }
}
