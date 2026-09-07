package org.taumc.celeritas.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.pipeline.GpuProfiler;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.taumc.celeritas.impl.render.GlMatrixSnapshot;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public class MixinTerrainGpuProfile {
    @WrapMethod(method = "render")
    private void iris$profileLayer(ChunkRenderMatrices matrices, CommandList commands, ChunkRenderListIterable lists,
            TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera, Operation<Void> original) {
        if (!GpuProfiler.isCapturing()) {
            original.call(matrices, commands, lists, pass, occlusionCamera, camera);
            return;
        }
        // Actual passes, not vanilla layers: consolidation can put cutout inside SOLID.
        // The RenderGlobal translucent HEAD hook has already run deferred here.
        String name = (GlMatrixSnapshot.isRenderingShadowPass() ? "shadows/terrain/" : "gbuffer/terrain/")
                + pass.name() + "-inclusive";
        String previous = GpuProfiler.setDrawStage(name);
        GpuProfiler.terrainPass();
        int timer = GpuProfiler.begin(name);
        try {
            original.call(matrices, commands, lists, pass, occlusionCamera, camera);
        } finally {
            GpuProfiler.end(timer);
            GpuProfiler.setDrawStage(previous);
        }
    }
}
