package net.irisshaders.iris.pipeline;

/** Mirrored four-mode blocks; each mode occupies every position over a full cycle. */
public final class TerrainDrawExperiment {
    private TerrainDrawExperiment() {}
    private static final int[] ORDER = {0, 1, 3, 2, 2, 3, 1, 0};
    private static final String[] NAMES = {"baseline", "cutout-only", "bounded-solid-only", "both"};

    public static int mode(int frame) {
        if (frame < 0) return 0;
        int block = frame / 32;
        return ORDER[block & 7] ^ ((block / 8) & 3);
    }

    public static String label(int frame) { return NAMES[mode(frame)]; }

    public static boolean useMerged(int frame, boolean cutout) {
        if (frame < 0) return false;
        return (mode(frame) & (cutout ? 1 : 2)) != 0;
    }
}
