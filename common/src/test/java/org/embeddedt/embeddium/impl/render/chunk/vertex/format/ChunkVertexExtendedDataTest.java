package org.embeddedt.embeddium.impl.render.chunk.vertex.format;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkVertexExtendedDataTest {
    @AfterEach
    void clearThreadLocalData() {
        ChunkVertexExtendedData.clear();
    }

    @Test
    void geometryFallbackDoesNotReuseBlockMetadata() {
        ChunkVertexExtendedData.set(42, (short) 3, 10, 11, 12, 13, 14, 15, (byte) 7);

        ChunkVertexExtendedData.setGeometry(101, 102, 103);

        ChunkVertexExtendedData.Data data = ChunkVertexExtendedData.current();
        assertEquals(-1, data.blockId);
        assertEquals(-1, data.renderType);
        assertEquals(101, data.midTexCoord);
        assertEquals(102, data.normal);
        assertEquals(103, data.tangent);
        assertEquals(0, data.localX);
        assertEquals(0, data.localY);
        assertEquals(0, data.localZ);
        assertEquals(0, data.lightValue);
    }
}
