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
        int w = 150, h = 20, gap = 24, top = height / 6 + 12;
        int lx = width / 2 - w - 5, rx = width / 2 + 5;

        // left column: gamepad
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.gamepadEnabled)
                .build(lx, top, w, h, Text.translatable("mario64.settings.gamepad"), (b, v) -> c.gamepadEnabled = v));
        addDrawableChild(new SliderWidget(lx, top + gap, w, h, Text.empty(), c.deadzone / 0.5) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.translatable("mario64.settings.deadzone", Math.round(value * 50) + "%"));
            }
            @Override protected void applyValue() { c.deadzone = value * 0.5; }
        });
        addDrawableChild(new SliderWidget(lx, top + gap * 2, w, h, Text.empty(), (c.cameraSpeed - 0.1) / 2.9) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.translatable("mario64.settings.camera", String.format("%.1fx", 0.1 + value * 2.9)));
            }
            @Override protected void applyValue() { c.cameraSpeed = 0.1 + value * 2.9; }
        });
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.invertCameraY)
                .build(lx, top + gap * 3, w, h, Text.translatable("mario64.settings.invert"), (b, v) -> c.invertCameraY = v));

        // right column: music
        addDrawableChild(CyclingButtonWidget.<String>builder(v -> Text.translatable("mario64.settings.music." + v))
                .values("mario", "always", "off").initially(c.music)
                .build(rx, top, w, h, Text.translatable("mario64.settings.music"), (b, v) -> c.music = v));
        java.util.List<Integer> tracks = new java.util.ArrayList<>();
        for (int t : MarioMusic.PICKER) tracks.add(t);
        addDrawableChild(CyclingButtonWidget.<Integer>builder(v -> Text.literal(v < 0 ? "Auto" : MarioMusic.name(v)))
                .values(tracks).initially(tracks.contains(c.track) ? c.track : -1)
                .tooltip(v -> net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(MarioMusic.name(v))))
                .build(rx, top + gap, w, h, Text.translatable("mario64.settings.track"), (b, v) -> c.track = v));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 - 100, top + gap * 6, 200, h).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int w = 150, top = height / 6 + 12;
        int lx = width / 2 - w - 5, rx = width / 2 + 5;
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 30, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mario64.settings.gamepad.header"), lx + w / 2, top - 13, 0xFFD21F);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mario64.settings.music.header"), rx + w / 2, top - 13, 0xFFD21F);
        String pad = MarioGamepad.connectedName();
        Text status = pad == null ? Text.translatable("mario64.settings.none") : Text.translatable("mario64.settings.detected", pad);
        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, top + 24 * 4 + 6, pad == null ? 0xFF8080 : 0x80FF80);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mario64.settings.layout"), width / 2, top + 24 * 4 + 20, 0xAAAAAA);
        int now = MarioMusic.playing();
        if (now >= 0) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("♪ " + MarioMusic.name(now)), rx + w / 2, top + 24 * 2 + 6, 0xFFFFFF);
    }

    @Override
    public void close() {
        MarioConfig.get().save();
        if (client != null) client.setScreen(parent);
    }
}
