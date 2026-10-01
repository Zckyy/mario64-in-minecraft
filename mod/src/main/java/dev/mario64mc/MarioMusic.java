package dev.mario64mc;

import dev.mario64mc.sm64.LibSM64;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * The original SM64 soundtrack, sequenced live by SM64's own sound engine from the ROM's sequences and soundbanks.
 * In "Auto" the track follows where you are, the way SM64 levels have their own themes.
 */
public final class MarioMusic {
    // decomp/include/seq_ids.h
    public static final int SEQ_TITLE = 0x02, SEQ_GRASS = 0x03, SEQ_CASTLE = 0x04, SEQ_WATER = 0x05, SEQ_HOT = 0x06,
            SEQ_BOSS_KOOPA = 0x07, SEQ_SNOW = 0x08, SEQ_SLIDE = 0x09, SEQ_SPOOKY = 0x0A, SEQ_UNDERGROUND = 0x0C,
            SEQ_POWERUP = 0x0E, SEQ_METAL_CAP = 0x0F, SEQ_KOOPA_ROAD = 0x11, SEQ_MERRY_GO_ROUND = 0x13, SEQ_RACE = 0x14,
            SEQ_BOSS = 0x16, SEQ_ENDLESS_STAIRS = 0x18, SEQ_BOSS_KOOPA_FINAL = 0x19, SEQ_CREDITS = 0x1A, SEQ_FILE_SELECT = 0x21;

    /** Tracks offered in the settings picker, in order. -1 = Auto. */
    public static final int[] PICKER = {-1, SEQ_GRASS, SEQ_CASTLE, SEQ_WATER, SEQ_HOT, SEQ_SNOW, SEQ_SPOOKY, SEQ_UNDERGROUND,
            SEQ_SLIDE, SEQ_KOOPA_ROAD, SEQ_BOSS_KOOPA, SEQ_BOSS_KOOPA_FINAL, SEQ_BOSS, SEQ_POWERUP, SEQ_METAL_CAP,
            SEQ_MERRY_GO_ROUND, SEQ_RACE, SEQ_ENDLESS_STAIRS, SEQ_FILE_SELECT, SEQ_TITLE, SEQ_CREDITS};

    public static String name(int seq) {
        return switch (seq) {
            case -1 -> "Auto (follows where you are)";
            case SEQ_TITLE -> "Title Theme";
            case SEQ_GRASS -> "Bob-omb Battlefield";
            case SEQ_CASTLE -> "Inside the Castle Walls";
            case SEQ_WATER -> "Dire, Dire Docks";
            case SEQ_HOT -> "Lethal Lava Land";
            case SEQ_BOSS_KOOPA -> "Koopa's Theme";
            case SEQ_SNOW -> "Snow Mountain";
            case SEQ_SLIDE -> "Slider";
            case SEQ_SPOOKY -> "Haunted House";
            case SEQ_UNDERGROUND -> "Cave Dungeon";
            case SEQ_POWERUP -> "Powerful Mario";
            case SEQ_METAL_CAP -> "Metallic Mario";
            case SEQ_KOOPA_ROAD -> "Koopa's Road";
            case SEQ_MERRY_GO_ROUND -> "Merry-Go-Round";
            case SEQ_RACE -> "Race Fanfare";
            case SEQ_BOSS -> "Boss Battle";
            case SEQ_ENDLESS_STAIRS -> "Endless Staircase";
            case SEQ_BOSS_KOOPA_FINAL -> "Ultimate Koopa";
            case SEQ_CREDITS -> "Staff Roll";
            case SEQ_FILE_SELECT -> "File Select";
            default -> String.format("Sequence 0x%02X", seq);
        };
    }

    private static int playing = -1;        // sequence we last started (-1 = none)
    private static int candidate = -1, candidateTicks;
    private static float duck = 1f;          // test hook / future fades

    private MarioMusic() {}

    static float volumeScale() { return duck; }

    static void setVolumeScale(float v) { duck = v; }

    public static int playing() { return playing; }

    /** Should SM64 music be playing right now? */
    static boolean wanted(MinecraftClient mc) {
        String mode = MarioConfig.get().music;
        if (mc.world == null || mc.player == null || "off".equals(mode)) return false;
        return "always".equals(mode) || MarioController.INSTANCE.active;
    }

    /** Client tick: pick the track for the current place and switch with a little hysteresis. */
    static void tick(MinecraftClient mc) {
        if (!wanted(mc)) { stop(); return; }
        if ("always".equals(MarioConfig.get().music) && !MarioController.INSTANCE.ensureAudio(mc)) return;
        if (!MarioAudio.started()) return;
        mc.getMusicTracker().stop(); // Minecraft's own music would play on top otherwise

        int fixed = MarioConfig.get().track;
        int want = fixed >= 0 ? fixed : autoTrack(mc);
        if (want == playing) { candidate = -1; return; }
        if (playing >= 0 && want != candidate) { candidate = want; candidateTicks = 0; return; }
        if (playing >= 0 && ++candidateTicks < 40) return; // ~2 s in the new place before switching
        play(mc, want);
    }

    static void play(MinecraftClient mc, int seq) {
        int old = playing;
        playing = seq;
        candidate = -1;
        MarioAudio.run(() -> {
            if (old >= 0) LibSM64.sm64_stop_background_music((short) old);
            LibSM64.sm64_play_music((byte) 0, (short) ((4 << 8) | seq), (short) 0); // SEQUENCE_ARGS(4, seq), level player
        });
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("♪ " + name(seq)).formatted(Formatting.GOLD), true);
        MarioController.LOG.info("MARIO64TEST music -> 0x{} {}", Integer.toHexString(seq), name(seq));
    }

    static void stop() {
        if (playing < 0) return;
        int old = playing;
        playing = -1;
        candidate = -1;
        MarioAudio.run(() -> LibSM64.sm64_stop_background_music((short) old));
    }

    /** SM64 level theme for the player's surroundings. */
    static int autoTrack(MinecraftClient mc) {
        World w = mc.world;
        var p = mc.player;
        BlockPos pos = p.getBlockPos();
        MarioController m = MarioController.INSTANCE;

        boolean bossNear = !w.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, p.getBoundingBox().expand(64),
                e -> e instanceof EnderDragonEntity || e instanceof WitherEntity || e instanceof WardenEntity).isEmpty();
        if (bossNear) return SEQ_BOSS_KOOPA;
        if (w.getRegistryKey() == World.NETHER) return SEQ_HOT;
        if (w.getRegistryKey() == World.END) return SEQ_KOOPA_ROAD;
        boolean swimming = m.active ? (m.action & MarioController.ACT_FLAG_SWIMMING) != 0
                : w.getFluidState(pos.up()).isIn(FluidTags.WATER);
        if (swimming) return SEQ_WATER;
        boolean noSky = w.getLightLevel(LightType.SKY, pos.up()) == 0;
        int depth = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ()) - pos.getY();
        if (noSky && depth >= 8) return SEQ_UNDERGROUND; // deep under the surface: Hazy Maze Cave
        if (noSky) return SEQ_CASTLE;                    // covered but shallow: indoors
        Biome biome = w.getBiome(pos).value();
        if (biome.getTemperature() < 0.15f) return SEQ_SNOW;
        if (!biome.hasPrecipitation() && biome.getTemperature() > 1.5f) return SEQ_HOT; // deserts: Shifting Sand Land shares LLL's theme
        long time = w.getTimeOfDay() % 24000;
        if (time > 13000 && time < 23000) return SEQ_SPOOKY;
        return SEQ_GRASS;
    }
}
