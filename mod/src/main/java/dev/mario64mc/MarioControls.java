package dev.mario64mc;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The "Mario 64" section of Options > Controls > Key Binds, plus the on-screen controls card.
 *
 * Minecraft maps each physical key to a single KeyBinding (the last one registered wins), so defaulting Mario's A/B/Z to
 * Space / left click / Shift would steal vanilla jump, attack and sneak even with Mario off. Instead they start unbound,
 * which means "use Minecraft's Jump / Attack / Sneak key". Once you bind one, it is read straight from GLFW.
 */
public final class MarioControls {
    public static final String CATEGORY = "category.mario64";
    public static KeyBinding toggle, buttonA, buttonB, buttonZ, showControls;
    private static boolean cardPinned;
    private static long cardUntil;

    private MarioControls() {}

    static void register() {
        toggle = reg("key.mario64.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M);
        buttonA = reg("key.mario64.a", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode());
        buttonB = reg("key.mario64.b", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode());
        buttonZ = reg("key.mario64.z", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode());
        showControls = reg("key.mario64.controls", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H);
    }

    private static KeyBinding reg(String id, InputUtil.Type type, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, type, code, CATEGORY));
    }

    public static void flashCard() { cardUntil = System.currentTimeMillis() + 8000; }

    /** Client tick: key presses that toggle things. */
    static void tick(MinecraftClient mc) {
        while (toggle.wasPressed()) {
            MarioController.INSTANCE.toggle();
            if (MarioController.INSTANCE.active) flashCard();
        }
        while (showControls.wasPressed()) cardPinned = !cardPinned;
    }

    public static boolean a(MinecraftClient mc) { return held(mc, buttonA, mc.options.jumpKey); }
    public static boolean b(MinecraftClient mc) { return held(mc, buttonB, mc.options.attackKey); }
    public static boolean z(MinecraftClient mc) { return held(mc, buttonZ, mc.options.sneakKey); }

    private static boolean held(MinecraftClient mc, KeyBinding mario, KeyBinding vanilla) {
        if (mario.isUnbound()) return vanilla.isPressed();
        if (mc.currentScreen != null) return false;
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(mario);
        long window = mc.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(window, key.getCode());
    }

    /** Display name of the key that currently drives a Mario button. */
    public static String name(KeyBinding mario, KeyBinding vanilla) {
        return (mario.isUnbound() ? vanilla : mario).getBoundKeyLocalizedText().getString();
    }

    public static String aName() { var o = MinecraftClient.getInstance().options; return name(buttonA, o.jumpKey); }
    public static String bName() { var o = MinecraftClient.getInstance().options; return name(buttonB, o.attackKey); }
    public static String zName() { var o = MinecraftClient.getInstance().options; return name(buttonZ, o.sneakKey); }

    public static String moveNames() {
        var o = MinecraftClient.getInstance().options;
        return o.forwardKey.getBoundKeyLocalizedText().getString() + o.leftKey.getBoundKeyLocalizedText().getString()
                + o.backKey.getBoundKeyLocalizedText().getString() + o.rightKey.getBoundKeyLocalizedText().getString();
    }

    /** Controls card: shown for 8 s after becoming Mario, or pinned with the "Show Mario controls" key. */
    static void renderCard(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!MarioController.INSTANCE.active || mc.options.hudHidden || mc.currentScreen != null) return;
        if (!cardPinned && System.currentTimeMillis() > cardUntil) return;
        String a = aName(), b = bName(), z = zName();
        String[][] rows = {
                {"Move", moveNames()},
                {"A  jump / swim", a},
                {"B  punch / kick / dive", b},
                {"Z  crouch / ground pound", z},
                {"Camera", "Mouse"},
                {"", ""},
                {"Triple jump", a + " x3 on landing"},
                {"Long jump", "run, " + z + " + " + a},
                {"Backflip", "hold " + z + ", " + a},
                {"Side flip", "reverse while running, " + a},
                {"Wall kick", a + " as you hit a wall"},
                {"Ground pound", z + " in the air"},
                {"Slide kick", "run, " + z + " + " + b},
                {"Dive", "run, " + b},
                {"", ""},
                {"Exit Mario", toggle.getBoundKeyLocalizedText().getString()},
                {cardPinned ? "Hide this" : "Pin this", showControls.getBoundKeyLocalizedText().getString()},
        };
        var tr = mc.textRenderer;
        int lineH = 10, labelW = 0, valueW = 0;
        for (String[] r : rows) { labelW = Math.max(labelW, tr.getWidth(r[0])); valueW = Math.max(valueW, tr.getWidth(r[1])); }
        int w = labelW + 12 + valueW + 8, h = rows.length * lineH + 22;
        // right side, under the power meter's row, shrunk to fit the window (chat and hotbar live bottom-left/centre)
        float scale = Math.min(1f, Math.min((ctx.getScaledWindowHeight() - 70f) / h, (ctx.getScaledWindowWidth() * 0.45f) / w));
        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(ctx.getScaledWindowWidth() - w * scale - 6, 36, 0); // below the power meter
        ms.scale(scale, scale, 1);
        ctx.fill(0, 0, w, h, 0xA0000000);
        ctx.drawTextWithShadow(tr, Text.translatable(CATEGORY).getString() + " controls", 4, 4, 0xFFFFD21F);
        for (int i = 0; i < rows.length; i++) {
            int ry = 18 + i * lineH;
            ctx.drawTextWithShadow(tr, rows[i][0], 4, ry, 0xFFDDDDDD);
            ctx.drawTextWithShadow(tr, rows[i][1], 4 + labelW + 12, ry, 0xFFFFFFFF);
        }
        ms.pop();
    }
}
