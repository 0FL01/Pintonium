package org.taumc.celeritas.mixin.compat.scannable;

import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

/**
 * Disables Scannable's cosmetic depth-based scan wave under Pintonium.
 *
 * <p>The wave renderer either replaces Minecraft's live depth attachment or
 * re-renders terrain into a private framebuffer. Its OptiFine path instead
 * copies depth with an unmanaged GL program and framebuffer. All three paths
 * bypass Pintonium's render-target and shader state tracking, which can leave
 * holes in the world or turn the final shader image black.</p>
 *
 * <p>Detected-block highlights are owned by Scannable's ScanManager and are
 * unaffected. With shaders enabled, MixinProxyOptiFine routes those highlights
 * through Scannable's game-overlay path.</p>
 */
@Pseudo
@Mixin(targets = "li.cil.scannable.client.renderer.ScannerRenderer", remap = false)
public abstract class MixinScannerRenderer {
    @Unique
    private static boolean celeritas$logged;

    @Inject(method = "ping", at = @At("HEAD"), cancellable = true, remap = false)
    private void celeritas$disableUnsafeDepthWave(Vec3d position, CallbackInfo ci) {
        if (!celeritas$logged) {
            IRIS_LOGGER.info("Disabled Scannable's depth-based scan wave for Pintonium; scan result highlights remain enabled.");
            celeritas$logged = true;
        }

        ci.cancel();
    }
}
