package dev.mario64mc;

import com.sun.jna.Memory;
import dev.mario64mc.sm64.LibSM64;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Owns the libsm64 Mario instance and binds it to the local Minecraft player. */
public final class MarioController {
    public static final Logger LOG = LoggerFactory.getLogger("mario64");
    public static final MarioController INSTANCE = new MarioController();
    static final String US_ROM_SHA1 = "9bef1128717f958171a4afac3ed78ee2bb4e86ce";

    // decomp/include/sm64.h
    static final int MARIO_PUNCHING = 0x00100000, MARIO_KICKING = 0x00200000, MARIO_TRIPPING = 0x00400000;
    public static final int ACT_FLAG_AIR = 1 << 11, ACT_FLAG_SWIMMING = 1 << 13;
    static final int ACT_GROUND_POUND_LAND = 0x0080023C, ACT_GROUND_POUND = 0x008008A9, ACT_DIVE = 0x0188088A,
            ACT_DIVE_SLIDE = 0x00880456, ACT_SLIDE_KICK = 0x018008AA, ACT_JUMP_KICK = 0x018008AC,
            ACT_DOUBLE_JUMP = 0x03000881;

    static final double TICK = 1.0 / 30.0;
    static final float S = WorldCollision.SCALE;

    private boolean initialized, initFailed;
    private Memory rom, texture;
    private final Memory inputs = new Memory(LibSM64.INPUTS_SIZE), state = new Memory(LibSM64.STATE_SIZE),
            geoBuf = new Memory(LibSM64.GEOBUF_SIZE);
    private final Memory gPos = new Memory(LibSM64.GEO_MAX_TRIANGLES * 9 * 4L), gNorm = new Memory(LibSM64.GEO_MAX_TRIANGLES * 9 * 4L),
            gCol = new Memory(LibSM64.GEO_MAX_TRIANGLES * 9 * 4L), gUv = new Memory(LibSM64.GEO_MAX_TRIANGLES * 6 * 4L);
    @SuppressWarnings("unused") // must stay reachable: native code holds the callback
    private static final LibSM64.DebugPrint DEBUG_PRINT = msg -> {
        // libsm64's audio engine traces every command; only surface real problems
        if (msg.startsWith("Tried") || msg.contains("ERROR") || msg.contains("rror")) LOG.warn("[libsm64] {}", msg);
    };

    public boolean active;
    private int marioId = -1;
    public int ox, oy, oz;
    private World world;
    private ClientPlayerEntity boundPlayer;
    private Perspective savedPerspective;

    // geometry: previous and current 30 Hz frames, interpolated at render time
    public final float[] prevPos = new float[LibSM64.GEO_MAX_TRIANGLES * 9], curPos = new float[LibSM64.GEO_MAX_TRIANGLES * 9],
            curNorm = new float[LibSM64.GEO_MAX_TRIANGLES * 9], curCol = new float[LibSM64.GEO_MAX_TRIANGLES * 9],
            curUv = new float[LibSM64.GEO_MAX_TRIANGLES * 6];
    public int prevTris, curTris;
    private final float[] prevMario = new float[3], curMario = new float[3], vel = new float[3];
    public float alpha;
    public double renderX, renderY, renderZ;

    public int health = 0x880, action, flags;
    public float faceAngle;

    private double acc;
    private long lastNanos;
    private final WorldCollision collision = new WorldCollision();
    private BlockPos lastBuildPos;
    private int ticksSinceBuild;
    private float lastPlayerHealth = -1;
    private final Map<Integer, Integer> hitCooldown = new HashMap<>();
    private int tickCount;

    private static volatile UUID marioPlayer;

    public static boolean isMarioPlayer(UUID id) { return id.equals(marioPlayer); }

    private MarioController() {}

    public static Path configDir() { return FabricLoader.getInstance().getConfigDir().resolve("mario64"); }

    private boolean init(MinecraftClient mc) {
        if (initialized) return true;
        if (initFailed) return false;
        Path dir = configDir();
        Path dll = dir.resolve("sm64.dll"), romPath = dir.resolve("baserom.us.z64");
        try {
            Files.createDirectories(dir);
            if (!Files.exists(dll) || !Files.exists(romPath)) {
                say(mc, Formatting.RED, "Mario 64: put sm64.dll and your own Super Mario 64 (USA) ROM named baserom.us.z64 in " + dir);
                return false;
            }
            byte[] bytes = Files.readAllBytes(romPath);
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            if (!sha1.equals(US_ROM_SHA1)) LOG.warn("ROM SHA1 {} is not the US release ({}); continuing anyway", sha1, US_ROM_SHA1);

            LibSM64.load(dll);
            LibSM64.sm64_register_debug_print_function(DEBUG_PRINT);
            rom = new Memory(bytes.length);
            rom.write(0, bytes, 0, bytes.length);
            texture = new Memory((long) LibSM64.TEXTURE_WIDTH * LibSM64.TEXTURE_HEIGHT * 4);
            LibSM64.sm64_global_init(rom, texture);
            MarioRenderer.uploadTexture(texture.getByteArray(0, (int) texture.size()));
            MarioAudio.start(rom);

            geoBuf.setPointer(0, gPos);
            geoBuf.setPointer(8, gNorm);
            geoBuf.setPointer(16, gCol);
            geoBuf.setPointer(24, gUv);
            initialized = true;
            LOG.info("libsm64 initialised from {}", romPath);
            return true;
        } catch (Throwable t) {
            initFailed = true;
            LOG.error("Failed to initialise libsm64", t);
            say(mc, Formatting.RED, "Mario 64 failed to start: " + t);
            return false;
        }
    }

    public void toggle() {
        if (active) deactivate(); else activate();
    }

    public void activate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || !init(mc)) return;
        world = mc.world;
        boundPlayer = player;
        setOrigin(player.getBlockPos());
        rebuild(player.getBlockPos());
        marioId = LibSM64.sm64_mario_create(rel(player.getX(), ox), rel(player.getY(), oy), rel(player.getZ(), oz));
        if (marioId < 0) {
            say(mc, Formatting.RED, "Mario needs solid ground under you to spawn.");
            return;
        }
        LibSM64.sm64_set_mario_faceangle(marioId, (float) Math.toRadians(net.minecraft.util.math.MathHelper.wrapDegrees(-player.getYaw())));
        curMario[0] = prevMario[0] = rel(player.getX(), ox);
        curMario[1] = prevMario[1] = rel(player.getY(), oy);
        curMario[2] = prevMario[2] = rel(player.getZ(), oz);
        curTris = prevTris = 0;
        acc = 0;
        lastNanos = System.nanoTime();
        lastPlayerHealth = player.getHealth();
        savedPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        active = true;
        marioPlayer = player.getUuid();
        MarioAudio.setActive(true);
        if (!AutoTest.enabled()) say(mc, Formatting.GOLD, "It's-a me, Mario!  [" + MarioControls.moveNames() + "] move  [" + MarioControls.aName()
                + "] A  [" + MarioControls.bName() + "] B  [" + MarioControls.zName() + "] Z  ["
                + MarioControls.showControls.getBoundKeyLocalizedText().getString() + "] controls");
    }

    public void deactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (marioId >= 0) LibSM64.sm64_mario_delete(marioId);
        marioId = -1;
        active = false;
        marioPlayer = null;
        MarioAudio.setActive(false);
        if (savedPerspective != null) mc.options.setPerspective(savedPerspective);
        if (mc.player != null) mc.player.setVelocity(0, 0, 0);
    }

    /** Called once per rendered frame before the camera is set up. */
    public void frame(MinecraftClient mc) {
        if (!active) return;
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) { deactivate(); return; }
        if (mc.world != world || player != boundPlayer) {
            world = mc.world;
            boundPlayer = player;
            teleportToPlayer(player);
        }
        long now = System.nanoTime();
        double dt = Math.min((now - lastNanos) / 1e9, 0.25);
        lastNanos = now;
        if (mc.isPaused() || player.isDead()) dt = 0;
        acc += dt;
        while (acc >= TICK) {
            acc -= TICK;
            tickOnce(mc, player);
            if (!active) return;
        }
        alpha = (float) (acc / TICK);
        renderX = ox + lerp(prevMario[0], curMario[0], alpha) / S;
        renderY = oy + lerp(prevMario[1], curMario[1], alpha) / S;
        renderZ = oz + lerp(prevMario[2], curMario[2], alpha) / S;
        player.setPosition(renderX, renderY, renderZ);
        player.prevX = player.lastRenderX = renderX;
        player.prevY = player.lastRenderY = renderY;
        player.prevZ = player.lastRenderZ = renderZ;
        player.setVelocity(0, 0, 0);
        player.fallDistance = 0;
    }

    /** Called from the client tick: hurt reactions. */
    public void clientTick(MinecraftClient mc) {
        if (!active || mc.player == null) return;
        float hp = mc.player.getHealth();
        if (lastPlayerHealth >= 0 && hp < lastPlayerHealth - 0.01f && hp > 0) {
            // play SM64's knockback reaction, pushed away from the nearest mob if there is one
            double sx = renderX + Math.sin(faceAngle), sz = renderZ + Math.cos(faceAngle);
            Entity near = null;
            for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(4), e -> e instanceof LivingEntity))
                if (near == null || e.squaredDistanceTo(mc.player) < near.squaredDistanceTo(mc.player)) near = e;
            if (near != null) { sx = near.getX(); sz = near.getZ(); }
            LibSM64.sm64_mario_take_damage(marioId, Math.max(1, Math.round(lastPlayerHealth - hp) / 2), 0,
                    rel(sx, ox), rel(renderY, oy), rel(sz, oz));
        }
        lastPlayerHealth = hp;
    }

    /** Server teleported us (command, respawn, correction): move Mario to the player. */
    public void onServerTeleport() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (active && mc.player != null) teleportToPlayer(mc.player);
    }

    private void teleportToPlayer(ClientPlayerEntity player) {
        setOrigin(player.getBlockPos());
        rebuild(player.getBlockPos());
        float x = rel(player.getX(), ox), y = rel(player.getY(), oy), z = rel(player.getZ(), oz);
        LibSM64.sm64_set_mario_position(marioId, x, y, z);
        LibSM64.sm64_set_mario_velocity(marioId, 0, 0, 0);
        LibSM64.sm64_set_mario_forward_velocity(marioId, 0);
        curMario[0] = prevMario[0] = x; curMario[1] = prevMario[1] = y; curMario[2] = prevMario[2] = z;
        prevTris = 0;
        curTris = 0;
    }

    private void tickOnce(MinecraftClient mc, ClientPlayerEntity player) {
        tickCount++;
        // floating origin: keep SM64 coordinates small (its MarioState water level is an s16)
        if (Math.abs(curMario[0]) > 64 * S || Math.abs(curMario[1]) > 64 * S || Math.abs(curMario[2]) > 64 * S) recenter();

        BlockPos mb = BlockPos.ofFloored(ox + curMario[0] / S, oy + curMario[1] / S + 0.5, oz + curMario[2] / S);
        if (!mb.equals(lastBuildPos) || ++ticksSinceBuild >= 10) rebuild(mb);

        LibSM64.sm64_set_mario_water_level(marioId, waterLevel(mb));

        // Mario's power meter mirrors Minecraft health (8 wedges)
        int wedges = Math.max(1, Math.min(8, (int) Math.ceil(player.getHealth() / player.getMaxHealth() * 8)));
        LibSM64.sm64_set_mario_health(marioId, (short) ((wedges << 8) | 0x80));

        writeInputs(mc);

        System.arraycopy(curPos, 0, prevPos, 0, curTris * 9);
        prevTris = curTris;
        System.arraycopy(curMario, 0, prevMario, 0, 3);

        LibSM64.sm64_mario_tick(marioId, inputs, state, geoBuf);

        state.read(LibSM64.STATE_POS, curMario, 0, 3);
        state.read(LibSM64.STATE_VEL, vel, 0, 3);
        faceAngle = state.getFloat(LibSM64.STATE_FACE_ANGLE);
        health = state.getShort(LibSM64.STATE_HEALTH);
        action = state.getInt(LibSM64.STATE_ACTION);
        flags = state.getInt(LibSM64.STATE_FLAGS);
        curTris = geoBuf.getShort(32) & 0xFFFF;
        gPos.read(0, curPos, 0, curTris * 9);
        gNorm.read(0, curNorm, 0, curTris * 9);
        gCol.read(0, curCol, 0, curTris * 9);
        gUv.read(0, curUv, 0, curTris * 6);

        attacks(mc, player);
        if (AutoTest.enabled()) AutoTest.marioTick(mc);
    }

    private final float[] scripted = new float[5];

    public double renderXNow() { return ox + curMario[0] / S; }
    public double renderYNow() { return oy + curMario[1] / S; }
    public double renderZNow() { return oz + curMario[2] / S; }

    private void writeInputs(MinecraftClient mc) {
        var o = mc.options;
        float fwd = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        float strafe = (o.rightKey.isPressed() ? 1 : 0) - (o.leftKey.isPressed() ? 1 : 0);
        float len = (float) Math.sqrt(fwd * fwd + strafe * strafe);
        if (len > 1) { fwd /= len; strafe /= len; }
        float yaw = (float) Math.toRadians(mc.gameRenderer.getCamera().getYaw());
        inputs.setFloat(0, (float) -Math.sin(yaw)); // camLookX
        inputs.setFloat(4, (float) Math.cos(yaw));  // camLookZ
        if (AutoTest.enabled()) {
            AutoTest.fillInputs(scripted);
            if (scripted[0] != 0 || scripted[1] != 0 || scripted[2] + scripted[3] + scripted[4] > 0) {
                strafe = scripted[0]; fwd = -scripted[1];
            }
        }
        inputs.setFloat(8, strafe);                  // stickX: +1 = right
        inputs.setFloat(12, -fwd);                   // stickY: -1 = forward (N64 convention)
        inputs.setByte(16, (byte) (MarioControls.a(mc) || scripted[2] > 0 ? 1 : 0));   // A
        inputs.setByte(17, (byte) (MarioControls.b(mc) || scripted[3] > 0 ? 1 : 0)); // B
        inputs.setByte(18, (byte) (MarioControls.z(mc) || scripted[4] > 0 ? 1 : 0));  // Z
    }

    /** SM64 water level (absolute units) for Mario's column, or very low when there is no water. */
    private int waterLevel(BlockPos mb) {
        BlockPos.Mutable p = mb.mutableCopy();
        if (!world.getFluidState(p).isIn(FluidTags.WATER)) p.move(0, -1, 0);
        if (world.getFluidState(p).isIn(FluidTags.WATER)) {
            while (world.getFluidState(p.up()).isIn(FluidTags.WATER) && p.getY() < world.getTopYInclusive()) p.move(0, 1, 0);
        } else {
            int limit = 40;
            while (limit-- > 0 && p.getY() > world.getBottomY()) {
                var st = world.getBlockState(p);
                if (st.getFluidState().isIn(FluidTags.WATER)) break;
                if (!st.isAir() && !st.getCollisionShape(world, p).isEmpty()) return -30000;
                if (!st.getFluidState().isEmpty()) return -30000;
                p.move(0, -1, 0);
            }
            if (!world.getFluidState(p).isIn(FluidTags.WATER)) return -30000;
        }
        FluidState f = world.getFluidState(p);
        double top = p.getY() + f.getHeight(world, p);
        return (int) Math.max(-30000, Math.min(30000, (top - oy) * S));
    }

    private void attacks(MinecraftClient mc, ClientPlayerEntity player) {
        hitCooldown.entrySet().removeIf(e -> e.getValue() <= tickCount);
        double mx = ox + curMario[0] / S, my = oy + curMario[1] / S, mz = oz + curMario[2] / S;
        boolean kick = (flags & (MARIO_KICKING | MARIO_TRIPPING)) != 0 || action == ACT_SLIDE_KICK || action == ACT_JUMP_KICK;
        boolean punch = (flags & MARIO_PUNCHING) != 0 || action == ACT_DIVE || action == ACT_DIVE_SLIDE;
        boolean pound = action == ACT_GROUND_POUND_LAND;
        if (kick || punch || pound) {
            double fx = Math.sin(faceAngle), fz = Math.cos(faceAngle);
            Box box = pound ? new Box(mx - 1.6, my - 0.3, mz - 1.6, mx + 1.6, my + 1.0, mz + 1.6)
                    : new Box(mx + fx * 0.7 - 0.8, my + 0.1, mz + fz * 0.7 - 0.8, mx + fx * 0.7 + 0.8, my + 1.5, mz + fz * 0.7 + 0.8);
            for (Entity e : world.getOtherEntities(player, box, e -> e instanceof LivingEntity && e.isAlive())) {
                hit(mc, player, e, pound ? 10f : kick ? 6f : 4f, mx, mz);
            }
        }
        // stomp: falling onto a mob's head bounces Mario like a Goomba stomp
        if ((action & ACT_FLAG_AIR) != 0 && vel[1] < 0 && !pound) {
            Box feet = new Box(mx - 0.4, my - 0.4, mz - 0.4, mx + 0.4, my + 0.1, mz + 0.4);
            for (Entity e : world.getOtherEntities(player, feet, e -> e instanceof LivingEntity && e.isAlive())) {
                if (e.getBoundingBox().maxY > my + 0.15 || hitCooldown.containsKey(e.getId())) continue;
                hit(mc, player, e, action == ACT_GROUND_POUND ? 10f : 5f, mx, mz);
                LibSM64.sm64_set_mario_action(marioId, ACT_DOUBLE_JUMP);
                break;
            }
        }
    }

    private void hit(MinecraftClient mc, ClientPlayerEntity player, Entity e, float dmg, double mx, double mz) {
        if (hitCooldown.containsKey(e.getId())) return;
        hitCooldown.put(e.getId(), tickCount + 12);
        IntegratedServer srv = mc.getServer();
        if (srv == null) { // multiplayer: fall back to a normal melee hit packet
            mc.interactionManager.attackEntity(player, e);
            return;
        }
        int id = e.getId();
        UUID uuid = player.getUuid();
        var dimension = world.getRegistryKey();
        double kx = mx - e.getX(), kz = mz - e.getZ();
        srv.execute(() -> {
            ServerWorld sw = srv.getWorld(dimension);
            ServerPlayerEntity sp = srv.getPlayerManager().getPlayer(uuid);
            if (sw == null || sp == null) return;
            if (sw.getEntityById(id) instanceof LivingEntity le && le.isAlive()) {
                le.damage(sw, sw.getDamageSources().playerAttack(sp), dmg);
                le.takeKnockback(dmg >= 10 ? 1.0 : 0.6, kx, kz);
            }
        });
    }

    private void recenter() {
        int nx = (int) Math.floor(ox + curMario[0] / S), ny = (int) Math.floor(oy + curMario[1] / S), nz = (int) Math.floor(oz + curMario[2] / S);
        float dx = (ox - nx) * S, dy = (oy - ny) * S, dz = (oz - nz) * S;
        setOrigin(new BlockPos(nx, ny, nz));
        for (int i = 0; i < curTris * 9; i += 3) { curPos[i] += dx; curPos[i + 1] += dy; curPos[i + 2] += dz; }
        curMario[0] += dx; curMario[1] += dy; curMario[2] += dz;
        prevMario[0] += dx; prevMario[1] += dy; prevMario[2] += dz;
        rebuild(new BlockPos(nx, ny, nz));
        LibSM64.sm64_set_mario_position(marioId, curMario[0], curMario[1], curMario[2]);
    }

    public java.util.List<String> dumpNear(int radiusUnits) {
        int x = (int) curMario[0], y = (int) curMario[1], z = (int) curMario[2];
        return collision.dump(x - radiusUnits, y - radiusUnits, z - radiusUnits, x + radiusUnits, y + radiusUnits * 2, z + radiusUnits);
    }

    private void setOrigin(BlockPos p) { ox = p.getX(); oy = p.getY(); oz = p.getZ(); }

    private void rebuild(BlockPos center) {
        collision.build(world, center, ox, oy, oz);
        LibSM64.sm64_static_surfaces_load(com.sun.jna.Native.getDirectBufferPointer(collision.buffer()), collision.count());
        lastBuildPos = center;
        ticksSinceBuild = 0;
    }

    static float rel(double mc, int origin) { return (float) ((mc - origin) * S); }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static void say(MinecraftClient mc, Formatting f, String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(msg).formatted(f), false);
        LOG.info(msg);
    }
}
