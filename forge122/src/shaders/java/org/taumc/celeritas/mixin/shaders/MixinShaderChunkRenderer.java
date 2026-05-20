package org.taumc.celeritas.mixin.shaders;

import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides;
import net.irisshaders.iris.IrisCommon;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Objects;

@Mixin(ShaderChunkRenderer.class)
public abstract class MixinShaderChunkRenderer {
    @Shadow
    @Final
    private Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programs;

    @Shadow
    @Final
    protected RenderPassConfiguration<?> renderPassConfiguration;

    @Unique
    private IrisChunkProgramOverrides iris$chunkProgramOverrides;

    @Unique
    private int iris$lastPipelineVersion = -1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void iris$createChunkProgramOverrides(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, CallbackInfo ci) {
        this.iris$chunkProgramOverrides = new IrisChunkProgramOverrides();
    }

    @Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
    private void iris$useProgramOverride(ChunkShaderOptions options, CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        this.iris$clearCachedVanillaProgramsIfPipelineChanged();

        GlProgram<ChunkShaderInterface> override = this.iris$chunkProgramOverrides.getProgramOverride(options.pass(), this.renderPassConfiguration);

        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Unique
    private void iris$clearCachedVanillaProgramsIfPipelineChanged() {
        int pipelineVersion = IrisCommon.getPipelineManager().getVersionCounterForSodiumShaderReload();
        if (this.iris$lastPipelineVersion == pipelineVersion) {
            return;
        }

        this.iris$lastPipelineVersion = pipelineVersion;
        this.programs.values().stream().filter(Objects::nonNull).forEach(GlProgram::delete);
        this.programs.clear();
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void iris$deleteProgramOverrides(CommandList commandList, CallbackInfo ci) {
        if (this.iris$chunkProgramOverrides != null) {
            this.iris$chunkProgramOverrides.deleteShaders();
        }
    }
}
