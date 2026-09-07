package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrawCommandMaskTest {
    @Test
    void allMasksPreserveCommandsAndDoNotWritePastSelectedSlices() {
        long mesh = SectionRenderDataUnsafe.allocateHeap(1);
        MultiDrawBatch batch = new MultiDrawBatch(32);
        try {
            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                SectionRenderDataUnsafe.setVertexOffset(mesh, facing, 100 + facing);
                SectionRenderDataUnsafe.setElementCount(mesh, facing, 6 * (facing + 1));
                SectionRenderDataUnsafe.setIndexOffset(mesh, facing, 24 * facing);
            }
            for (int indexMask : new int[]{0, -1}) {
                for (int mask = 0; mask < (1 << ModelQuadFacing.COUNT); mask++) {
                    for (int prefix : new int[]{0, 3}) {
                        MemoryUtil.memSet(batch.pBaseVertex, 0x55, 32L * Integer.BYTES);
                        MemoryUtil.memSet(batch.pElementCount, 0x55, 32L * Integer.BYTES);
                        MemoryUtil.memSet(batch.pElementPointer, 0x55, 32L * Long.BYTES);
                        batch.size = prefix;
                        DefaultChunkRenderer.addDrawCommands(batch, mesh, mask, indexMask);
                        int expectedSize = prefix + Integer.bitCount(mask);
                        assertEquals(expectedSize, batch.size);
                        int index = prefix;
                        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                            if ((mask & (1 << facing)) == 0) continue;
                            assertEquals(100 + facing, MemoryUtil.memGetInt(batch.pBaseVertex + index * 4L));
                            assertEquals(6 * (facing + 1), MemoryUtil.memGetInt(batch.pElementCount + index * 4L));
                            assertEquals((long) (24 * facing & indexMask), MemoryUtil.memGetAddress(batch.pElementPointer + index * 8L));
                            index++;
                        }
                        for (int i = 0; i < 32; i++) {
                            if (i >= prefix && i < expectedSize) continue;
                            assertEquals(0x55555555, MemoryUtil.memGetInt(batch.pBaseVertex + i * 4L));
                            assertEquals(0x55555555, MemoryUtil.memGetInt(batch.pElementCount + i * 4L));
                            assertEquals(0x5555555555555555L, MemoryUtil.memGetAddress(batch.pElementPointer + i * 8L));
                        }
                    }
                }
            }
        } finally {
            batch.delete();
            SectionRenderDataUnsafe.freeHeap(mesh);
        }
    }
}
