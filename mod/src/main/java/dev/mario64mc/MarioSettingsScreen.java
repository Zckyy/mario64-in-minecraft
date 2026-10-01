package dev.mario64mc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Options > Controls > "Mario 64 Gamepad...": gamepad on/off, deadzone, camera speed, invert Y. */
public final class MarioSettingsScreen extends Screen {
    private final Screen parent;

    public MarioSettingsScreen(Screen parent) {
        super(Text.translatable("mario64.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MarioConfig c = MarioConfig.get();
        int x = width / 2 - 100, y = height / 4 + 8, w = 200, h = 20, gap = 24;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.gamepadEnabled)
                .build(x, y, w, h, Text.translatable("mario64.settings.gamepad"), (b, v) -> c.gamepadEnabled = v));
        addDrawableChild(new SliderWidget(x, y + gap, w, h, Text.empty(), c.deadzone / 0.5) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.translatable("mario64.settings.deadzone", Math.round(value * 50) + "%"));
            }
            @Override protected void applyValue() { c.deadzone = value * 0.5; }
        });
        addDrawableChild(new SliderWidget(x, y + gap * 2, w, h, Text.empty(), (c.cameraSpeed - 0.1) / 2.9) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.translatable("mario64.settings.camera", String.format("%.1fx", 0.1 + value * 2.9)));
            }
            @Override protected void applyValue() { c.cameraSpeed = 0.1 + value * 2.9; }
        });
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.invertCameraY)
                .build(x, y + gap * 3, w, h, Text.translatable("mario64.settings.invert"), (b, v) -> c.invertCameraY = v));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(x, y + gap * 6, w, h).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 4 - 24, 0xFFFFFF);
        String pad = MarioGamepad.connectedName();
        Text status = pad == null ? Text.translatable("mario64.settings.none") : Text.translatable("mario64.settings.detected", pad);
        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, height / 4 + 8 + 24 * 4 + 8, pad == null ? 0xFF8080 : 0x80FF80);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mario64.settings.layout"), width / 2, height / 4 + 8 + 24 * 5 + 2, 0xAAAAAA);
    }

    @Override
    public void close() {
        MarioConfig.get().save();
        if (client != null) client.setScreen(parent);
    }
}
