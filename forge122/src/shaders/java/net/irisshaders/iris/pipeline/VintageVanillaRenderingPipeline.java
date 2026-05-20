package net.irisshaders.iris.pipeline;

import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.irisshaders.iris.IrisVintage;
import net.irisshaders.iris.shaderpack.materialmap.VintageWorldRenderingSettings;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;

public class VintageVanillaRenderingPipeline extends VanillaRenderingPipeline {
    public VintageVanillaRenderingPipeline() {
        VintageWorldRenderingSettings.INSTANCE.setBlockStateIds(Object2IntMaps.emptyMap());
    }

    @Override
    public void beginLevelRendering() {
        super.beginLevelRendering();
        IrisVintage.resetVanillaGlState();
    }

    @Override
    public void finalizeLevelRendering() {
        super.finalizeLevelRendering();
        IrisVintage.resetVanillaGlState();
    }

    @Override
    public void finalizeGameRendering() {
        super.finalizeGameRendering();
        IrisVintage.resetVanillaGlState();
    }

    @Override
    public ParticleRenderingSettings getParticleRenderingSettings() {
        return ParticleRenderingSettings.MIXED;
    }
}
