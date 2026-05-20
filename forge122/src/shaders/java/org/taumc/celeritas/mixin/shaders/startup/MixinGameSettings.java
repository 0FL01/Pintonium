package org.taumc.celeritas.mixin.shaders.startup;

import net.irisshaders.iris.IrisCommon;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameSettings.class)
public class MixinGameSettings {
    @Unique
    private static boolean celeritas$shadersInitialized;

    @Inject(method = "loadOptions", at = @At("HEAD"))
    private void celeritas$initializeShaders(CallbackInfo ci) {
        if (celeritas$shadersInitialized) {
            return;
        }

        celeritas$shadersInitialized = true;
        IrisCommon.onEarlyInitialize();
    }
}
