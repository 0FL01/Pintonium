package org.taumc.celeritas.mixin.shaders.texture;

import net.irisshaders.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public abstract class MixinPBRTextureMap {
    @Inject(method = "loadTextureAtlas", at = {@At("HEAD"), @At("RETURN")})
    private void celeritas$invalidateMaterialAtlas(CallbackInfo ci) {
        PBRTextureManager.INSTANCE.onDeleteTexture(((TextureMap) (Object) this).getGlTextureId());
    }
}
