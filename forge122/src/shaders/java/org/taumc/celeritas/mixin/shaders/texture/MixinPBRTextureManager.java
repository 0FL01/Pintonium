package org.taumc.celeritas.mixin.shaders.texture;

import net.irisshaders.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class MixinPBRTextureManager {
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void celeritas$reloadMaterials(CallbackInfo ci) {
        PBRTextureManager.INSTANCE.clear();
    }
}
