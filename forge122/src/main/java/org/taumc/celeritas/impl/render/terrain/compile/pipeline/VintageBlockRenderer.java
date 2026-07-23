package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;

import java.util.List;

/**
 * Compatibility target for addons compiled against Celeritas 2.4's renderer split.
 *
 * <p>Pintonium's 1.12 path currently renders through Minecraft's vanilla block
 * dispatcher, so this class is not used by chunk meshing. Keeping the binary
 * target available lets addons that ship optional mixins against this class load
 * while Pintonium provides equivalent compatibility in its current meshing path.</p>
 */
@Deprecated
public class VintageBlockRenderer {
    @SuppressWarnings("unused")
    private IBlockState currentState;

    @SuppressWarnings("unused")
    private CeleritasBlockAccess currentBlockAccess;

    @SuppressWarnings("unused")
    public void renderBlock() {
        this.renderQuadList(null, null, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unused")
    protected void renderQuadList(ChunkModelBuilder defaultBuffer,
                                  ChunkBuildBuffers buffers,
                                  Material material,
                                  BlockPos pos,
                                  EnumFacing cullFace,
                                  LightPipeline lighter,
                                  IBlockColor colorProvider,
                                  Vec3d offset,
                                  List<BakedQuad> quads) {
    }
}
