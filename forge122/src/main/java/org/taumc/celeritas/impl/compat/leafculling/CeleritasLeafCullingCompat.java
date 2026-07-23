package org.taumc.celeritas.impl.compat.leafculling;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.Loader;
import org.taumc.celeritas.CeleritasVintage;

import java.lang.reflect.Field;
import java.util.Locale;

public class CeleritasLeafCullingCompat {
    private static final EnumFacing[] FACINGS = EnumFacing.values();
    private static final String MOD_ID = "celeritasleafculling";
    private static final Field CULLING_MODE_FIELD = findCullingModeField();

    public static boolean shouldRenderSurroundedLeavesAsSolid(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (CULLING_MODE_FIELD == null || !isLeafLike(state)) {
            return false;
        }

        Mode mode = getMode();
        return (mode == Mode.SOLID || mode == Mode.SOLID_AGGRESSIVE) && surroundedByLeaves(world, pos, mode == Mode.SOLID_AGGRESSIVE);
    }

    public static boolean isLeafLike(IBlockState state) {
        if (state == null) {
            return false;
        }

        Block block = state.getBlock();
        if (block instanceof BlockLeaves || state.getMaterial() == Material.LEAVES) {
            return true;
        }

        ResourceLocation registryName = Block.REGISTRY.getNameForObject(block);
        if (registryName == null) {
            return false;
        }

        String path = registryName.getPath().toLowerCase(Locale.ROOT);
        return path.contains("leaves") || path.contains("leaf");
    }

    public static BlockRenderLayer getLeafRenderLayer(IBlockState state, boolean renderAsSolid) {
        if (canUseSolidLeafLayer(state) && (renderAsSolid || !CeleritasVintage.options().quality.leavesQuality.isFancy(Minecraft.getMinecraft().gameSettings.fancyGraphics))) {
            return BlockRenderLayer.SOLID;
        }

        BlockRenderLayer alphaLayer = findSupportedAlphaLayer(state);
        if (alphaLayer != null) {
            return alphaLayer;
        }

        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    public static boolean shouldUseSolidRenderLayer(IBlockState state, boolean renderAsSolid) {
        return renderAsSolid && canUseSolidLeafLayer(state);
    }

    private static boolean canUseSolidLeafLayer(IBlockState state) {
        if (state == null || !(state.getBlock() instanceof BlockLeaves)) {
            return false;
        }

        ResourceLocation registryName = Block.REGISTRY.getNameForObject(state.getBlock());
        return registryName != null && "minecraft".equals(registryName.getNamespace());
    }

    private static BlockRenderLayer findSupportedAlphaLayer(IBlockState state) {
        if (state == null) {
            return null;
        }

        BlockRenderLayer[] alphaLayers = new BlockRenderLayer[] {
                BlockRenderLayer.CUTOUT_MIPPED,
                BlockRenderLayer.CUTOUT,
                BlockRenderLayer.TRANSLUCENT
        };

        for (BlockRenderLayer layer : alphaLayers) {
            if (canRenderInLayer(state, layer)) {
                return layer;
            }
        }

        return null;
    }

    private static boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        try {
            return state.getBlock().canRenderInLayer(state, layer);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findCullingModeField() {
        if (!Loader.isModLoaded(MOD_ID)) {
            return null;
        }

        try {
            Field field = Class.forName("toni.sodiumleafculling.config.LeafCullingConfig").getField("cullingMode");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Mode getMode() {
        try {
            Object value = CULLING_MODE_FIELD.get(null);
            if (value != null) {
                return Mode.valueOf(value.toString());
            }
        } catch (IllegalArgumentException | IllegalAccessException ignored) {
            // Fall through to disabled if the addon changes its enum names.
        }

        return Mode.NONE;
    }

    private static boolean surroundedByLeaves(IBlockAccess world, BlockPos pos, boolean aggressive) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (EnumFacing facing : FACINGS) {
            if (aggressive && (facing == EnumFacing.DOWN || facing == EnumFacing.UP)) {
                continue;
            }

            mutablePos.setPos(pos).move(facing);
            IBlockState neighbor = world.getBlockState(mutablePos);
            if (isLeafLike(neighbor)) {
                continue;
            }

            if (!neighbor.isSideSolid(world, mutablePos, facing.getOpposite())) {
                return false;
            }
        }

        return true;
    }

    private enum Mode {
        NONE,
        HOLLOW,
        SOLID,
        SOLID_AGGRESSIVE
    }
}
