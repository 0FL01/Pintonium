package org.taumc.celeritas.mixin.shaders.accessor;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderer.class)
public interface MixinEntityRendererLightmapAccessor {
    @Accessor("lightmapTexture")
    DynamicTexture celeritas$getLightmapTexture();
}
