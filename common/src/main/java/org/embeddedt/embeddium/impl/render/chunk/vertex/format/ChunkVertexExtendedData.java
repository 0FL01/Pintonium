package org.embeddedt.embeddium.impl.render.chunk.vertex.format;

import org.embeddedt.embeddium.api.util.NormI8;

/**
 * Thread-local metadata used by extended shader attributes while chunk geometry is encoded.
 */
public final class ChunkVertexExtendedData {
    private static final int MID_TEXTURE_MAX_VALUE = 32768;
    private static final ThreadLocal<Data> CURRENT = ThreadLocal.withInitial(Data::new);

    private ChunkVertexExtendedData() {
    }

    public static Data current() {
        return CURRENT.get();
    }

    public static void set(int blockId, short renderType, int midTexCoord, int normal, int tangent,
                           int localX, int localY, int localZ, byte lightValue) {
        Data data = CURRENT.get();
        data.blockId = (short) blockId;
        data.renderType = renderType;
        data.midTexCoord = midTexCoord;
        data.normal = normal;
        data.tangent = tangent;
        data.localX = localX;
        data.localY = localY;
        data.localZ = localZ;
        data.lightValue = lightValue;
    }

    public static void setGeometry(int midTexCoord, int normal, int tangent) {
        Data data = CURRENT.get();
        data.reset();
        data.midTexCoord = midTexCoord;
        data.normal = normal;
        data.tangent = tangent;
    }

    public static void clear() {
        CURRENT.get().reset();
    }

    public static int encodeMidTexCoord(float u, float v) {
        return ((Math.round(u * MID_TEXTURE_MAX_VALUE) & 0xFFFF) << 0) |
                ((Math.round(v * MID_TEXTURE_MAX_VALUE) & 0xFFFF) << 16);
    }

    public static int computeMidBlock(float x, float y, float z, int localX, int localY, int localZ) {
        return packMidBlock(localX + 0.5f - x, localY + 0.5f - y, localZ + 0.5f - z);
    }

    private static int packMidBlock(float x, float y, float z) {
        return ((int) (x * 64) & 0xFF) | (((int) (y * 64) & 0xFF) << 8) | (((int) (z * 64) & 0xFF) << 16);
    }

    public static int computeTangent(ChunkVertexEncoder.Vertex[] quad, int packedNormal) {
        ChunkVertexEncoder.Vertex v0 = quad[0];
        ChunkVertexEncoder.Vertex v1 = quad[1];
        ChunkVertexEncoder.Vertex v2 = quad[2];

        return computeTangent(
                NormI8.unpackX(packedNormal), NormI8.unpackY(packedNormal), NormI8.unpackZ(packedNormal),
                v0.x, v0.y, v0.z, v0.u, v0.v,
                v1.x, v1.y, v1.z, v1.u, v1.v,
                v2.x, v2.y, v2.z, v2.u, v2.v);
    }

    private static int computeTangent(float normalX, float normalY, float normalZ,
                                      float x0, float y0, float z0, float u0, float v0,
                                      float x1, float y1, float z1, float u1, float v1,
                                      float x2, float y2, float z2, float u2, float v2) {
        float edge1x = x1 - x0;
        float edge1y = y1 - y0;
        float edge1z = z1 - z0;

        float edge2x = x2 - x0;
        float edge2y = y2 - y0;
        float edge2z = z2 - z0;

        float deltaU1 = u1 - u0;
        float deltaV1 = v1 - v0;
        float deltaU2 = u2 - u0;
        float deltaV2 = v2 - v0;

        float denom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
        float f = denom == 0.0f ? 1.0f : 1.0f / denom;

        float tangentX = f * (deltaV2 * edge1x - deltaV1 * edge2x);
        float tangentY = f * (deltaV2 * edge1y - deltaV1 * edge2y);
        float tangentZ = f * (deltaV2 * edge1z - deltaV1 * edge2z);
        float tangentCoeff = rsqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        tangentX *= tangentCoeff;
        tangentY *= tangentCoeff;
        tangentZ *= tangentCoeff;

        float bitangentX = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
        float bitangentY = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
        float bitangentZ = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
        float bitangentCoeff = rsqrt(bitangentX * bitangentX + bitangentY * bitangentY + bitangentZ * bitangentZ);
        bitangentX *= bitangentCoeff;
        bitangentY *= bitangentCoeff;
        bitangentZ *= bitangentCoeff;

        float predictedBitangentX = tangentY * normalZ - tangentZ * normalY;
        float predictedBitangentY = tangentZ * normalX - tangentX * normalZ;
        float predictedBitangentZ = tangentX * normalY - tangentY * normalX;

        float dot = bitangentX * predictedBitangentX + bitangentY * predictedBitangentY + bitangentZ * predictedBitangentZ;
        float tangentW = dot < 0 ? -1.0f : 1.0f;

        return packNormal(tangentX, tangentY, tangentZ, tangentW);
    }

    private static int packNormal(float x, float y, float z, float w) {
        return (encodeNormal(x) << 0) | (encodeNormal(y) << 8) | (encodeNormal(z) << 16) | (encodeNormal(w) << 24);
    }

    private static int encodeNormal(float value) {
        return ((int) (Math.max(-1.0f, Math.min(1.0f, value)) * 127.0f)) & 0xFF;
    }

    private static float rsqrt(float value) {
        return value == 0.0f ? 1.0f : (float) (1.0 / Math.sqrt(value));
    }

    public static final class Data {
        public short blockId;
        public short renderType;
        public int midTexCoord;
        public int normal;
        public int tangent;
        public int localX;
        public int localY;
        public int localZ;
        public byte lightValue;

        private Data() {
            reset();
        }

        private void reset() {
            this.blockId = -1;
            this.renderType = -1;
            this.midTexCoord = 0;
            this.normal = 0;
            this.tangent = 0;
            this.localX = 0;
            this.localY = 0;
            this.localZ = 0;
            this.lightValue = 0;
        }
    }
}
