package org.taumc.celeritas.mixin.compat.scannable;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.CommonIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

/**
 * Routes Scannable's OptiFine-specific overlay path through Pintonium.
 *
 * <p>Scannable 1.12 detects shaders solely by reflecting OptiFine's Shaders
 * class. Without this bridge it treats Pintonium as vanilla, replaces the
 * Minecraft framebuffer's depth attachment, and re-renders terrain into its
 * private depth target. That conflicts with Pintonium's gbuffer pipeline and
 * can make the world translucent or the final image black.</p>
 */
@Pseudo
@Mixin(targets = "li.cil.scannable.integration.optifine.ProxyOptiFine", remap = false)
public abstract class MixinProxyOptiFine {
    @Unique
    private static boolean celeritas$logged;

    @Inject(method = "isShaderPackLoaded", at = @At("HEAD"), cancellable = true, remap = false)
    private void celeritas$detectPintoniumShaders(CallbackInfoReturnable<Boolean> cir) {
        CommonIrisRenderingPipeline pipeline = celeritas$getPipeline();
        if (pipeline == null) {
            return;
        }

        if (!celeritas$logged) {
            IRIS_LOGGER.info("Using Pintonium's shader-safe overlay path for Scannable scan results.");
            celeritas$logged = true;
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "getDepthTexture", at = @At("HEAD"), cancellable = true, remap = false)
    private void celeritas$providePintoniumDepthTexture(CallbackInfoReturnable<Integer> cir) {
        CommonIrisRenderingPipeline pipeline = celeritas$getPipeline();
        if (pipeline == null) {
            return;
        }

        int depthTexture = pipeline.getDepthTexture();
        if (depthTexture > 0) {
            cir.setReturnValue(depthTexture);
        }
    }

    @Unique
    private static CommonIrisRenderingPipeline celeritas$getPipeline() {
        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        return pipeline instanceof CommonIrisRenderingPipeline
                ? (CommonIrisRenderingPipeline) pipeline
                : null;
    }
}
