package dev.mario64mc.sm64;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.nio.file.Path;

/**
 * JNA direct mapping of libsm64.h (github.com/libsm64/libsm64), built from the n64decomp/sm64 source.
 * Minecraft 1.21.4 already ships JNA, so the mod needs no extra runtime dependency.
 */
public final class LibSM64 {
    private LibSM64() {}

    public interface DebugPrint extends Callback { void invoke(String msg); }

    // struct sizes / offsets (x64, natural alignment)
    public static final int SURFACE_SIZE = 44;      // int16 type, int16 force, uint16 terrain, pad, int32 v[3][3]
    public static final int INPUTS_SIZE = 20;       // float camLookX, camLookZ, stickX, stickY; u8 A, B, Z
    public static final int STATE_SIZE = 60;
    public static final int STATE_POS = 0, STATE_VEL = 12, STATE_FACE_ANGLE = 24, STATE_FWD_VEL = 28, STATE_HEALTH = 32,
            STATE_ACTION = 36, STATE_ANIM_ID = 40, STATE_ANIM_FRAME = 44, STATE_FLAGS = 48, STATE_PARTICLES = 52, STATE_INVINC = 56;
    public static final int GEOBUF_SIZE = 40;       // 4 pointers + uint16 numTrianglesUsed
    public static final int TEXTURE_WIDTH = 64 * 11, TEXTURE_HEIGHT = 64, GEO_MAX_TRIANGLES = 1024;

    private static boolean loaded;

    public static synchronized void load(Path dll) {
        if (loaded) return;
        Native.register(LibSM64.class, NativeLibrary.getInstance(dll.toAbsolutePath().toString()));
        loaded = true;
    }

    public static boolean isLoaded() { return loaded; }

    public static native void sm64_register_debug_print_function(DebugPrint fn);
    public static native void sm64_global_init(Pointer rom, Pointer outTexture);
    public static native void sm64_global_terminate();
    public static native void sm64_audio_init(Pointer rom);
    public static native int sm64_audio_tick(int numQueuedSamples, int numDesiredSamples, Pointer audioBuffer);

    public static native void sm64_static_surfaces_load(Pointer surfaces, int numSurfaces);

    public static native int sm64_mario_create(float x, float y, float z);
    public static native void sm64_mario_tick(int marioId, Pointer inputs, Pointer outState, Pointer outBuffers);
    public static native void sm64_mario_delete(int marioId);

    public static native void sm64_set_mario_action(int marioId, int action);
    public static native void sm64_set_mario_position(int marioId, float x, float y, float z);
    public static native void sm64_set_mario_faceangle(int marioId, float y);
    public static native void sm64_set_mario_velocity(int marioId, float x, float y, float z);
    public static native void sm64_set_mario_forward_velocity(int marioId, float vel);
    public static native void sm64_set_mario_invincibility(int marioId, short timer);
    public static native void sm64_set_mario_water_level(int marioId, int level);
    public static native void sm64_set_mario_health(int marioId, short health);
    public static native void sm64_mario_take_damage(int marioId, int damage, int subtype, float x, float y, float z);

    public static native void sm64_set_sound_volume(float vol);
    /** mario64mc patch (patches/libsm64-music-volume.patch): music-only volume. */
    public static native void sm64_set_music_volume(float vol);
    public static native void sm64_play_music(byte player, short seqArgs, short fadeTimer);
    public static native void sm64_stop_background_music(short seqId);
    public static native short sm64_get_current_background_music();
}
