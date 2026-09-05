package org.taumc.celeritas.mixin.shaders.accessor;

import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderManager.class)
public interface MixinRenderManagerShadowAccessor {
    @Accessor("renderPosX") double iris$getRenderPosX();
    @Accessor("renderPosY") double iris$getRenderPosY();
    @Accessor("renderPosZ") double iris$getRenderPosZ();
}
