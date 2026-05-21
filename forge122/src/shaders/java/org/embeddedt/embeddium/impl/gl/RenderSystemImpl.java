package org.embeddedt.embeddium.impl.gl;

import com.mitchej123.glsm.RenderSystemService;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.FloatBuffer;

public class RenderSystemImpl implements RenderSystemService {
    private static final int MAX_TRACKED_TEXTURES = 32;

    private final int[] shaderTextures = new int[MAX_TRACKED_TEXTURES];
    private final float[] shaderColor = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
    private final FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Matrix4f projectionMatrix = new Matrix4f();

    @Override
    public void glActiveTexture(int texture) {
        GL13.glActiveTexture(texture);
    }

    @Override
    public void enableCullFace() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    @Override
    public void disableCullFace() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Override
    public void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    @Override
    public void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void setUnknownBlendState() {
    }

    @Override
    public void enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    public void disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    @Override
    public void depthFunc(int depthFunc) {
        GL11.glDepthFunc(depthFunc);
    }

    @Override
    public void depthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    @Override
    public void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    @Override
    public void glUniform1i(int location, int value) {
        OpenGlHelper.glUniform1i(location, value);
    }

    @Override
    public void glUniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        OpenGlHelper.glUniformMatrix3(location, transpose, value);
    }

    @Override
    public void glUniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        OpenGlHelper.glUniformMatrix4(location, transpose, value);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clear(int mask, boolean checkError) {
        GL11.glClear(mask);
        if (checkError) {
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("OpenGL error while clearing: " + error);
            }
        }
    }

    @Override
    public void assertOnRenderThread() {
    }

    @Override
    public void assertOnRenderThreadOrInit() {
    }

    @Override
    public void setShaderTexture(int shaderTexture, int textureId) {
        if (shaderTexture >= 0 && shaderTexture < this.shaderTextures.length) {
            this.shaderTextures[shaderTexture] = textureId;
        }
    }

    @Override
    public int getShaderTexture(int shaderTexture) {
        if (shaderTexture < 0 || shaderTexture >= this.shaderTextures.length) {
            return 0;
        }

        return this.shaderTextures[shaderTexture];
    }

    @Override
    public void setShaderColor(float red, float green, float blue, float alpha) {
        this.shaderColor[0] = red;
        this.shaderColor[1] = green;
        this.shaderColor[2] = blue;
        this.shaderColor[3] = alpha;
    }

    @Override
    public float[] getShaderFogColor() {
        EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
        if (entityRenderer == null) {
            return new float[] {0.0f, 0.0f, 0.0f, 1.0f};
        }

        return new float[] {entityRenderer.fogColorRed, entityRenderer.fogColorGreen, entityRenderer.fogColorBlue, 1.0f};
    }

    @Override
    public float getShaderFogStart() {
        return GlStateManager.fogState.start;
    }

    @Override
    public float getShaderFogEnd() {
        return GlStateManager.fogState.end;
    }

    @Override
    public int getFogShape() {
        return 0;
    }

    @Override
    public float getShaderLineWidth() {
        return GL11.glGetFloat(GL11.GL_LINE_WIDTH);
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        this.projectionMatrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, this.projectionMatrixBuffer);
        return this.projectionMatrix.set(this.projectionMatrixBuffer);
    }

    @Override
    public void setProjectionMatrixOrth(Matrix4f projectionMatrix) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        this.projectionMatrixBuffer.clear();
        projectionMatrix.get(this.projectionMatrixBuffer);
        GL11.glLoadMatrix(this.projectionMatrixBuffer);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Override
    public void setProjectionMatrixOrigin(Matrix4f projectionMatrix) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        this.projectionMatrixBuffer.clear();
        projectionMatrix.get(this.projectionMatrixBuffer);
        GL11.glLoadMatrix(this.projectionMatrixBuffer);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
