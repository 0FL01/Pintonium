package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.pipeline.SodiumTerrainPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;

public class IrisChunkProgramOverrides {
    private static final List<ShaderType> RELEVANT_SHADER_TYPES = List.of(
            ShaderType.VERTEX,
            ShaderType.GEOM,
            ShaderType.TESS_CTRL,
            ShaderType.TESS_EVALUATE,
            ShaderType.FRAGMENT);

    private final EnumMap<IrisTerrainPass, GlProgram<ChunkShaderInterface>> programs = new EnumMap<>(IrisTerrainPass.class);
    private boolean shadersCreated = false;
    private int versionCounterForSodiumShaderReload = -1;

    private GlShader createShader(ShaderType type, IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
        if (pass == IrisTerrainPass.SHADOW_CUTOUT && type != ShaderType.FRAGMENT) {
            pass = IrisTerrainPass.SHADOW;
        }

        var info = pipeline.getPassInfo(pass);
        if (info == null) {
            throw new IllegalArgumentException("Unknown pass type " + pass);
        }

        Optional<String> irisShader = info.sources().get(type);
        String source = irisShader.orElse(null);

        if (source == null) {
            return null;
        }

        return new GlShader(type, "iris:sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + "." + type.fileExtension, source);
    }

    @Nullable
    private GlProgram<ChunkShaderInterface> createShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline, RenderPassConfiguration<?> configuration) {
        Map<ShaderType, GlShader> createdShaders = new EnumMap<>(ShaderType.class);

        for (var type : RELEVANT_SHADER_TYPES) {
            var shader = createShader(type, pass, pipeline);
            if (shader != null) {
                createdShaders.put(type, shader);
            }
        }

        BlendModeOverride blendOverride = pipeline.getPassInfo(pass).blendModeOverride();
        List<BufferBlendOverride> bufferOverrides = pipeline.getPassInfo(pass).bufferOverrides();
        float alpha = pipeline.getPassInfo(pass).alphaTest()
                .orElseThrow(() -> new IllegalStateException("Expected alpha test to be available"))
                .reference();

        if (createdShaders.get(ShaderType.VERTEX) == null || createdShaders.get(ShaderType.FRAGMENT) == null) {
            createdShaders.values().forEach(GlShader::delete);
            return null;
        }

        try {
            GlProgram.Builder builder = GlProgram.builder("iris:sodium_chunk_shader_for_" + pass.getName());
            createdShaders.values().forEach(builder::attachShader);

            int i = 0;
            var vertexType = configuration.getVertexTypeForPass(pass.toTerrainPass(configuration));
            for (var attr : vertexType.getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), i++);
            }

            return builder.<ChunkShaderInterface>link(context -> new IrisChunkShaderInterface(
                    ((org.embeddedt.embeddium.impl.gl.GlObject) context).handle(),
                    context,
                    pipeline,
                    new ChunkShaderOptions(List.of(ChunkFogMode.SMOOTH), pass.toTerrainPass(configuration)),
                    createdShaders.get(ShaderType.TESS_CTRL) != null || createdShaders.get(ShaderType.TESS_EVALUATE) != null,
                    pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT,
                    blendOverride,
                    bufferOverrides,
                    alpha,
                    pipeline.getCustomUniforms()));
        } finally {
            createdShaders.values().forEach(GlShader::delete);
        }
    }

    private void createShaders(SodiumTerrainPipeline pipeline, RenderPassConfiguration<?> configuration) {
        if (pipeline == null) {
            deleteShaders();
            shadersCreated = true;
            return;
        }

        pipeline.patchShaders(configuration);

        for (IrisTerrainPass pass : IrisTerrainPass.values()) {
            if (pass.isShadow() && !pipeline.hasShadowPass()) {
                this.programs.put(pass, null);
                continue;
            }

            try {
                this.programs.put(pass, createShader(pass, pipeline, configuration));
            } catch (RuntimeException e) {
                IRIS_LOGGER.warn("Failed to create Iris terrain shader override for pass {}. This pass will use Pintonium's vanilla shader.", pass, e);
                this.programs.put(pass, null);
            }
        }

        shadersCreated = true;
    }

    @Nullable
    public GlProgram<ChunkShaderInterface> getProgramOverride(TerrainRenderPass pass, RenderPassConfiguration<?> configuration) {
        int pipelineVersion = IrisCommon.getPipelineManager().getVersionCounterForSodiumShaderReload();
        if (versionCounterForSodiumShaderReload != pipelineVersion) {
            versionCounterForSodiumShaderReload = pipelineVersion;
            deleteShaders();
        }

        if (!IrisCommon.getIrisConfig().areShadersEnabled() || IrisCommon.getCurrentPack().isEmpty()) {
            markShadersDisabled();
            return null;
        }

        WorldRenderingPipeline worldRenderingPipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        SodiumTerrainPipeline sodiumTerrainPipeline = worldRenderingPipeline == null ? null : worldRenderingPipeline.getSodiumTerrainPipeline();

        if (!shadersCreated) {
            try {
                createShaders(sodiumTerrainPipeline, configuration);
            } catch (RuntimeException e) {
                IRIS_LOGGER.error("Failed to prepare Iris terrain shader overrides. Terrain will fall back to Pintonium's vanilla shader path.", e);
                deleteShaders();
                shadersCreated = true;
                return null;
            }
        }

        if (sodiumTerrainPipeline == null) {
            return null;
        }

        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            if (!sodiumTerrainPipeline.hasShadowPass()) {
                return null;
            }

            return this.programs.get(pass.supportsFragmentDiscard() ? IrisTerrainPass.SHADOW_CUTOUT : IrisTerrainPass.SHADOW);
        }

        if (pass.isReverseOrder()) {
            return this.programs.get(IrisTerrainPass.GBUFFER_TRANSLUCENT);
        }

        if (pass.supportsFragmentDiscard()) {
            return this.programs.get(IrisTerrainPass.GBUFFER_CUTOUT);
        }

        return this.programs.get(IrisTerrainPass.GBUFFER_SOLID);
    }

    private void markShadersDisabled() {
        if (!this.programs.isEmpty()) {
            deleteShaders();
        }

        shadersCreated = true;
    }

    public void deleteShaders() {
        for (GlProgram<?> program : this.programs.values()) {
            if (program != null) {
                program.delete();
            }
        }

        this.programs.clear();
        shadersCreated = false;
    }
}
