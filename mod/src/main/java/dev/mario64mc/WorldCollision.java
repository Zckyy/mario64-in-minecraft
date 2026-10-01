package dev.mario64mc;

import dev.mario64mc.sm64.LibSM64;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Turns the Minecraft blocks around Mario into SM64 collision triangles (struct SM64Surface).
 * Coordinates are relative to a floating origin and scaled: 1 block = 100 SM64 units.
 */
public final class WorldCollision {
    public static final float SCALE = 100f;

    // decomp/include/surface_terrains.h
    static final short SURFACE_DEFAULT = 0x0000, SURFACE_BURNING = 0x0001, SURFACE_NOT_SLIPPERY = 0x0015,
            SURFACE_SLIPPERY = 0x0014, SURFACE_ICE = 0x002E, SURFACE_SHALLOW_QUICKSAND = 0x0021;
    static final short TERRAIN_GRASS = 0, TERRAIN_STONE = 1, TERRAIN_SNOW = 2, TERRAIN_SAND = 3, TERRAIN_SPOOKY = 4,
            TERRAIN_WATER = 5;

    static final int RADIUS = 8, UP = 7, DOWN = 6, EXTRA_DOWN = 64;

    private ByteBuffer buf = ByteBuffer.allocateDirect(LibSM64.SURFACE_SIZE * 8192).order(ByteOrder.LITTLE_ENDIAN);
    private int count;
    private int ox, oy, oz;
    private short type, terrain;

    public ByteBuffer buffer() { return buf; }
    public int count() { return count; }

    public void build(World world, BlockPos center, int originX, int originY, int originZ) {
        ox = originX; oy = originY; oz = originZ;
        count = 0;
        buf.clear();
        BlockPos.Mutable p = new BlockPos.Mutable();
        BlockPos.Mutable n = new BlockPos.Mutable();
        int minY = Math.max(world.getBottomY(), center.getY() - DOWN);
        int maxY = Math.min(world.getTopYInclusive(), center.getY() + UP);
        for (int x = center.getX() - RADIUS; x <= center.getX() + RADIUS; x++) {
            for (int z = center.getZ() - RADIUS; z <= center.getZ() + RADIUS; z++) {
                for (int y = minY; y <= maxY; y++) {
                    p.set(x, y, z);
                    addBlock(world, p, n, true);
                }
                // keep a floor under every column so SM64 never sees "no floor" (which acts as an invisible wall)
                boolean found = false;
                for (int y = minY - 1; y >= Math.max(world.getBottomY(), minY - EXTRA_DOWN); y--) {
                    p.set(x, y, z);
                    if (addBlock(world, p, n, false)) { found = true; break; }
                }
                if (!found) { // void or a very deep pit: a floor far below, so the drop is a fall and not a wall
                    type = SURFACE_DEFAULT; terrain = TERRAIN_STONE;
                    double fy = minY - EXTRA_DOWN - 1;
                    quad(x, fy, z, x + 1, fy, z, x + 1, fy, z + 1, x, fy, z + 1, 0, 1, 0);
                }
            }
        }
        buf.flip();
    }

    /** @return true if the position produced any collision */
    private boolean addBlock(World world, BlockPos.Mutable p, BlockPos.Mutable n, boolean allFaces) {
        BlockState state = world.getBlockState(p);
        FluidState fluid = state.getFluidState();
        int x = p.getX(), y = p.getY(), z = p.getZ();

        if (fluid.isIn(FluidTags.LAVA) && !world.getFluidState(n.set(x, y + 1, z)).isIn(FluidTags.LAVA)) {
            type = SURFACE_BURNING; terrain = TERRAIN_STONE;
            float h = fluid.getHeight(world, p);
            quad(x, y + h, z, x + 1, y + h, z, x + 1, y + h, z + 1, x, y + h, z + 1, 0, 1, 0);
            if (state.isAir() || state.getCollisionShape(world, p).isEmpty()) return true;
        }
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(world, p);
        if (shape.isEmpty()) return false;
        classify(state);

        if (allFaces && state.getBlock() instanceof StairsBlock && state.get(StairsBlock.HALF) == BlockHalf.BOTTOM
                && state.get(StairsBlock.SHAPE) == StairShape.STRAIGHT) {
            stairsRamp(world, p, n, state.get(StairsBlock.FACING));
            return true;
        }

        boolean full = Block.isShapeFullCube(shape);
        for (Box b : shape.getBoundingBoxes()) {
            double x0 = x + b.minX, x1 = x + b.maxX, y0 = y + b.minY, y1 = y + b.maxY, z0 = z + b.minZ, z1 = z + b.maxZ;
            // top face -> floor
            if (!(b.maxY >= 0.999 && isFull(world, n.set(x, y + 1, z))))
                quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0);
            if (!allFaces) continue;
            // bottom face -> ceiling
            if (!(b.minY <= 0.001 && isFull(world, n.set(x, y - 1, z))))
                quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
            // Short boxes that sit on the block floor (slabs, carpets, snow layers) get no walls, so Mario steps up
            // onto them like he does on SM64 stairs instead of being blocked by a 50-unit wall.
            if (!full && b.minY <= 0.001 && b.maxY <= 0.51) continue;
            if (!(b.minX <= 0.001 && isFull(world, n.set(x - 1, y, z))))
                quad(x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0);
            if (!(b.maxX >= 0.999 && isFull(world, n.set(x + 1, y, z))))
                quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0);
            if (!(b.minZ <= 0.001 && isFull(world, n.set(x, y, z - 1))))
                quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
            if (!(b.maxZ >= 0.999 && isFull(world, n.set(x, y, z + 1))))
                quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        }
        return true;
    }

    /** A straight bottom stair becomes a 45-degree non-slippery ramp, which is how SM64 builds its staircases. */
    private void stairsRamp(World world, BlockPos.Mutable p, BlockPos.Mutable n, Direction facing) {
        int x = p.getX(), y = p.getY(), z = p.getZ();
        type = SURFACE_NOT_SLIPPERY;
        // local frame: "f" runs from the low edge (0) to the high edge (1) along `facing`
        double[][] c = new double[4][];
        // corner(lowEdge/highEdge, side 0/1)
        for (int i = 0; i < 4; i++) {
            int f = i / 2, s = i % 2;
            double fx, fz;
            switch (facing) {
                case NORTH -> { fx = s; fz = 1 - f; }
                case SOUTH -> { fx = s; fz = f; }
                case WEST -> { fx = 1 - f; fz = s; }
                default -> { fx = f; fz = s; } // EAST
            }
            c[i] = new double[]{x + fx, z + fz};
        }
        // ramp floor: low edge at y, high edge at y+1
        quad(c[0][0], y, c[0][1], c[1][0], y, c[1][1], c[3][0], y + 1, c[3][1], c[2][0], y + 1, c[2][1],
                -facing.getOffsetX() * 0.7071, 0.7071, -facing.getOffsetZ() * 0.7071);
        // high back wall
        if (!isFull(world, n.set(x + facing.getOffsetX(), y, z + facing.getOffsetZ())))
            quad(c[2][0], y, c[2][1], c[3][0], y, c[3][1], c[3][0], y + 1, c[3][1], c[2][0], y + 1, c[2][1],
                    facing.getOffsetX(), 0, facing.getOffsetZ());
        // triangular side walls
        for (int s = 0; s < 2; s++) {
            double[] lo = c[s], hi = c[2 + s];
            Direction side = s == 0 ? (facing.getAxis() == Direction.Axis.Z ? Direction.WEST : Direction.NORTH)
                    : (facing.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH);
            tri(lo[0], y, lo[1], hi[0], y, hi[1], hi[0], y + 1, hi[1], side.getOffsetX(), 0, side.getOffsetZ());
        }
        if (!isFull(world, n.set(x, y - 1, z)))
            quad(x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, 0, -1, 0);
    }

    private static boolean isFull(World world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        return !s.isAir() && Block.isShapeFullCube(s.getCollisionShape(world, pos));
    }

    private void classify(BlockState s) {
        type = SURFACE_DEFAULT;
        if (s.isIn(BlockTags.ICE)) { type = SURFACE_ICE; terrain = TERRAIN_SNOW; return; }
        if (s.isOf(Blocks.MAGMA_BLOCK)) { type = SURFACE_BURNING; terrain = TERRAIN_STONE; return; }
        if (s.isOf(Blocks.SOUL_SAND)) { type = SURFACE_SHALLOW_QUICKSAND; terrain = TERRAIN_SAND; return; }
        BlockSoundGroup g = s.getSoundGroup();
        if (g == BlockSoundGroup.GRASS || g == BlockSoundGroup.GRAVEL || g == BlockSoundGroup.ROOTED_DIRT
                || g == BlockSoundGroup.MOSS_BLOCK || g == BlockSoundGroup.MUD || g == BlockSoundGroup.AZALEA_LEAVES)
            terrain = TERRAIN_GRASS;
        else if (g == BlockSoundGroup.SNOW || g == BlockSoundGroup.POWDER_SNOW || g == BlockSoundGroup.WOOL)
            terrain = TERRAIN_SNOW;
        else if (g == BlockSoundGroup.SAND || g == BlockSoundGroup.SUSPICIOUS_SAND)
            terrain = TERRAIN_SAND;
        else if (g == BlockSoundGroup.WOOD || g == BlockSoundGroup.NETHER_WOOD || g == BlockSoundGroup.BAMBOO_WOOD
                || g == BlockSoundGroup.CHERRY_WOOD || g == BlockSoundGroup.LADDER)
            terrain = TERRAIN_SPOOKY; // SM64's "spooky" terrain is the creaky wooden floor sound
        else
            terrain = TERRAIN_STONE;
    }

    private void quad(double ax, double ay, double az, double bx, double by, double bz,
                      double cx, double cy, double cz, double dx, double dy, double dz,
                      double nx, double ny, double nz) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, nx, ny, nz);
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, nx, ny, nz);
    }

    /** Emits a triangle, flipping its winding so libsm64's (v2-v1)x(v3-v2) normal points along (nx,ny,nz). */
    private void tri(double ax, double ay, double az, double bx, double by, double bz,
                     double cx, double cy, double cz, double nx, double ny, double nz) {
        int[] v = {u(ax, ox), u(ay, oy), u(az, oz), u(bx, ox), u(by, oy), u(bz, oz), u(cx, ox), u(cy, oy), u(cz, oz)};
        long e1x = v[3] - v[0], e1y = v[4] - v[1], e1z = v[5] - v[2];
        long e2x = v[6] - v[3], e2y = v[7] - v[4], e2z = v[8] - v[5];
        double cxn = e1y * e2z - e1z * e2y, cyn = e1z * e2x - e1x * e2z, czn = e1x * e2y - e1y * e2x;
        if (cxn == 0 && cyn == 0 && czn == 0) return;
        if (cxn * nx + cyn * ny + czn * nz < 0) { // swap v2 and v3
            for (int i = 0; i < 3; i++) { int t = v[3 + i]; v[3 + i] = v[6 + i]; v[6 + i] = t; }
        }
        if (buf.remaining() < LibSM64.SURFACE_SIZE) grow();
        buf.putShort(type).putShort((short) 0).putShort(terrain).putShort((short) 0);
        for (int i : v) buf.putInt(i);
        count++;
    }

    /** Debug: every triangle with a vertex inside the given SM64-unit box. */
    public java.util.List<String> dump(int x0, int y0, int z0, int x1, int y1, int z1) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int b = i * LibSM64.SURFACE_SIZE;
            int[] v = new int[9];
            for (int k = 0; k < 9; k++) v[k] = buf.getInt(b + 8 + k * 4);
            boolean hit = false;
            for (int k = 0; k < 3; k++) hit |= v[k*3] >= x0 && v[k*3] <= x1 && v[k*3+1] >= y0 && v[k*3+1] <= y1 && v[k*3+2] >= z0 && v[k*3+2] <= z1;
            if (hit) out.add(String.format("type=%d (%d,%d,%d) (%d,%d,%d) (%d,%d,%d)", buf.getShort(b), v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8]));
        }
        return out;
    }

    private void grow() {
        ByteBuffer nb = ByteBuffer.allocateDirect(buf.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.flip();
        nb.put(buf);
        buf = nb;
    }

    private static int u(double mc, int origin) { return (int) Math.round((mc - origin) * SCALE); }
}
