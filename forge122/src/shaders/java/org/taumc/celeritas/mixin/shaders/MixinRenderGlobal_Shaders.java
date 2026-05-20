package org.taumc.celeritas.mixin.shaders;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_Shaders {
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
}
