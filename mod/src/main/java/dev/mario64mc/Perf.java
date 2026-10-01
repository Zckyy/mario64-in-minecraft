package dev.mario64mc;

/** Cheap accumulating timers for diagnosing frame-time problems (read and reset by the soak test). */
public final class Perf {
    public static long buildNs, buildLoadNs, tickNs, attackNs, renderNs, frameNs;
    public static int builds, ticks, frames, maxTicksPerFrame, surfaces;

    private Perf() {}

    public static String report() {
        String s = String.format("frames=%d ticks=%d maxTicks/frame=%d | per tick: sm64 %.3fms attacks %.3fms | builds=%d build %.2fms load %.2fms surfaces=%d | render %.3fms/frame frame() %.3fms",
                frames, ticks, maxTicksPerFrame, ticks == 0 ? 0 : tickNs / 1e6 / ticks, ticks == 0 ? 0 : attackNs / 1e6 / ticks,
                builds, builds == 0 ? 0 : buildNs / 1e6 / builds, builds == 0 ? 0 : buildLoadNs / 1e6 / builds, surfaces,
                frames == 0 ? 0 : renderNs / 1e6 / frames, frames == 0 ? 0 : frameNs / 1e6 / frames);
        buildNs = buildLoadNs = tickNs = attackNs = renderNs = frameNs = 0;
        builds = ticks = frames = maxTicksPerFrame = 0;
        return s;
    }
}
