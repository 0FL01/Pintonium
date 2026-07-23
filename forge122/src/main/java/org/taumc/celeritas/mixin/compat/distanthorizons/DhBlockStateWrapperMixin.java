package org.taumc.celeritas.mixin.compat.distanthorizons;

import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper", remap = false)
public abstract class DhBlockStateWrapperMixin {
    @Inject(
            method = "getLightEmission(Lnet/minecraft/block/state/IBlockState;)I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private static void pintonium$clampLightEmission(IBlockState state, CallbackInfoReturnable<Integer> cir) {
        int light = cir.getReturnValueI();
        cir.setReturnValue(Math.max(0, Math.min(15, light)));
    }
}
