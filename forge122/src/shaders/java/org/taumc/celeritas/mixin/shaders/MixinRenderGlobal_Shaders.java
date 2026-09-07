package org.taumc.celeritas.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.pipeline.GpuProfiler;
import net.minecraft.client.renderer.culling.ICamera;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.pipeline.CommonIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.VintageIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.compat.distanthorizons.DhRenderStateSnapshot;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_Shaders {
    @WrapMethod(method = "renderEntities")
    private void iris$profileEntities(Entity view, ICamera camera, float ticks, Operation<Void> original) {
        int timer = GpuProfiler.begin(org.taumc.celeritas.impl.render.GlMatrixSnapshot.isRenderingShadowPass()
                ? "shadows/entities-and-blockentities-inclusive"
                : net.minecraftforge.client.MinecraftForgeClient.getRenderPass() == 0
                ? "gbuffer/entities-and-blockentities/pass0-inclusive"
                : "gbuffer/entities-and-blockentities/pass1-or-other-inclusive");
        try {
            original.call(view, camera, ticks);
        } finally {
            GpuProfiler.end(timer);
        }
    }

    @Unique
    private boolean celeritas$selectionOutlineShaderBridgeActive;

    @Inject(method = "renderBlockLayer", at = @At("HEAD"))
    private void iris$beginTranslucentStage(BlockRenderLayer blockLayerIn, double partialTicks, int pass, Entity entityIn,
            CallbackInfoReturnable<Integer> cir) {
        if (blockLayerIn != BlockRenderLayer.TRANSLUCENT
                || org.taumc.celeritas.impl.render.GlMatrixSnapshot.isRenderingShadowPass()) {
            return;
        }

        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.beginTranslucents();
            if (pipeline instanceof CommonIrisRenderingPipeline) {
                CommonIrisRenderingPipeline commonPipeline = (CommonIrisRenderingPipeline) pipeline;
                DhRenderStateSnapshot state = DhRenderStateSnapshot.capture();
                try {
                    DHCompat.renderDeferredLods();
                } finally {
                    // DH 3.2 only restores a subset of the state it changes. In particular,
                    // its cleanup hard-resets blending and leaves viewport/cull/scissor/depth
                    // state behind. Isolate the external render pass so a shader reload or a
                    // cancelled DH pass cannot contaminate the translucent terrain pass.
                    commonPipeline.bindDefault();
                    state.restore();
                }
            }
        }
    }

    @Inject(method = "drawSelectionBox", at = @At("HEAD"))
    private void celeritas$beginSelectionOutlineShaderBridge(EntityPlayer player, RayTraceResult movingObjectPositionIn,
            int execute, float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        this.celeritas$selectionOutlineShaderBridgeActive = pipeline instanceof VintageIrisRenderingPipeline
                && ((VintageIrisRenderingPipeline) pipeline).beginVintageLineRendering();
    }

    @Inject(method = "drawSelectionBox", at = @At("RETURN"))
    private void celeritas$endSelectionOutlineShaderBridge(EntityPlayer player, RayTraceResult movingObjectPositionIn,
            int execute, float partialTicks, CallbackInfo ci) {
        if (!this.celeritas$selectionOutlineShaderBridgeActive) {
            return;
        }

        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof VintageIrisRenderingPipeline) {
            ((VintageIrisRenderingPipeline) pipeline).endVintageLineRendering();
        }

        this.celeritas$selectionOutlineShaderBridgeActive = false;
    }
}
