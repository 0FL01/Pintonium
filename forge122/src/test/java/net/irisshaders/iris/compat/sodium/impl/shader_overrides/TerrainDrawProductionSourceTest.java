package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source wiring regression, runnable without bootstrapping Minecraft or a GL context. */
public class TerrainDrawProductionSourceTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]);
        Path overrides = root.resolve("forge122/src/shaders/java/net/irisshaders/iris/compat/sodium/impl/shader_overrides");
        String shader = Files.readString(overrides.resolve("IrisChunkShaderInterface.java"));
        String programs = Files.readString(overrides.resolve("IrisChunkProgramOverrides.java"));
        require(!shader.contains("GpuProfiler") && !shader.contains("TerrainDrawExperiment"), "merge must not depend on capture/debug/timers");
        require(programs.contains("String rejection = pass != IrisTerrainPass.GBUFFER_CUTOUT"), "solid must remain legacy");
        require(programs.contains("createdShaders.size() != 2") && programs.contains("TerrainDrawEligibility.rejectionReason(source)"), "final-source and extra-stage fallback");
        require(shader.contains("coalescingEligible && !isShadowPass")
                && shader.contains("!ShadowRenderingState.areShadowsCurrentlyBeingRendered()")
                && shader.contains("!GL11C.glIsEnabled(GL31C.GL_PRIMITIVE_RESTART)"), "shadow/restart fallback");
        require(shader.contains("!pass.isSorted() && !pass.isReverseOrder()")
                && shader.contains("&& pass.supportsFragmentDiscard()"), "only ordered cutout passes");
        String renderer = Files.readString(root.resolve("common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer.java"));
        require(renderer.contains("primitiveType == GlPrimitiveType.TRIANGLES")
                && renderer.contains("== QuadPrimitiveType.TRIANGULATED"), "geometry fallback");
        require(renderer.contains("coalesce ? coalesceDrawCommands(this.batch, true) : this.batch.getIndexBufferSize()"), "no experimental solid cap or dry preparation");
        String profiler = Files.readString(root.resolve("common-shaders/src/main/java/net/irisshaders/iris/pipeline/GpuProfiler.java"));
        require(!profiler.contains("TerrainDrawExperiment") && !profiler.contains("TerrainDraw2x2"), "no automatic terrain experiment");
        require(profiler.contains("areDebugOptionsEnabled()") && profiler.contains("50_000_000_000L"), "bounded opt-in profiler");
        System.out.println("TerrainDrawProductionSourceTest passed (source wiring, not live GL)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
