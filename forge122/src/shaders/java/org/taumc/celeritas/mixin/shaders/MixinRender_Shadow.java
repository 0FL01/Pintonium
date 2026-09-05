package org.taumc.celeritas.mixin.shaders;

import net.irisshaders.iris.shadows.CommonShadowRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public class MixinRender_Shadow {
    @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true)
    private void iris$skipShadowLabel(Entity entity, String text, double x, double y, double z, int distance, CallbackInfo ci) {
        if (CommonShadowRenderer.ACTIVE) ci.cancel();
    }
}
