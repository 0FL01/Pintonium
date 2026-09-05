package org.taumc.celeritas.mixin.shaders.texture;

import net.irisshaders.iris.texture.TextureTracker;
import net.irisshaders.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinPBRShutdown {
    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void celeritas$closeMaterials(CallbackInfo ci) {
        PBRTextureManager.INSTANCE.close();
        TextureTracker.INSTANCE.clear();
    }
}
