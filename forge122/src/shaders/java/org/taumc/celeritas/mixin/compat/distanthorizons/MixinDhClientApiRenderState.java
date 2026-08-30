package org.taumc.celeritas.mixin.compat.distanthorizons;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.CommonIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.compat.distanthorizons.DhRenderStateSnapshot;

/**
 * Isolates both DH render entry points from Minecraft's fixed-function state.
 *
 * DH 3.2 restores only part of the state changed by its OpenGL renderer. The normal
 * opaque entry point is outside Pintonium's translucent hook, so it needs its own
 * boundary in addition to the deferred-pass try/finally.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public abstract class MixinDhClientApiRenderState {
    @Unique
    private DhRenderStateSnapshot pintonium$dhRenderState;

    @Inject(method = {"renderLods", "renderDeferredLodsForShaders"}, at = @At("HEAD"), remap = false)
    private void pintonium$captureDhRenderState(CallbackInfo ci) {
        this.pintonium$dhRenderState = DhRenderStateSnapshot.capture();
    }

    @Inject(method = {"renderLods", "renderDeferredLodsForShaders"}, at = @At("RETURN"), remap = false)
    private void pintonium$restoreDhRenderState(CallbackInfo ci) {
        DhRenderStateSnapshot state = this.pintonium$dhRenderState;
        this.pintonium$dhRenderState = null;
        if (state == null) {
            return;
        }

        WorldRenderingPipeline pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof CommonIrisRenderingPipeline) {
            ((CommonIrisRenderingPipeline) pipeline).bindDefault();
        }
        state.restore();
    }
}
