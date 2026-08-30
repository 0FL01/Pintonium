package org.taumc.celeritas.mixin.core.terrain;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Honors the shaderpack oldLighting directive on Minecraft 1.12.
 *
 * <p>Vanilla's block model renderer multiplies terrain vertex colors by a
 * fixed value for each face (down 0.5, X 0.6, Z 0.8, up 1.0). Shaderpacks
 * with oldLighting=false perform their own normal-based directional lighting
 * and expect those fixed multipliers to be disabled.</p>
 */
@Mixin(value = LightUtil.class, remap = false)
public abstract class LightUtilMixin {
    @Inject(
            method = "diffuseLight(Lnet/minecraft/util/EnumFacing;)F",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void celeritas$disableDirectionalShading(EnumFacing side,
                                                             CallbackInfoReturnable<Float> cir) {
        if (celeritas$shouldDisableDirectionalShading()) {
            cir.setReturnValue(1.0F);
        }
    }

    /**
     * Use the current pipeline as the authoritative source as well as the
     * worker-visible settings snapshot. This avoids retaining vanilla's
     * X=0.6/Z=0.8 face multipliers if a chunk worker observed the old pipeline
     * while shaders were being enabled.
     */
    private static boolean celeritas$shouldDisableDirectionalShading() {
        if (WorldRenderingSettings.INSTANCE.shouldDisableDirectionalShading()) {
            return true;
        }

        try {
            WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
            return pipeline != null && pipeline.shouldDisableDirectionalShading();
        } catch (Throwable ignored) {
            // LightUtil may be initialized before the shader subsystem during
            // early model setup. In that case vanilla lighting is appropriate.
            return false;
        }
    }
}
