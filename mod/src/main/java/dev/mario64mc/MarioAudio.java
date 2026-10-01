package dev.mario64mc;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import dev.mario64mc.sm64.LibSM64;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Streams libsm64's own SM64 sound engine (Mario's voice, footsteps, jingles) at 32 kHz stereo, like test/audio.cpp. */
final class MarioAudio {
    private static volatile boolean active;
    private static Thread thread;

    private MarioAudio() {}

    static void setActive(boolean on) { active = on; }

    static void start(Pointer rom) {
        if (thread != null) return;
        LibSM64.sm64_audio_init(rom);
        thread = new Thread(MarioAudio::run, "mario64-audio");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        try {
            AudioFormat fmt = new AudioFormat(32000, 16, 2, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 32000 * 4 / 2);
            line.start();
            Memory buf = new Memory(544 * 2 * 2 * 2 * 2);
            byte[] bytes = new byte[(int) buf.size()];
            float lastVol = -1;
            long next = System.nanoTime();
            while (true) {
                if (active) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    float vol = mc.options.getSoundVolume(SoundCategory.MASTER) * mc.options.getSoundVolume(SoundCategory.PLAYERS);
                    if (vol != lastVol) { LibSM64.sm64_set_sound_volume(vol); lastVol = vol; }
                    int queued = (line.getBufferSize() - line.available()) / 4;
                    int n = LibSM64.sm64_audio_tick(queued, 1100, buf);
                    if (queued < 6000 && n > 0) {
                        int len = Math.min(bytes.length, n * 2 * 4);
                        buf.read(0, bytes, 0, len);
                        line.write(bytes, 0, len);
                    }
                }
                next += 33_000_000L;
                long sleep = next - System.nanoTime();
                if (sleep > 0) Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                else next = System.nanoTime();
            }
        } catch (Throwable t) {
            MarioController.LOG.error("Mario audio thread stopped", t);
        }
    }
}
