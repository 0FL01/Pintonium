package org.taumc.celeritas.mixin.compat.distanthorizons;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.block.TextureAtlasSpriteWrapper", remap = false)
public abstract class DhTextureAtlasSpriteWrapperMixin {
    private static final Object pintonium$frameLoadLock = new Object();

    @Inject(
            method = "getPixelARGB(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;III)I",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void pintonium$readFrameSafely(TextureAtlasSprite sprite, int frame, int x, int y,
                                                  CallbackInfoReturnable<Integer> cir) {
        IndexOutOfBoundsException lastFailure = null;

        for (int attempt = 0; attempt < 4; attempt++) {
            synchronized (pintonium$frameLoadLock) {
                try {
                    int[][] frameData = sprite.getFrameTextureData(frame);
                    if (frameData.length != 0 && frameData[0] != null) {
                        cir.setReturnValue(frameData[0][y * sprite.getIconWidth() + x]);
                        return;
                    }
                } catch (IndexOutOfBoundsException failure) {
                    lastFailure = failure;
                }
            }

            Thread.yield();
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
    }
}
