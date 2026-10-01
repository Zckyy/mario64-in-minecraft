package dev.mario64mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings in config/mario64/mario64.json. */
public final class MarioConfig {
    public boolean gamepadEnabled = true;
    /** Radial deadzone for both sticks, 0..0.5. */
    public double deadzone = 0.15;
    /** Right-stick camera speed multiplier (1.0 = 180 deg/s yaw at full tilt). */
    public double cameraSpeed = 1.0;
    public boolean invertCameraY = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MarioConfig instance;

    public static MarioConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() { return MarioController.configDir().resolve("mario64.json"); }

    private static MarioConfig load() {
        try {
            if (Files.exists(file())) {
                MarioConfig c = GSON.fromJson(Files.readString(file()), MarioConfig.class);
                if (c != null) return c.clamp();
            }
        } catch (Exception e) {
            MarioController.LOG.warn("Could not read {}, using defaults", file(), e);
        }
        return new MarioConfig();
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(clamp()));
        } catch (Exception e) {
            MarioController.LOG.warn("Could not save {}", file(), e);
        }
    }

    private MarioConfig clamp() {
        deadzone = Math.max(0, Math.min(0.5, deadzone));
        cameraSpeed = Math.max(0.1, Math.min(3, cameraSpeed));
        return this;
    }
}
