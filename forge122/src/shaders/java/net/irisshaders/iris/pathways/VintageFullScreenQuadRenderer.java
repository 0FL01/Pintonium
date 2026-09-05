package net.irisshaders.iris.pathways;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import net.irisshaders.iris.IrisCommon;
import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

import java.nio.FloatBuffer;

public class VintageFullScreenQuadRenderer implements FullScreenQuadRenderer {
    private static final int STRIDE = 5 * Float.BYTES;
    private static int vao;
    private static int vbo;

    private boolean depthTestEnabled;
    private boolean cullFaceEnabled;
    private boolean depthMaskEnabled;
    private boolean alphaTestEnabled;
    private Object diagnosticPack;
    private int diagnosticDraws;

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
        this.alphaTestEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // Post-processing may write RGB only (e.g. Chocapic final). The legacy
        // terrain alpha test must not discard those fullscreen fragments.
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
    }

    @Override
    public void renderQuad() {
        boolean trace = false;
        if (IrisCommon.getIrisConfig().areDebugOptionsEnabled()) {
            Object pack = IrisCommon.getCurrentPack().orElse(null);
            if (pack != diagnosticPack) {
                diagnosticPack = pack;
                diagnosticDraws = 0;
            }
            trace = diagnosticDraws++ < 64;
        }
        if (trace) {
            traceFramebuffer("before");
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        if (trace) {
            traceFramebuffer("after");
        }
    }

    private void traceFramebuffer(String stage) {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int drawBuffer = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        IRIS_LOGGER.info("[Shader quad] draw={} stage={} program={} position={} uv={} fbo={} buffer={} viewport={} blend={} scissor={} colorMask={}",
                diagnosticDraws, stage, program, program == 0 ? -1 : GL20.glGetAttribLocation(program, "Position"),
                program == 0 ? -1 : GL20.glGetAttribLocation(program, "UV0"), drawFramebuffer, Integer.toHexString(drawBuffer),
                java.util.Arrays.toString(viewport), GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), colorMask());

        // Readback is diagnostic-only and bounded. Do not reinterpret a client pointer
        // as a PBO offset or inherit pixel packing that could overrun the sample buffer.
        if (drawFramebuffer == 0 || drawBuffer == GL11.GL_NONE || GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_SAMPLES) != 0
                || GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING) != 0
                || GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH) != 0
                || GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS) != 0
                || GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS) != 0
                || viewport[2] <= 0 || viewport[3] <= 0) {
            return;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawFramebuffer);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                IRIS_LOGGER.warn("[Shader quad] Incomplete framebuffer: {} status={}", drawFramebuffer, Integer.toHexString(status));
                return;
            }
            int componentType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                    drawBuffer, GL30.GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE);
            if (componentType == GL11.GL_INT || componentType == GL11.GL_UNSIGNED_INT) {
                return;
            }
            GL11.glReadBuffer(drawBuffer);
            FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
            for (int part = 1; part <= 3; part++) {
                int x = viewport[0] + (viewport[2] - 1) * part / 4;
                int y = viewport[1] + (viewport[3] - 1) * part / 4;
                GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                IRIS_LOGGER.info("[Shader quad pixel] draw={} stage={} xy={},{} rgba={},{},{},{}",
                        diagnosticDraws, stage, x, y, pixel.get(0), pixel.get(1), pixel.get(2), pixel.get(3));
            }
        } finally {
            GL11.glReadBuffer(previousReadBuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        }
    }

    private static String colorMask() {
        var mask = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
        return "" + mask.get(0) + mask.get(1) + mask.get(2) + mask.get(3);
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
        if (this.alphaTestEnabled) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
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
