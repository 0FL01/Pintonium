package org.taumc.celeritas.mixin.shaders;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.VintageIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class MixinParticleManager_Shaders {
    @Unique
    private boolean celeritas$particlesActive;

    @Unique
    private boolean celeritas$litParticlesActive;

    @Unique
    private VintageIrisRenderingPipeline celeritas$getVintagePipeline() {
        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof VintageIrisRenderingPipeline) {
            return (VintageIrisRenderingPipeline) pipeline;
        }

        return null;
    }

    @Unique
    private boolean celeritas$beginParticles() {
        VintageIrisRenderingPipeline pipeline = this.celeritas$getVintagePipeline();
        return pipeline != null && pipeline.beginVintageParticleRendering();
    }

    @Unique
    private void celeritas$endParticles(boolean active) {
        if (!active) {
            return;
        }

        VintageIrisRenderingPipeline pipeline = this.celeritas$getVintagePipeline();
        if (pipeline != null) {
            pipeline.endVintageParticleRendering();
        }
    }

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void celeritas$beginParticleShaderBridge(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.celeritas$particlesActive = this.celeritas$beginParticles();
    }

    @Inject(method = "renderParticles", at = @At("RETURN"))
    private void celeritas$endParticleShaderBridge(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.celeritas$endParticles(this.celeritas$particlesActive);
        this.celeritas$particlesActive = false;
    }

    @Inject(method = "renderLitParticles", at = @At("HEAD"))
    private void celeritas$beginLitParticleShaderBridge(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.celeritas$litParticlesActive = this.celeritas$beginParticles();
    }

    @Inject(method = "renderLitParticles", at = @At("RETURN"))
    private void celeritas$endLitParticleShaderBridge(Entity entityIn, float partialTicks, CallbackInfo ci) {
        this.celeritas$endParticles(this.celeritas$litParticlesActive);
        this.celeritas$litParticlesActive = false;
    }
}
