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
                        lookup -> lookup.getOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow("flat".equals(System.getProperty("mario64.preset")) ? WorldPresets.FLAT : WorldPresets.DEFAULT).value().createDimensionsRegistryHolder(),
                        new TitleScreen());
            }
        }
        if (mc.player == null || mc.world == null) return;
        inWorldTicks++;
        if (inWorldTicks == 60) {
            mc.player.networkHandler.sendChatCommand("time set noon");
            String setup = System.getProperty("mario64.setup");
            var srv = mc.getServer();
            java.util.UUID id = mc.player.getUuid();
            if (setup != null && srv != null) srv.execute(() -> {
                var sp = srv.getPlayerManager().getPlayer(id);
                for (String cmd : setup.split(";"))
                    srv.getCommandManager().executeWithPrefix(sp.getCommandSource().withSilent().withMaxLevel(4), cmd.trim());
            });
        }
        if (inWorldTicks == 120) {
            mc.player.setPitch(22);
            mc.player.setYaw(0);
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
        if (t % 10 == 0 && mc.player != null) {
            var mobs = mc.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, mc.player.getBoundingBox().expand(12), e -> e != mc.player);
            for (var e : mobs) log(mc, String.format("  mob %s hp=%.1f at (%.1f,%.1f,%.1f)", e.getType().getUntranslatedName(), e.getHealth(), e.getX(), e.getY(), e.getZ()));
        }
        int[] shots = "show".equals(SCENARIO) ? new int[]{90, 130, 170, 230, 280, 330, 400} : "course".equals(SCENARIO) ? new int[]{40, 70, 95, 120, 150, 180, 210, 250, 290, 330} : new int[]{40, 100, 132, 175, 228, 262, 290, 352, 362, 420};
        for (int s : shots) if (t == s) shot(mc, "mario_" + SCENARIO + "_t" + t);
        if (t == 80) {
            log(mc, "origin " + m.ox + "," + m.oy + "," + m.oz);
            for (String line : m.dumpNear(150)) log(mc, "  surf " + line);
        }
        if (t == ("show".equals(SCENARIO) ? 520 : 470)) {
            log(mc, "DONE");
            mc.scheduleStop();
        }
    }

    /** Scripted controller: {stickX, stickY, A, B, Z}. */
    static void fillInputs(float[] o) {
        java.util.Arrays.fill(o, 0);
        if (t < 0) return;
        if ("show".equals(SCENARIO)) {
            MarioController m = MarioController.INSTANCE;
            boolean landing = m.action == 0x04000470 || m.action == 0x04000472; // ACT_JUMP_LAND, ACT_DOUBLE_JUMP_LAND
            if (t >= 60 && t < 470) o[1] = -1;
            if (in(75, 78) || (landing && t < 220)) o[2] = 1;             // single -> double -> triple jump chain
            boolean air = (m.action & MarioController.ACT_FLAG_AIR) != 0;
            if (air && m.renderZNow() > 31 && m.renderZNow() < 36 && t % 6 < 3) o[3] = 1; // dive off the platform at the pig
            if (m.renderYNow() < -60.5 && t % 14 < 4) o[2] = 1;          // swim strokes once in the pool
            return;
        }
        if ("course".equals(SCENARIO)) {
            if (t >= 45 && t < 330) o[1] = -1;                    // keep running/swimming forward
            if (t >= 95 && t < 200 && t % 20 < 3) o[3] = 1;       // punch whatever is in front
            if (t >= 230 && t % 16 < 4) o[2] = 1;                 // swim strokes / jump out
            return;
        }
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
