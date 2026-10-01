package dev.mario64mc;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Gamepad input through GLFW's standard gamepad mapping. XInput pads such as the GameSir G7 SE show up as an Xbox
 * controller here. N64 -> Xbox layout, as in the PC ports:
 *   left stick = analog stick (true analog: half tilt walks, full tilt runs)
 *   right stick = camera,  A = A,  X or B = B,  LT / RT / LB = Z,
 *   Menu = pause,  View = pin the controls card.
 */
public final class MarioGamepad {
    /** A snapshot of one controller, already deadzoned. Tests can inject one with {@link #inject}. */
    public static final class State {
        public float stickX, stickY, camX, camY;
        public boolean a, b, z, start, back;
        public String name = "";
    }

    private static final GLFWGamepadState RAW = GLFWGamepadState.create();
    private static State injected;
    private static State current;
    private static int jid = -1;
    private static boolean prevStart, prevBack;

    private MarioGamepad() {}

    /** Test hook: pretend this is the connected gamepad (null = back to real hardware). */
    public static void inject(State s) { injected = s; }

    /** The active gamepad this frame, or null if disabled / none connected. */
    public static State current() { return current; }

    /** Name of the first connected gamepad, or null. */
    public static String connectedName() {
        if (injected != null) return injected.name;
        int j = findGamepad();
        return j < 0 ? null : GLFW.glfwGetGamepadName(j);
    }

    /** Poll once per frame (render thread). */
    static void poll() {
        current = null;
        if (!MarioConfig.get().gamepadEnabled) return;
        if (injected != null) { current = injected; keepAwake(injected); return; }
        if (jid < 0 || !GLFW.glfwJoystickIsGamepad(jid)) jid = findGamepad();
        if (jid < 0 || !GLFW.glfwGetGamepadState(jid, RAW)) return;

        State s = new State();
        double dz = MarioConfig.get().deadzone;
        float[] l = radial(RAW.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), RAW.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y), dz);
        float[] r = radial(RAW.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), RAW.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y), dz);
        s.stickX = l[0]; s.stickY = l[1]; // GLFW: up = -1, which is also SM64's "forward"
        s.camX = r[0]; s.camY = r[1];
        s.a = btn(GLFW.GLFW_GAMEPAD_BUTTON_A);
        s.b = btn(GLFW.GLFW_GAMEPAD_BUTTON_X) || btn(GLFW.GLFW_GAMEPAD_BUTTON_B);
        // triggers rest at -1 and go to +1 when fully pressed
        s.z = RAW.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.3f || RAW.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.3f
                || btn(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        s.start = btn(GLFW.GLFW_GAMEPAD_BUTTON_START);
        s.back = btn(GLFW.GLFW_GAMEPAD_BUTTON_BACK);
        s.name = GLFW.glfwGetGamepadName(jid);
        current = s;
        keepAwake(s);
    }

    /**
     * Minecraft 1.21.2+ caps the game at 30 FPS after 60 s without keyboard/mouse input (10 FPS after 10 min). Gamepad
     * input is invisible to it, so report real pad activity as input, the same way a key press does.
     */
    private static void keepAwake(State s) {
        boolean used = s.stickX != 0 || s.stickY != 0 || s.camX != 0 || s.camY != 0 || s.a || s.b || s.z || s.start || s.back;
        if (used) MinecraftClient.getInstance().getInactivityFpsLimiter().onInput();
    }

    /** Edge-triggered menu buttons, handled on the client tick. */
    static void menuButtons(MinecraftClient mc) {
        State s = current;
        boolean start = s != null && s.start, back = s != null && s.back;
        if (MarioController.INSTANCE.active && mc.currentScreen == null) {
            if (start && !prevStart) mc.openGameMenu(false);
            if (back && !prevBack) MarioControls.togglePinnedCard();
        }
        prevStart = start;
        prevBack = back;
    }

    /** Radial deadzone with rescale, so the usable range still reaches 1.0. */
    static float[] radial(float x, float y, double dz) {
        double mag = Math.sqrt(x * x + y * y);
        if (mag <= dz) return new float[]{0, 0};
        double scaled = Math.min(1, (mag - dz) / (1 - dz));
        return new float[]{(float) (x / mag * scaled), (float) (y / mag * scaled)};
    }

    private static boolean btn(int b) { return RAW.buttons(b) == GLFW.GLFW_PRESS; }

    private static int findGamepad() {
        for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++)
            if (GLFW.glfwJoystickPresent(j) && GLFW.glfwJoystickIsGamepad(j)) return j;
        return -1;
    }
}
