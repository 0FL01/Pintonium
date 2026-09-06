package org.taumc.celeritas.mixin.shaders;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.VintageIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Routes vanilla sky geometry through the pack's sky programs. Within
 * {@code renderSky}, Tessellator draws are, in order: the untextured gradient
 * box, the sun quad and the moon quad. Untextured VBO/display-list boxes go
 * through SkyBasic, sun and moon through SkyTextured, and vanilla stars are
 * skipped while the basic bridge is active because the pack draws its own.
 * Anything else, or any pack without sky programs, keeps vanilla behavior.
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobalSky_Shaders {
    @Shadow
    private VertexBuffer skyVBO;
    @Shadow
    private VertexBuffer sky2VBO;
    @Shadow
    private VertexBuffer starVBO;
    @Shadow
    private int glSkyList;
    @Shadow
    private int glSkyList2;
    @Shadow
    private int starGLCallList;

    private static VintageIrisRenderingPipeline celeritas$skyPipeline() {
        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        return pipeline instanceof VintageIrisRenderingPipeline vintage ? vintage : null;
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 0))
    private void iris$skyBasicTessellator(Tessellator tessellator) {
        VintageIrisRenderingPipeline pipeline = celeritas$skyPipeline();
        boolean active = pipeline != null && pipeline.beginVintageSkyBasic();
        try {
            tessellator.draw();
        } finally {
            if (active) {
                pipeline.endVintageSkyBasic();
            }
        }
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 1))
    private void iris$skyTexturedSun(Tessellator tessellator) {
        VintageIrisRenderingPipeline pipeline = celeritas$skyPipeline();
        boolean active = pipeline != null && pipeline.beginVintageSkyTextured();
        try {
            tessellator.draw();
        } finally {
            if (active) {
                pipeline.endVintageSkyTextured();
            }
        }
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 2))
    private void iris$skyTexturedMoon(Tessellator tessellator) {
        VintageIrisRenderingPipeline pipeline = celeritas$skyPipeline();
        boolean active = pipeline != null && pipeline.beginVintageSkyTextured();
        try {
            tessellator.draw();
        } finally {
            if (active) {
                pipeline.endVintageSkyTextured();
            }
        }
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V"))
    private void iris$skyBasicVbo(VertexBuffer buffer, int mode) {
        if (buffer == this.starVBO) {
            VintageIrisRenderingPipeline pipeline = celeritas$skyPipeline();
            if (pipeline != null && pipeline.beginVintageSkyBasic()) {
                // Pack draws its own stars; the vanilla points would double them.
                pipeline.endVintageSkyBasic();
                return;
            }
            buffer.drawArrays(mode);
            return;
        }
        if (buffer != this.skyVBO && buffer != this.sky2VBO) {
            buffer.drawArrays(mode);
            return;
        }
        VintageIrisRenderingPipeline pipeline = celeritas$skyPipeline();
        boolean active = pipeline != null && pipeline.beginVintageSkyBasic();
        try {
            buffer.drawArrays(mode);
        } finally {
            if (active) {
                pipeline.endVintageSkyBasic();
            }
        }
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V"))
    private void iris$skyBasicCallList(int list) {
        if (list == this.starGLCallList) {
            VintageIrisRenderingPipeline pipeline = celeritas$skyPipeline();
            if (pipeline != null && pipeline.beginVintageSkyBasic()) {
                pipeline.endVintageSkyBasic();
                return;
            }
            GlStateManager.callList(list);
            return;
        }
        if (list != this.glSkyList && list != this.glSkyList2) {
            GlStateManager.callList(list);
            return;
        }
        VintageIrisRenderingPipeline pipeline = celeritas$skyPipeline();
        boolean active = pipeline != null && pipeline.beginVintageSkyBasic();
        try {
            GlStateManager.callList(list);
        } finally {
            if (active) {
                pipeline.endVintageSkyBasic();
            }
        }
    }
}
