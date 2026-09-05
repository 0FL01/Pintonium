package org.taumc.celeritas.impl.render.terrain.compile;

import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fluids.IFluidBlock;

public final class OilRendering {
    // Internal vertex marker; the shader bridge exposes ordinary fluid type 1 to packs.
    public static final short RENDER_TYPE = 257;
    public static final float OPACITY = 0.8F;

    private OilRendering() {
    }

    public static boolean isOil(IBlockState state) {
        if (!(state.getBlock() instanceof IFluidBlock)) {
            return false;
        }
        var name = state.getBlock().getRegistryName();
        return name != null && switch (name.toString()) {
            case "buildcraftenergy:fluid_block_oil_heat_0",
                 "buildcraftenergy:fluid_block_oil_heat_1",
                 "buildcraftenergy:fluid_block_oil_heat_2",
                 "thermalfoundation:fluid_crude_oil" -> true;
            default -> false;
        };
    }
}
