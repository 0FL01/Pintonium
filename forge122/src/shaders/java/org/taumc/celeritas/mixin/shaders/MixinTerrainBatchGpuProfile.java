package org.taumc.celeritas.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.pipeline.GpuProfiler;
import org.embeddedt.embeddium.impl.gl.device.DrawCommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.tessellation.GlIndexType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public class MixinTerrainBatchGpuProfile {
    @WrapOperation(method = "executeDrawBatch", at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/gl/device/DrawCommandList;multiDrawElementsBaseVertex(Lorg/embeddedt/embeddium/impl/gl/device/MultiDrawBatch;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlPrimitiveType;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlIndexType;)V"))
    private static void iris$countBatch(DrawCommandList list, MultiDrawBatch batch, GlPrimitiveType primitive,
            GlIndexType index, Operation<Void> original) {
        original.call(list, batch, primitive, index);
        GpuProfiler.terrainBatch(batch.size());
    }
}
