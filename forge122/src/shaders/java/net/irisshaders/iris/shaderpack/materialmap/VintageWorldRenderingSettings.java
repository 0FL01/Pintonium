package net.irisshaders.iris.shaderpack.materialmap;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.block.state.IBlockState;

public class VintageWorldRenderingSettings {
    public static final VintageWorldRenderingSettings INSTANCE = new VintageWorldRenderingSettings();

    private Object2IntMap<IBlockState> blockStateIds = Object2IntMaps.emptyMap();

    public Object2IntMap<IBlockState> getBlockStateIds() {
        return this.blockStateIds;
    }

    public void setBlockStateIds(Object2IntMap<IBlockState> blockStateIds) {
        this.blockStateIds = blockStateIds == null ? Object2IntMaps.emptyMap() : blockStateIds;
        WorldRenderingSettings.INSTANCE.setReloadRequired();
    }
}
