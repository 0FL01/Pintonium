package org.taumc.celeritas.mixin.core.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.embeddedt.embeddium.impl.render.ShaderModBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

@Mixin(RenderManager.class)
public class RenderManagerHeldLightMixin {
    @ModifyExpressionValue(method = {"renderEntityStatic", "renderMultipass"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBrightnessForRender()I"))
    private int celeritas$heldLight(int original, Entity entity, float partialTicks) {
        var renderer = CeleritasWorldRenderer.instanceNullable();
        if (renderer == null || ShaderModBridge.areShadersEnabled()) {
            return original;
        }
        return renderer.getHeldItemLight(entity.world).apply(
                entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + entity.getEyeHeight(),
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks, original);
    }
}
