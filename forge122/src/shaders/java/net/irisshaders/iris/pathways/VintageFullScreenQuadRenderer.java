package net.irisshaders.iris.pathways;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

public class VintageFullScreenQuadRenderer implements FullScreenQuadRenderer {
    private static final int STRIDE = 5 * Float.BYTES;
    private static int vao;
    private static int vbo;

    private boolean depthTestEnabled;
    private boolean cullFaceEnabled;
    private boolean depthMaskEnabled;

    @Override
    public void render() {
        begin();
        renderQuad();
        end();
    }

    @Override
    public void begin() {
        ensureBuffers();

        this.depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        this.cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        this.depthMaskEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
    }

    @Override
    public void renderQuad() {
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    }

    @Override
    public void end() {
        GL20.glDisableVertexAttribArray(1);
        GL20.glDisableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        if (this.depthTestEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }

        if (this.cullFaceEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        GL11.glDepthMask(this.depthMaskEnabled);
    }

    private static void ensureBuffers() {
        if (vao != 0) {
            return;
        }

        float[] vertices = {
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                1.0F, 0.0F, 0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 0.0F, 1.0F,
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F
        };

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }
}
