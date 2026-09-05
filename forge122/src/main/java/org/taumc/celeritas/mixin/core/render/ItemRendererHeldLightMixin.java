package org.taumc.celeritas.mixin.core.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import org.embeddedt.embeddium.impl.render.ShaderModBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

@Mixin(ItemRenderer.class)
public class ItemRendererHeldLightMixin {
    @ModifyExpressionValue(method = "setLightmap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"))
    private int celeritas$heldLight(int original) {
        var renderer = CeleritasWorldRenderer.instanceNullable();
        var player = Minecraft.getMinecraft().player;
        if (renderer == null || player == null || ShaderModBridge.areShadersEnabled()) {
            return original;
        }
        var light = renderer.getHeldItemLight(player.world);
        // The source and first-person view belong to this player; use the same
        // interpolated eye position rather than introducing a tick-position offset.
        return light.apply(light.x(), light.y(), light.z(), original);
    }
}
