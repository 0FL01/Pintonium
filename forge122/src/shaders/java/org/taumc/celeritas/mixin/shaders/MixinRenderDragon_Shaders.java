package org.taumc.celeritas.mixin.shaders;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.VintageIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.entity.RenderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderDragon.class)
public class MixinRenderDragon_Shaders {
    @Unique
    private static boolean celeritas$crystalBeamBridgeActive;

    @Unique
    private static VintageIrisRenderingPipeline celeritas$getVintagePipeline() {
        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof VintageIrisRenderingPipeline) {
            return (VintageIrisRenderingPipeline) pipeline;
        }

        return null;
    }

    @Inject(method = "renderCrystalBeams", at = @At("HEAD"))
    private static void celeritas$beginCrystalBeamShaderBridge(double x, double y, double z, float partialTicks,
            double crystalX, double crystalY, double crystalZ, int ticksExisted, double cameraX, double cameraY, double cameraZ,
            CallbackInfo ci) {
        VintageIrisRenderingPipeline pipeline = celeritas$getVintagePipeline();
        celeritas$crystalBeamBridgeActive = pipeline != null && pipeline.beginVintageBeaconBeamRendering();
    }

    @Inject(method = "renderCrystalBeams", at = @At("RETURN"))
    private static void celeritas$endCrystalBeamShaderBridge(double x, double y, double z, float partialTicks,
            double crystalX, double crystalY, double crystalZ, int ticksExisted, double cameraX, double cameraY, double cameraZ,
            CallbackInfo ci) {
        if (!celeritas$crystalBeamBridgeActive) {
            return;
        }

        VintageIrisRenderingPipeline pipeline = celeritas$getVintagePipeline();
        if (pipeline != null) {
            pipeline.endVintageBeaconBeamRendering();
        }
        celeritas$crystalBeamBridgeActive = false;
    }
}
