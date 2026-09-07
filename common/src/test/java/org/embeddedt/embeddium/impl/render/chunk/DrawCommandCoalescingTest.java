package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DrawCommandCoalescingTest {
    @Test
    void allFaceMasksPreserveActualTriangulatedIndexStream() {
        long mesh = SectionRenderDataUnsafe.allocateHeap(1);
        MultiDrawBatch batch = new MultiDrawBatch(32);
        ByteBuffer indices = MemoryUtil.memAlloc(6 * 4 * 1024);
        try {
            QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(indices, 1024);
            for (int gap : new int[]{0, 4}) {
                int vertex = 100;
                for (int facing = 0; facing < 7; facing++) {
                    SectionRenderDataUnsafe.setVertexOffset(mesh, facing, vertex);
                    SectionRenderDataUnsafe.setElementCount(mesh, facing, 6 * (facing + 1));
                    vertex += 4 * (facing + 1) + gap;
                }
                for (int mask = 0; mask < 128; mask++) {
                    batch.clear();
                    DefaultChunkRenderer.addDrawCommands(batch, mesh, mask, 0);
                    List<Integer> before = expand(batch, indices);
                    int oldSize = batch.size;
                    int capacity = DefaultChunkRenderer.coalesceDrawCommands(batch, false);
                    assertEquals(oldSize, batch.size);
                    assertEquals(before, expand(batch, indices));
                    assertEquals(capacity, DefaultChunkRenderer.coalesceDrawCommands(batch, true));
                    assertEquals(capacity, batch.getIndexBufferSize());
                    assertEquals(before, expand(batch, indices));
                    if (gap == 0 && mask == 127) assertEquals(1, batch.size); // seven draws -> one
                    if (gap != 0) assertEquals(oldSize, batch.size);
                }
            }
        } finally {
            MemoryUtil.memFree(indices);
            batch.delete();
            SectionRenderDataUnsafe.freeHeap(mesh);
        }
    }

    @Test
    void randomizedAdjacentGappedReversedAndIndexedCommandsPreserveOrder() {
        Random random = new Random(0x780L);
        MultiDrawBatch batch = new MultiDrawBatch(128);
        ByteBuffer indices = MemoryUtil.memAlloc(6 * 4 * 4096);
        try {
            QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(indices, 4096);
            for (int trial = 0; trial < 10000; trial++) {
                batch.size = random.nextInt(128);
                int vertex = 1024;
                for (int i = 0; i < batch.size; i++) {
                    int count = 6 * random.nextInt(12);
                    MemoryUtil.memPutInt(batch.pBaseVertex + i * 4L, vertex);
                    MemoryUtil.memPutInt(batch.pElementCount + i * 4L, count);
                    MemoryUtil.memPutAddress(batch.pElementPointer + i * 8L, random.nextInt(8) == 0 ? 24L : 0L);
                    vertex += count / 6 * 4 + (random.nextInt(3) == 0 ? random.nextInt(17) - 8 : 0);
                }
                List<Integer> before = expand(batch, indices);
                int cap = switch (trial % 3) {
                    case 0 -> Integer.MAX_VALUE;
                    case 1 -> batch.getIndexBufferSize();
                    default -> 24;
                };
                int oldSize = batch.size;
                int capacity = DefaultChunkRenderer.coalesceDrawCommands(batch, false, cap);
                assertEquals(oldSize, batch.size);
                assertEquals(before, expand(batch, indices));
                assertEquals(capacity, DefaultChunkRenderer.coalesceDrawCommands(batch, true, cap));
                assertEquals(before, expand(batch, indices));
            }
        } finally {
            MemoryUtil.memFree(indices);
            batch.delete();
        }
    }

    @Test
    void boundedMergesStopAtCapAndNeverSplitOversizedCommands() {
        MultiDrawBatch batch = new MultiDrawBatch(8);
        ByteBuffer indices = MemoryUtil.memAlloc(6 * 4 * 128);
        try {
            QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(indices, 128);
            for (int cap : new int[]{12, 24, 60}) {
                int[] counts = {6, 6, 12, 60, 6, 6};
                batch.size = counts.length;
                int base = 0;
                for (int i = 0; i < counts.length; i++) {
                    MemoryUtil.memPutInt(batch.pBaseVertex + i * 4L, base);
                    MemoryUtil.memPutInt(batch.pElementCount + i * 4L, counts[i]);
                    MemoryUtil.memPutAddress(batch.pElementPointer + i * 8L, 0);
                    base += counts[i] / 6 * 4;
                }
                List<Integer> before = expand(batch, indices);
                DefaultChunkRenderer.coalesceDrawCommands(batch, true, cap);
                assertEquals(before, expand(batch, indices));
                assertEquals(cap == 12 ? 4 : 3, batch.size);
                for (int i = 0; i < batch.size; i++) {
                    int count = MemoryUtil.memGetInt(batch.pElementCount + i * 4L);
                    assertTrue(count <= cap || count == 60);
                }
            }
        } finally {
            MemoryUtil.memFree(indices);
            batch.delete();
        }
    }

    @Test
    void overflowAndPartialQuadsDoNotMerge() {
        MultiDrawBatch batch = new MultiDrawBatch(4);
        try {
            for (int count : new int[]{5, Integer.MAX_VALUE - 1}) {
                batch.size = 2;
                MemoryUtil.memPutInt(batch.pBaseVertex, 0);
                MemoryUtil.memPutInt(batch.pElementCount, count);
                MemoryUtil.memPutInt(batch.pBaseVertex + 4, count / 6 * 4);
                MemoryUtil.memPutInt(batch.pElementCount + 4, 6);
                DefaultChunkRenderer.coalesceDrawCommands(batch, true);
                assertEquals(2, batch.size);
            }
        } finally {
            batch.delete();
        }
    }

    private static List<Integer> expand(MultiDrawBatch batch, ByteBuffer indices) {
        List<Integer> result = new ArrayList<>();
        for (int draw = 0; draw < batch.size; draw++) {
            int base = MemoryUtil.memGetInt(batch.pBaseVertex + draw * 4L);
            int count = MemoryUtil.memGetInt(batch.pElementCount + draw * 4L);
            long pointer = MemoryUtil.memGetAddress(batch.pElementPointer + draw * 8L);
            for (int i = 0; i < count; i++) result.add(base + indices.getInt((int) pointer + i * 4));
        }
        return result;
    }
}
