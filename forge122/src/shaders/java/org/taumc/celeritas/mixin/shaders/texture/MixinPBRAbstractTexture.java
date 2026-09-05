package org.taumc.celeritas.mixin.shaders.texture;

import net.irisshaders.iris.texture.TextureTracker;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.embeddedt.embeddium.impl.resource.VintageAbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public abstract class MixinPBRAbstractTexture {
    @Inject(method = "getGlTextureId", at = @At("RETURN"))
    private void celeritas$trackTexture(CallbackInfoReturnable<Integer> cir) {
        int id = cir.getReturnValue();
        if (TextureTracker.INSTANCE.getTexture(id) == null) {
            TextureTracker.INSTANCE.trackTexture(id, new VintageAbstractTexture((AbstractTexture) (Object) this));
        }
    }
}
