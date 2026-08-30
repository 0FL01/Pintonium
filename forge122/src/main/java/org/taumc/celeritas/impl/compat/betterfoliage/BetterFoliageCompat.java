package org.taumc.celeritas.impl.compat.betterfoliage;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.Loader;
import org.taumc.celeritas.CeleritasVintage;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

/**
 * Bridges Pintonium's custom chunk mesher to Better Foliage's block rendering hooks.
 *
 * <p>Better Foliage normally injects these calls into RenderChunk#rebuildChunk. Pintonium
 * replaces that chunk-building path, so the hooks have to be called explicitly here.</p>
 */
public final class BetterFoliageCompat {
    private static final String MOD_ID = "betterfoliage";
    private static final String RL_FOLIAGE_HANDLER_CLASS = "betterfoliage.render.feature.RenderingHandler";
    private static final String LEGACY_HOOKS_CLASS = "mods.betterfoliage.client.Hooks";

    private static final MethodHandle RENDER_BLOCK;
    private static final MethodHandle CAN_RENDER_BLOCK_IN_LAYER;
    private static final Api API;
    private static final boolean AVAILABLE;

    static {
        MethodHandle renderBlock = null;
        MethodHandle canRenderBlockInLayer = null;
        Api api = Api.NONE;

        if (Loader.isModLoaded(MOD_ID)) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> handler = Class.forName(RL_FOLIAGE_HANDLER_CLASS);

                renderBlock = lookup.findStatic(handler, "wrapRenderBlock", MethodType.methodType(
                        Boolean.class,
                        Supplier.class,
                        IBlockState.class,
                        BlockPos.class,
                        IBlockAccess.class,
                        Supplier.class,
                        BlockRenderLayer.class
                ));
                canRenderBlockInLayer = lookup.findStatic(handler, "canRenderBlockInLayer", MethodType.methodType(
                        boolean.class,
                        Block.class,
                        IBlockState.class,
                        BlockRenderLayer.class
                ));

                api = Api.RL_FOLIAGE;
                CeleritasVintage.logger().info("Enabled RL Foliage chunk-meshing compatibility");
            } catch (ReflectiveOperationException | LinkageError rlFoliageFailure) {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                    Class<?> hooks = Class.forName(LEGACY_HOOKS_CLASS);

                    renderBlock = lookup.findStatic(hooks, "renderWorldBlock", MethodType.methodType(
                            boolean.class,
                            BlockRendererDispatcher.class,
                            IBlockState.class,
                            BlockPos.class,
                            IBlockAccess.class,
                            BufferBuilder.class,
                            BlockRenderLayer.class
                    ));
                    canRenderBlockInLayer = lookup.findStatic(hooks, "canRenderBlockInLayer", MethodType.methodType(
                            boolean.class,
                            Block.class,
                            IBlockState.class,
                            BlockRenderLayer.class
                    ));

                    api = Api.LEGACY;
                    CeleritasVintage.logger().info("Enabled legacy Better Foliage chunk-meshing compatibility");
                } catch (ReflectiveOperationException | LinkageError legacyFailure) {
                    legacyFailure.addSuppressed(rlFoliageFailure);
                    CeleritasVintage.logger().error("Could not initialize Better Foliage compatibility; using vanilla block rendering", legacyFailure);
                }
            }
        }

        RENDER_BLOCK = renderBlock;
        CAN_RENDER_BLOCK_IN_LAYER = canRenderBlockInLayer;
        API = api;
        AVAILABLE = renderBlock != null && canRenderBlockInLayer != null;
    }

    private BetterFoliageCompat() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String getDebugStatus() {
        switch (API) {
            case RL_FOLIAGE:
                return "RL Foliage bridge active";
            case LEGACY:
                return "legacy Better Foliage bridge active";
            default:
                return "bridge unavailable";
        }
    }

    public static boolean canRenderBlockInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        if (!AVAILABLE) {
            return block.canRenderInLayer(state, layer);
        }

        try {
            return (boolean) CAN_RENDER_BLOCK_IN_LAYER.invokeExact(block, state, layer);
        } catch (Throwable throwable) {
            throw propagate(throwable);
        }
    }

    public static boolean renderBlock(BlockRendererDispatcher dispatcher, IBlockState state, BlockPos pos,
                                      IBlockAccess blockAccess, BufferBuilder buffer, BlockRenderLayer layer) {
        if (!AVAILABLE) {
            return dispatcher.renderBlock(state, pos, blockAccess, buffer);
        }

        try {
            if (API == Api.RL_FOLIAGE) {
                Supplier<Boolean> original = () -> dispatcher.renderBlock(state, pos, blockAccess, buffer);
                Supplier<BufferBuilder> bufferSupplier = () -> buffer;
                Boolean result = (Boolean) RENDER_BLOCK.invokeExact(original, state, pos, blockAccess, bufferSupplier, layer);
                return result != null ? result : original.get();
            }

            return (boolean) RENDER_BLOCK.invokeExact(dispatcher, state, pos, blockAccess, buffer, layer);
        } catch (Throwable throwable) {
            throw propagate(throwable);
        }
    }

    private enum Api {
        NONE,
        RL_FOLIAGE,
        LEGACY
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        return new RuntimeException("Better Foliage block rendering failed", throwable);
    }
}
