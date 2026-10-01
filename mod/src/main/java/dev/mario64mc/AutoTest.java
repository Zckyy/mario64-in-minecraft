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
        if ("tech".equals(SCENARIO)) techCheck(mc, m);
        if ("flips".equals(SCENARIO)) flipsCheck(mc, m);
        if (t == ("show".equals(SCENARIO) ? 520 : "tech".equals(SCENARIO) ? 360 : "flips".equals(SCENARIO) ? 420 : 470)) {
            if ("flips".equals(SCENARIO)) log(mc, String.format("RESULT backflip=%s height=%.2f side_flip=%s height=%.2f slide_kick=%s",
                    flipSeen[0] ? "PASS" : "FAIL", flipPeak[0] - flipBase[0], flipSeen[1] ? "PASS" : "FAIL", flipPeak[1] - flipBase[1],
                    flipSeen[2] ? "PASS" : "FAIL"));
            if ("tech".equals(SCENARIO)) log(mc, String.format("RESULT long_jump=%s distance=%.2f blocks wall_kick=%s",
                    longJumpLanded ? "PASS" : "FAIL", longJumpEndZ - longJumpStartZ, wallKicked ? "PASS" : "FAIL"));
            log(mc, "DONE");
            mc.scheduleStop();
        }
    }

    /** Scripted controller: {stickX, stickY, A, B, Z}. */
    static void fillInputs(float[] o) {
        java.util.Arrays.fill(o, 0);
        if (t < 0) return;
        if ("flips".equals(SCENARIO)) { flipsInputs(o); return; }
        if ("tech".equals(SCENARIO)) {
            MarioController m = MarioController.INSTANCE;
            double z = m.renderZNow();
            if (t >= 40 && !wallKicked) o[1] = -1;                          // run toward +z (the wall)
            if (!longJumped && t >= 70 && t < 82) o[4] = 1;                 // Z while running -> crouch slide
            if (m.action == ACT_CROUCH_SLIDE && !longJumped) o[2] = 1;      // fresh A during crouch slide -> long jump
            if (longJumpLanded && !wallKicked && z > 25.5 && (m.action & MarioController.ACT_FLAG_AIR) == 0
                    && jumpPressTicks < 3) { o[2] = 1; jumpPressTicks++; }   // running jump at the wall
            if (m.action == ACT_AIR_HIT_WALL) o[2] = 1;                     // fresh A in the 2-frame window -> wall kick
            return;
        }
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

    // decomp/include/sm64.h
    static final int ACT_CROUCH_SLIDE = 0x04808459, ACT_LONG_JUMP = 0x03000888, ACT_LONG_JUMP_LAND = 0x00000479,
            ACT_LONG_JUMP_LAND_STOP = 0x0800023B, ACT_AIR_HIT_WALL = 0x000008A7, ACT_WALL_KICK_AIR = 0x03000886;
    private static boolean longJumped, longJumpLanded, wallKicked;
    private static int jumpPressTicks, lastAction, shotAt = -1;
    private static double longJumpStartZ, longJumpEndZ;

    private static void techCheck(MinecraftClient mc, MarioController m) {
        if (m.action != lastAction) {
            log(mc, String.format("action 0x%08X -> 0x%08X at t=%d z=%.2f y=%.2f", lastAction, m.action, t, m.renderZNow(), m.renderYNow()));
            if (m.action == ACT_LONG_JUMP && !longJumped) { longJumped = true; longJumpStartZ = m.renderZNow(); shotAt = t + 8; }
            if (longJumped && !longJumpLanded && (m.action == ACT_LONG_JUMP_LAND || m.action == ACT_LONG_JUMP_LAND_STOP
                    || (lastAction == ACT_LONG_JUMP && (m.action & MarioController.ACT_FLAG_AIR) == 0))) {
                longJumpLanded = true; longJumpEndZ = m.renderZNow();
            }
            if (m.action == ACT_WALL_KICK_AIR && !wallKicked) { wallKicked = true; shotAt = t + 5; }
            lastAction = m.action;
        }
        if (t == shotAt) shot(mc, "mario_tech_" + (wallKicked ? "wallkick" : "longjump"));
    }

    // --- "flips": backflip, side flip, slide kick. A tiny state machine driven by Mario's real action state. ---
    static final int ACT_CROUCHING = 0x0C008220, ACT_START_CROUCHING = 0x0C008221, ACT_BACKFLIP = 0x01000883,
            ACT_TURNING_AROUND = 0x00000443, ACT_FINISH_TURNING_AROUND = 0x00000444, ACT_SIDE_FLIP = 0x01000887,
            ACT_SLIDE_KICK = 0x018008AA, ACT_SLIDE_KICK_SLIDE = 0x0080045A;
    static final int[] FLIP_ACTIONS = {ACT_BACKFLIP, ACT_SIDE_FLIP, ACT_SLIDE_KICK};
    static final String[] FLIP_NAMES = {"backflip", "sideflip", "slidekick"};
    private static int phase, phaseStart = 40, landedAt = -1;
    private static final boolean[] flipSeen = new boolean[3];
    private static final double[] flipBase = new double[3], flipPeak = new double[3];

    private static void flipsInputs(float[] o) {
        MarioController m = MarioController.INSTANCE;
        if (t < 40 || phase > 2) return;
        int pt = t - phaseStart;
        boolean seen = flipSeen[phase];
        if (phase == 0) {                                   // backflip: hold Z standing still, fresh A once crouched
            if (!seen) {
                o[4] = 1;
                if (m.action == ACT_CROUCHING || m.action == ACT_START_CROUCHING) o[2] = pt % 4 == 3 ? 1 : 0;
            }
        } else if (phase == 1) {                            // side flip: run, yank the stick back, A while skidding
            if (!seen) {
                o[1] = pt < 30 ? -1 : 1;
                if (m.action == ACT_TURNING_AROUND || m.action == ACT_FINISH_TURNING_AROUND) o[2] = 1;
            }
        } else {                                            // slide kick: run, Z into a crouch slide, fresh B
            if (!seen) {
                o[1] = -1;
                if (pt >= 30) o[4] = 1;
                if (m.action == 0x04808459) o[3] = 1;       // ACT_CROUCH_SLIDE
            }
        }
    }

    private static void flipsCheck(MinecraftClient mc, MarioController m) {
        if (m.action != lastAction) {
            log(mc, String.format("action 0x%08X -> 0x%08X at t=%d pos=(%.2f,%.2f,%.2f)", lastAction, m.action, t,
                    m.renderXNow(), m.renderYNow(), m.renderZNow()));
            lastAction = m.action;
        }
        if (phase > 2) return;
        if (!flipSeen[phase] && m.action == FLIP_ACTIONS[phase]) {
            flipSeen[phase] = true;
            flipBase[phase] = flipPeak[phase] = m.renderYNow();
            shotAt = t + 6;
            log(mc, FLIP_NAMES[phase] + " started at t=" + t);
        }
        if (flipSeen[phase]) {
            flipPeak[phase] = Math.max(flipPeak[phase], m.renderYNow());
            boolean grounded = (m.action & MarioController.ACT_FLAG_AIR) == 0 && m.action != ACT_SLIDE_KICK_SLIDE;
            if (grounded && landedAt < 0) landedAt = t;
            if (landedAt >= 0 && t - landedAt > 25) { phase++; phaseStart = t; landedAt = -1; }
        } else if (t - phaseStart > 120) {                  // give up on this move, move on
            log(mc, FLIP_NAMES[phase] + " never triggered");
            phase++; phaseStart = t;
        }
        if (t == shotAt) shot(mc, "mario_flips_" + FLIP_NAMES[Math.min(phase, 2)]);
    }

    private static boolean in(int a, int b) { return t >= a && t < b; }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(), msg -> log(mc, "screenshot " + name));
    }

    static void log(MinecraftClient mc, String msg) { MarioController.LOG.info("MARIO64TEST {}", msg); }
}
