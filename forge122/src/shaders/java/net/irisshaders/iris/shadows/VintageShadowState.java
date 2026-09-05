package net.irisshaders.iris.shadows;

import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.program.Program;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.taumc.celeritas.impl.compat.distanthorizons.DhRenderStateSnapshot;
import org.taumc.celeritas.impl.render.GlMatrixSnapshot;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer;

import static com.mitchej123.glsm.GLStateManagerService.GL_STATE_MANAGER;

/** Owns legacy stacks as well as the GL objects not covered by glPushAttrib. */
final class VintageShadowState implements AutoCloseable {
    private final DhRenderStateSnapshot renderState = DhRenderStateSnapshot.capture();
    private final GlMatrixSnapshot matrices = GlMatrixSnapshot.capture();
    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int modelDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
    private final int projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
    private final float lightmapX = OpenGlHelper.lastBrightnessX;
    private final float lightmapY = OpenGlHelper.lastBrightnessY;
    private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int[] textures = new int[Math.min(32, GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))];
    private final int[] samplers = new int[textures.length];
    private final boolean[] textureEnabled = new boolean[textures.length];
    private final FloatBuffer[] textureMatrices = new FloatBuffer[GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS)];
    private final int[] textureDepths = new int[textureMatrices.length];
    private final FloatBuffer[] attributes = new FloatBuffer[GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)];
    private final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    private final int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
    private final float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
    private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
    private final boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean rescaleNormal = GL11.glIsEnabled(GL12_RESCALE_NORMAL);
    private final boolean normalize = GL11.glIsEnabled(GL11.GL_NORMALIZE);
    private final FloatBuffer color;
    private final ByteBuffer colorMask;
    private final int shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
    private final boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
    private final boolean[] lights = new boolean[8];
    private final boolean polygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
    private final float polygonFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
    private final float polygonUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
    private static final int GL12_RESCALE_NORMAL = 0x803A;

    VintageShadowState(MemoryStack stack) {
        color = stack.callocFloat(16);
        colorMask = stack.calloc(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        for (int i = 0; i < lights.length; i++) lights[i] = GL11.glIsEnabled(GL11.GL_LIGHT0 + i);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        for (int i = 0; i < textures.length; i++) {
            // Shader sampler slots exceed the legacy Minecraft texture-state array.
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            samplers[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if (i < textureMatrices.length) {
                textureEnabled[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
                textureMatrices[i] = stack.callocFloat(16);
                GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, textureMatrices[i]);
                textureDepths[i] = GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH);
            }
        }
        for (int i = 1; i < attributes.length; i++) {
            attributes[i] = stack.callocFloat(4);
            GL20.glGetVertexAttribfv(i, GL20.GL_CURRENT_VERTEX_ATTRIB, attributes[i]);
        }
        GL13.glActiveTexture(activeTexture);
    }

    @Override
    public void close() {
        Program.unbind();
        BlendModeOverride.restore();
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        // glPopAttrib restores the driver, not Minecraft's cached state. Replay cached switches.
        set(alpha, GlStateManager::enableAlpha, GlStateManager::disableAlpha);
        GlStateManager.alphaFunc(alphaFunc, alphaRef);
        set(lighting, GlStateManager::enableLighting, GlStateManager::disableLighting);
        for (int i = 0; i < lights.length; i++) {
            GlStateManager.enableLight(i);
            GlStateManager.disableLight(i);
            if (lights[i]) GlStateManager.enableLight(i);
        }
        set(colorMaterial, GlStateManager::enableColorMaterial, GlStateManager::disableColorMaterial);
        set(polygonOffset, GlStateManager::enablePolygonOffset, GlStateManager::disablePolygonOffset);
        GlStateManager.doPolygonOffset(polygonFactor, polygonUnits);
        GlStateManager.shadeModel(shadeModel);
        GlStateManager.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
        set(fog, GlStateManager::enableFog, GlStateManager::disableFog);
        set(cull, GlStateManager::enableCull, GlStateManager::disableCull);
        set(rescaleNormal, GlStateManager::enableRescaleNormal, GlStateManager::disableRescaleNormal);
        set(normalize, GlStateManager::enableNormalize, GlStateManager::disableNormalize);
        GlStateManager.resetColor();
        GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        for (int i = 0; i < textures.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            if (i < textureMatrices.length) {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
                // Binding zero first prevents a stale texture cache from suppressing the restore.
                GlStateManager.bindTexture(0);
                GlStateManager.bindTexture(textures[i]);
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
            }
            GL33.glBindSampler(i, samplers[i]);
            if (i < textureMatrices.length) {
                set(textureEnabled[i], GlStateManager::enableTexture2D, GlStateManager::disableTexture2D);
                restoreStack(GL11.GL_TEXTURE, GL11.GL_TEXTURE_STACK_DEPTH, textureDepths[i]);
                GL11.glLoadMatrix(textureMatrices[i]);
            }
        }
        for (int i = 1; i < attributes.length; i++) {
            FloatBuffer value = attributes[i];
            GL20.glVertexAttrib4f(i, value.get(0), value.get(1), value.get(2), value.get(3));
        }
        restoreStack(GL11.GL_PROJECTION, GL11.GL_PROJECTION_STACK_DEPTH, projectionDepth);
        restoreStack(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH, modelDepth);
        matrices.restore();
        GL11.glMatrixMode(matrixMode);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL_STATE_MANAGER.glUseProgram(program);
        OpenGlHelper.setClientActiveTexture(clientTexture);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightmapX, lightmapY);
        renderState.restore();
        GlStateManager.setActiveTexture(activeTexture);
    }

    private static void restoreStack(int mode, int query, int depth) {
        GL11.glMatrixMode(mode);
        int current = GL11.glGetInteger(query);
        while (current > depth) { GL11.glPopMatrix(); current--; }
        while (current < depth) { GL11.glPushMatrix(); current++; }
    }

    private static void set(boolean enabled, Runnable enable, Runnable disable) {
        if (enabled) { disable.run(); enable.run(); }
        else { enable.run(); disable.run(); }
    }
}
