package org.taumc.celeritas.mixin.shaders.statelisteners;

import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_Fog {
    @Shadow
    public float fogColorRed, fogColorGreen, fogColorBlue;

    @Unique
    private static Runnable celeritas$fogStartListener;

    @Unique
    private static Runnable celeritas$fogEndListener;

    static {
        StateUpdateNotifiers.fogStartNotifier = listener -> celeritas$fogStartListener = listener;
        StateUpdateNotifiers.fogEndNotifier = listener -> celeritas$fogEndListener = listener;
    }

    @Inject(method = "updateFogColor", at = @At("RETURN"))
    private void celeritas$captureFogColor(float partialTicks, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setFogColor(this.fogColorRed, this.fogColorGreen, this.fogColorBlue);
    }

    @Inject(method = "setupFog", at = @At("HEAD"))
    private void celeritas$resetFogDensity(int startCoords, float partialTicks, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setFogDensity(-1.0F);
    }

    @Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;setFogStart(F)V"))
    private void celeritas$captureFogStart(float start) {
        GlStateManager.setFogStart(start);

        if (celeritas$fogStartListener != null) {
            celeritas$fogStartListener.run();
        }
    }

    @Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;setFogEnd(F)V"))
    private void celeritas$captureFogEnd(float end) {
        GlStateManager.setFogEnd(end);

        if (celeritas$fogEndListener != null) {
            celeritas$fogEndListener.run();
        }
    }

    @Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;setFogDensity(F)V"))
    private void celeritas$captureFogDensity(float density) {
        CapturedRenderingState.INSTANCE.setFogDensity(density);
        GlStateManager.setFogDensity(density);
    }
}
