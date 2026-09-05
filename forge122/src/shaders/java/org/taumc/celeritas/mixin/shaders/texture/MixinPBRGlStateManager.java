package org.taumc.celeritas.mixin.shaders.texture;

import net.irisshaders.iris.texture.TextureTracker;
import net.irisshaders.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class MixinPBRGlStateManager {
    @Inject(method = "bindTexture", at = @At("RETURN"))
    private static void celeritas$bindMaterial(int texture, CallbackInfo ci) {
        // Includes vanilla entity skins, armor and hands, even if Minecraft skipped a cached bind.
        if (PBRTextureManager.INSTANCE.isInitialized() && GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE0) {
            TextureTracker.INSTANCE.onSetShaderTexture(0, texture);
        }
    }

    @Inject(method = "deleteTexture", at = @At("HEAD"))
    private static void celeritas$deleteMaterial(int texture, CallbackInfo ci) {
        TextureTracker.INSTANCE.onDeleteTexture(texture);
        PBRTextureManager.INSTANCE.onDeleteTexture(texture);
    }
}
