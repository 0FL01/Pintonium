package org.taumc.celeritas.mixin.shaders;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.irisshaders.iris.IrisConstants;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.IrisVintage;
import net.irisshaders.iris.pipeline.CommonIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.VintageIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.IrisTimeUniforms;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.GlMatrixSnapshot;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_Shaders {
    @Shadow
    public Minecraft mc;

    @Unique
    private WorldRenderingPipeline iris$pipeline;

    @Unique
    private boolean iris$handBridgeActive;

    @Unique
    private boolean iris$weatherBridgeActive;

    @Unique
    private GlMatrixSnapshot iris$gbufferMatrices;

    @Unique
    private void iris$captureGbufferMatrices() {
        GlMatrixSnapshot matrices = GlMatrixSnapshot.captureMainCamera();
        this.iris$gbufferMatrices = matrices;
        CapturedRenderingState.INSTANCE.setGbufferProjection(matrices.projection());
        CapturedRenderingState.INSTANCE.setGbufferModelView(matrices.modelView());
    }

    @Unique
    private void iris$bindDefaultFramebuffer() {
        if (this.iris$pipeline instanceof CommonIrisRenderingPipeline pipeline) {
            pipeline.bindDefault();
        }
    }

    @Unique
    private boolean iris$isVanillaPipeline() {
        return this.iris$pipeline == null || this.iris$pipeline instanceof VanillaRenderingPipeline;
    }

    @Unique
    private boolean iris$areShadersActive() {
        return IrisCommon.getIrisConfig() != null
                && IrisCommon.getIrisConfig().areShadersEnabled()
                && IrisCommon.getCurrentPack().isPresent();
    }

    @Unique
    private VintageIrisRenderingPipeline iris$getVintagePipeline() {
        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof VintageIrisRenderingPipeline) {
            return (VintageIrisRenderingPipeline) pipeline;
        }

        return null;
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void iris$setupPipeline(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        this.iris$pipeline = null;
        this.iris$gbufferMatrices = null;
        GlMatrixSnapshot.clearMainCamera();
        DHCompat.checkFrame();

        if (pass != 2 || this.mc.world == null || this.mc.getRenderViewEntity() == null) {
            return;
        }

        if (!this.iris$areShadersActive()) {
            return;
        }

        IrisTimeUniforms.updateTime();
        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);

        Entity camera = this.mc.getRenderViewEntity();
        CapturedRenderingState.INSTANCE.setCloudTime((camera.world.getWorldTime() + partialTicks) * 0.03F);

        NamespacedId dimensionId = IrisVintage.getCurrentDimension();
        IrisCommon.lastDimension = dimensionId;
        this.iris$pipeline = IrisCommon.getPipelineManager().preparePipeline(dimensionId);
        this.iris$pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;clear(I)V", ordinal = 0))
    private void iris$bindMainFramebufferBeforeVanillaClear(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass == 2 && this.iris$isVanillaPipeline()) {
            IrisVintage.resetVanillaGlState();
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;clear(I)V", shift = At.Shift.AFTER, ordinal = 0))
    private void iris$beginLevelRendering(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass == 2 && this.iris$pipeline != null) {
            SystemTimeUniforms.COUNTER.beginFrame();
            SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());
            this.iris$pipeline.beginLevelRendering();
            if (this.iris$isVanillaPipeline()) {
                IrisVintage.resetVanillaGlState();
            }
            this.iris$bindDefaultFramebuffer();
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V", shift = At.Shift.AFTER))
    private void iris$captureCurrentFrameMatrices(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass == 2 && this.iris$pipeline != null) {
            this.iris$captureGbufferMatrices();
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V", shift = At.Shift.AFTER))
    private void iris$runPreparePass(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass == 2 && this.iris$pipeline != null) {
            try {
                this.iris$pipeline.renderShadows(null, null);
            } finally {
                this.iris$bindDefaultFramebuffer();
                if (this.iris$gbufferMatrices != null) {
                    // DH 3.2 captures the legacy GL matrices at SOLID terrain HEAD for
                    // culling and section placement. The shadow renderer leaves its sun
                    // camera on those stacks, so restore the main camera before DH's mixin
                    // and Pintonium's terrain renderer can observe it.
                    this.iris$gbufferMatrices.restore();
                }
            }
        }
    }

    @WrapWithCondition(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;clear(I)V", ordinal = 1))
    private boolean iris$preserveWorldDepthBeforeHand(int mask) {
        // The second clear is vanilla's hand-only depth reset. Shader composites
        // still need scene depth; the first full-frame clear remains untouched.
        return this.iris$getVintagePipeline() == null;
    }

    @Inject(method = "renderHand(FI)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false))
    private void iris$compressHandDepth(float partialTicks, int pass, CallbackInfo ci) {
        if (this.iris$getVintagePipeline() != null) {
            // Pre-multiply projection, not modelview: this must match MC_HAND_DEPTH.
            GlStateManager.scale(1.0F, 1.0F, IrisConstants.DEPTH);
        }
    }

    @Inject(method = "renderRainSnow(F)V", at = @At("HEAD"))
    private void iris$beginWeather(float partialTicks, CallbackInfo ci) {
        VintageIrisRenderingPipeline pipeline = this.iris$getVintagePipeline();
        this.iris$weatherBridgeActive = pipeline != null && pipeline.beginVintageWeatherRendering();
    }

    @Inject(method = "renderRainSnow(F)V", at = @At("RETURN"))
    private void iris$endWeather(float partialTicks, CallbackInfo ci) {
        if (this.iris$weatherBridgeActive) {
            VintageIrisRenderingPipeline pipeline = this.iris$getVintagePipeline();
            if (pipeline != null) {
                pipeline.endVintageWeatherRendering();
            }
            this.iris$weatherBridgeActive = false;
        }
    }

    @Inject(method = "renderHand(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V"))
    private void iris$beginHandShaderBridge(float partialTicks, int pass, CallbackInfo ci) {
        VintageIrisRenderingPipeline pipeline = this.iris$getVintagePipeline();
        this.iris$handBridgeActive = pipeline != null && pipeline.beginVintageHandRendering();
    }

    @Inject(method = "renderHand(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V", shift = At.Shift.AFTER))
    private void iris$endHandShaderBridge(float partialTicks, int pass, CallbackInfo ci) {
        if (!this.iris$handBridgeActive) {
            return;
        }

        VintageIrisRenderingPipeline pipeline = this.iris$getVintagePipeline();
        if (pipeline != null) {
            pipeline.endVintageHandRendering();
        }

        this.iris$handBridgeActive = false;
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("TAIL"))
    private void iris$finalizeLevelRendering(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass == 2 && this.iris$pipeline != null) {
            this.iris$pipeline.finalizeLevelRendering();
            this.iris$pipeline = null;
            this.iris$gbufferMatrices = null;
        }
    }
}
