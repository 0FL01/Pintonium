package org.taumc.celeritas.mixin.shaders;

import net.irisshaders.iris.IrisCommon;
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

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_Shaders {
    @Unique
    private boolean celeritas$selectionOutlineShaderBridgeActive;

    @Inject(method = "renderBlockLayer", at = @At("HEAD"))
    private void iris$beginTranslucentStage(BlockRenderLayer blockLayerIn, double partialTicks, int pass, Entity entityIn,
            CallbackInfoReturnable<Integer> cir) {
        if (blockLayerIn != BlockRenderLayer.TRANSLUCENT) {
            return;
        }

        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.beginTranslucents();
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
