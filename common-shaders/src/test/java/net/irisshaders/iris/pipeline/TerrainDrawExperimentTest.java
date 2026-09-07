package net.irisshaders.iris.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainDrawExperimentTest {
    @Test
    void independentModesBalancedAtEverySamplingPhaseAndBlockPosition() {
        assertFalse(TerrainDrawExperiment.useMerged(-1, true));
        assertFalse(TerrainDrawExperiment.useMerged(-1, false));
        int[][] positions = new int[8][4];
        for (int phase = 0; phase < 4; phase++) {
            int[] frames = new int[4], samples = new int[4];
            for (int frame = 0; frame < 10240; frame++) {
                int mode = TerrainDrawExperiment.mode(frame);
                assertEquals((mode & 1) != 0, TerrainDrawExperiment.useMerged(frame, true));
                assertEquals((mode & 2) != 0, TerrainDrawExperiment.useMerged(frame, false));
                assertEquals(mode, TerrainDrawExperiment.mode((frame / 32) * 32));
                frames[mode]++;
                if ((frame + phase) % 4 == 0) samples[mode]++;
                if (phase == 0 && frame % 32 == 0) positions[(frame / 32) & 7][mode]++;
            }
            assertArrayEquals(new int[]{2560, 2560, 2560, 2560}, frames);
            assertArrayEquals(new int[]{640, 640, 640, 640}, samples);
        }
        for (int[] position : positions) assertArrayEquals(new int[]{10, 10, 10, 10}, position);
    }
}
