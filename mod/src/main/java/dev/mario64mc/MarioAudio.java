package dev.mario64mc;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import dev.mario64mc.sm64.LibSM64;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Streams libsm64's own SM64 sound engine (Mario's voice, SFX and the sequenced soundtrack) at 32 kHz stereo, like
 * test/audio.cpp. All libsm64 audio calls happen on this thread; other threads queue them with {@link #run(Runnable)}.
 */
final class MarioAudio {
    private static volatile boolean active;
    private static Thread thread;
    private static final ConcurrentLinkedQueue<Runnable> COMMANDS = new ConcurrentLinkedQueue<>();
    /** RMS of the output over the last ~0.5 s, 0..1 (for tests and diagnostics). */
    static volatile float rms;
    private static volatile ByteArrayOutputStream recording;
    private static volatile int recordBytesLeft;
    private static volatile Path recordTarget;

    private MarioAudio() {}

    static void setActive(boolean on) { active = on; }

    static boolean started() { return thread != null; }

    /** Run a libsm64 audio call on the audio thread. */
    static void run(Runnable r) { COMMANDS.add(r); }

    /** Record the next {@code seconds} of output to a WAV file. */
    static void record(Path wav, double seconds) {
        recordTarget = wav;
        recordBytesLeft = (int) (seconds * 32000) * 4;
        recording = new ByteArrayOutputStream();
    }

    static void start(Pointer rom) {
        if (thread != null) return;
        LibSM64.sm64_audio_init(rom);
        thread = new Thread(MarioAudio::loop, "mario64-audio");
        thread.setDaemon(true);
        active = true; // keep ticking once started: music commands, fades and Mario's SFX all need the sequencer running
        thread.start();
    }

    private static void loop() {
        try {
            AudioFormat fmt = new AudioFormat(32000, 16, 2, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 32000 * 4 / 2);
            line.start();
            Memory buf = new Memory(544 * 2 * 2 * 2 * 2);
            byte[] bytes = new byte[(int) buf.size()];
            float lastSfx = -1, lastMusic = -1;
            double sumSq = 0;
            long samples = 0;
            long next = System.nanoTime();
            while (true) {
                for (Runnable r; (r = COMMANDS.poll()) != null; ) r.run();
                if (active) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    float master = mc.options.getSoundVolume(SoundCategory.MASTER);
                    float sfx = master * mc.options.getSoundVolume(SoundCategory.PLAYERS);
                    float music = master * mc.options.getSoundVolume(SoundCategory.MUSIC) * MarioMusic.volumeScale();
                    if (sfx != lastSfx) { LibSM64.sm64_set_sound_volume(sfx); lastSfx = sfx; }
                    if (music != lastMusic) { LibSM64.sm64_set_music_volume(music); lastMusic = music; }
                    int queued = (line.getBufferSize() - line.available()) / 4;
                    int n = LibSM64.sm64_audio_tick(queued, 1100, buf);
                    if (queued < 6000 && n > 0) {
                        int len = Math.min(bytes.length, n * 2 * 4);
                        buf.read(0, bytes, 0, len);
                        line.write(bytes, 0, len);
                        ByteBuffer bb = ByteBuffer.wrap(bytes, 0, len).order(ByteOrder.LITTLE_ENDIAN);
                        for (int i = 0; i + 1 < len; i += 2) { double v = bb.getShort(i) / 32768.0; sumSq += v * v; samples++; }
                        if (samples >= 32000) { rms = (float) Math.sqrt(sumSq / samples); sumSq = 0; samples = 0; }
                        captureForRecording(bytes, len);
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

    private static void captureForRecording(byte[] bytes, int len) throws IOException {
        ByteArrayOutputStream rec = recording;
        if (rec == null) return;
        int take = Math.min(len, recordBytesLeft);
        rec.write(bytes, 0, take);
        recordBytesLeft -= take;
        if (recordBytesLeft > 0) return;
        recording = null;
        byte[] pcm = rec.toByteArray();
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + pcm.length).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 2).putInt(32000).putInt(32000 * 4).putShort((short) 4).putShort((short) 16)
                .put("data".getBytes()).putInt(pcm.length);
        byte[] out = java.util.Arrays.copyOf(h.array(), 44 + pcm.length);
        System.arraycopy(pcm, 0, out, 44, pcm.length);
        Files.createDirectories(recordTarget.getParent());
        Files.write(recordTarget, out);
        MarioController.LOG.info("MARIO64TEST recorded {} ({} s)", recordTarget, pcm.length / 128000.0);
    }
}
