package dev.mario64mc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Dev-only oracle (-Dmario64.autotest=<scenario>): opens a lab world, becomes Mario, plays a scripted input
 * timeline, logs SM64 state lines prefixed "MARIO64TEST" and saves screenshots, then quits.
 */
public final class AutoTest {
    static final String SCENARIO = System.getProperty("mario64.autotest");
    static final String WORLD = System.getProperty("mario64.world", "MarioLab");
    private static boolean worldRequested;
    private static int inWorldTicks = -1;
    private static int t = -1;

    public static boolean enabled() { return SCENARIO != null; }

    static void clientTick(MinecraftClient mc) {
        if (!enabled()) return;
        if (!worldRequested && mc.currentScreen instanceof TitleScreen) {
            worldRequested = true;
            if (mc.getLevelStorage().levelExists(WORLD)) {
                mc.createIntegratedServerLoader().start(WORLD, () -> {});
            } else {
                GameRules rules = new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES);
                rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
                rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
                rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
                LevelInfo info = new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, rules, DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart(WORLD, info, new GeneratorOptions(64646464L, false, false),
                        lookup -> lookup.getOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT).value().createDimensionsRegistryHolder(),
                        new TitleScreen());
            }
        }
        if (mc.player == null || mc.world == null) return;
        inWorldTicks++;
        if (inWorldTicks == 60) {
            mc.player.networkHandler.sendChatCommand("time set noon");
            String setup = System.getProperty("mario64.setup");
            if (setup != null) for (String cmd : setup.split(";")) mc.player.networkHandler.sendChatCommand(cmd.trim());
        }
        if (inWorldTicks == 120) {
            mc.player.setPitch(22);
            MarioController.INSTANCE.activate();
            t = 0;
            log(mc, "activated active=" + MarioController.INSTANCE.active);
        }
    }

    /** Called once per SM64 tick (30 Hz) after Mario was ticked. */
    static void marioTick(MinecraftClient mc) {
        if (t < 0) return;
        t++;
        MarioController m = MarioController.INSTANCE;
        if (t % 10 == 0) log(mc, String.format("t=%d action=0x%08X flags=0x%08X pos=(%.2f,%.2f,%.2f) health=0x%X tris=%d face=%.2f",
                t, m.action, m.flags, m.renderXNow(), m.renderYNow(), m.renderZNow(), m.health, m.curTris, m.faceAngle));
        int[] shots = {40, 100, 132, 175, 228, 262, 290, 352, 362, 420};
        for (int s : shots) if (t == s) shot(mc, "mario_" + SCENARIO + "_t" + t);
        if (t == 470) {
            log(mc, "DONE");
            mc.scheduleStop();
        }
    }

    /** Scripted controller: {stickX, stickY, A, B, Z}. */
    static void fillInputs(float[] o) {
        java.util.Arrays.fill(o, 0);
        if (t < 0) return;
        if (t >= 45 && t < 190) o[1] = -1;          // run forward
        if (in(120, 124) || in(137, 141) || in(154, 158)) o[2] = 1; // single, double, triple jump
        if (in(220, 223)) o[3] = 1;                 // punch
        if (in(240, 244)) o[2] = 1;                 // jump
        if (in(256, 262)) o[4] = 1;                 // ground pound
        if (t >= 300 && t < 360) o[1] = -1;         // run again
        if (in(335, 345)) o[4] = 1;                 // crouch while running...
        if (in(338, 342)) o[2] = 1;                 // ...then A: long jump
        if (t >= 380 && t < 420) o[0] = 1;          // turn right and run
        if (in(400, 403)) o[3] = 1;                 // dive / kick
    }

    private static boolean in(int a, int b) { return t >= a && t < b; }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(), msg -> log(mc, "screenshot " + name));
    }

    static void log(MinecraftClient mc, String msg) { MarioController.LOG.info("MARIO64TEST {}", msg); }
}
