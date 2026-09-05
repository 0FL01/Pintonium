package net.irisshaders.iris.pathways;

/** CPU geometry check against the renderer's actual vertex data; no GL context required. */
public class FullscreenTriangleTest {
    public static void main(String[] args) throws Exception {
        var field = VintageFullScreenQuadRenderer.class.getDeclaredField("VERTICES");
        field.setAccessible(true);
        float[] vertices = (float[]) field.get(null);
        if (vertices.length != 35) throw new AssertionError("Expected compatibility quad and triangle");
        for (int i = 0; i < 7; i++) {
            int p = i * 5;
            if (vertices[p + 2] != 0 || vertices[p] != vertices[p + 3] || vertices[p + 1] != vertices[p + 4]) {
                throw new AssertionError("Position/UV mapping changed");
            }
        }
        // Convexity then establishes coverage and the same affine UV map everywhere inside.
        for (int i = 0; i < 4; i++) {
            float x = vertices[i * 5], y = vertices[i * 5 + 1];
            float bx = vertices[25] - vertices[20], by = vertices[26] - vertices[21];
            float cx = vertices[30] - vertices[20], cy = vertices[31] - vertices[21];
            float area = bx * cy - by * cx;
            if (area <= 0) throw new AssertionError("Triangle is degenerate or reversed");
            float b = ((x - vertices[20]) * cy - (y - vertices[21]) * cx) / area;
            float c = (bx * (y - vertices[21]) - by * (x - vertices[20])) / area;
            float a = 1 - b - c;
            float u = a * vertices[23] + b * vertices[28] + c * vertices[33];
            float v = a * vertices[24] + b * vertices[29] + c * vertices[34];
            if (a < 0 || b < 0 || c < 0 || u != x || v != y) {
                throw new AssertionError("Triangle does not preserve fullscreen coverage/UVs");
            }
        }
        System.out.println("PASS fullscreen triangle coverage and affine UV equivalence");
    }
}
