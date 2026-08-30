package org.taumc.celeritas.impl.compat.distanthorizons;

import com.mitchej123.glsm.GLStateManagerService;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Captures the OpenGL state that Distant Horizons 3.2 does not restore. */
public final class DhRenderStateSnapshot {
    private final int[] viewport = new int[4];
    private final boolean cullEnabled;
    private final boolean blendEnabled;
    private final boolean depthTestEnabled;
    private final boolean scissorEnabled;
    private final boolean depthMask;
    private final int depthFunc;
    private final BlendMode blendMode;
    private final int activeTexture;

    private DhRenderStateSnapshot() {
        IrisRenderSystem.getIntegerv(GL11.GL_VIEWPORT, this.viewport);
        this.cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        this.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        this.depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        this.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        this.depthMask = GLStateManagerService.GL_STATE_MANAGER.getDepthStateMask();
        this.depthFunc = GLStateManagerService.GL_STATE_MANAGER.glGetInteger(GL11.GL_DEPTH_FUNC);
        this.blendMode = GLStateManagerService.GL_STATE_MANAGER.getBlendMode();
        this.activeTexture = GLStateManagerService.GL_STATE_MANAGER.getActiveTexture();
    }

    public static DhRenderStateSnapshot capture() {
        return new DhRenderStateSnapshot();
    }

    public void restore() {
        GLStateManagerService stateManager = GLStateManagerService.GL_STATE_MANAGER;
        stateManager.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
        setEnabled(GL11.GL_CULL_FACE, this.cullEnabled);
        setEnabled(GL11.GL_SCISSOR_TEST, this.scissorEnabled);

        if (this.blendEnabled) {
            stateManager.enableBlend();
        } else {
            stateManager.disableBlend();
        }
        stateManager.glBlendFuncSeparate(
                this.blendMode.srcRgb(),
                this.blendMode.dstRgb(),
                this.blendMode.srcAlpha(),
                this.blendMode.dstAlpha());

        if (this.depthTestEnabled) {
            stateManager.enableDepthTest();
        } else {
            stateManager.disableDepthTest();
        }
        stateManager.glDepthFunc(this.depthFunc);
        stateManager.glDepthMask(this.depthMask);
        stateManager.glActiveTexture(GL13.GL_TEXTURE0 + this.activeTexture);
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
