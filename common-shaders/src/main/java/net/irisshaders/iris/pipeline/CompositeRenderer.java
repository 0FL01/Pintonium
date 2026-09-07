package net.irisshaders.iris.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static com.mitchej123.glsm.GLStateManagerService.GL_STATE_MANAGER;
import static com.mitchej123.glsm.RenderSystemService.RENDER_SYSTEM;
import static org.embeddedt.embeddium.compat.mc.MinecraftVersionShimService.MINECRAFT_SHIM;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.framebuffer.ViewportData;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.*;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.foss_transform.TransformPatcherBridge;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.samplers.IrisImages;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.BufferFlipper;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;

public class CompositeRenderer {
	private final RenderTargets renderTargets;

	private final ImmutableList<Pass> passes;
	private final CompositeTextureVersions textureVersions = new CompositeTextureVersions();
	private final Map<ComputeProgram, String> computeNames = new java.util.IdentityHashMap<>();
	private final TextureAccess noiseTexture;
	private final FrameUpdateNotifier updateNotifier;
	private final CenterDepthSampler centerDepthSampler;
	private final Object2ObjectMap<String, TextureAccess> customTextureIds;
	private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
	private final CustomUniforms customUniforms;
	private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
	private final Set<GlImage> customImages;
	private final TextureStage textureStage;
	private final WorldRenderingPipeline pipeline;
	// The full unbind sweep below exists only for shader pack reloading (stale
	// texture units after destroy). Pipelines are rebuilt on reload, so running
	// it once per pipeline lifetime is equivalent and saves ~units*4 GL calls
	// on every chain (begin/prepare/deferred/composite) every frame.
	private boolean didInitialUnbindSweep;

	public CompositeRenderer(WorldRenderingPipeline pipeline, PackDirectives packDirectives, ProgramSource[] sources, ComputeSource[][] computes, RenderTargets renderTargets, ShaderStorageBufferHolder holder,
							 TextureAccess noiseTexture, FrameUpdateNotifier updateNotifier,
							 CenterDepthSampler centerDepthSampler, BufferFlipper bufferFlipper,
							 Supplier<ShadowRenderTargets> shadowTargetsSupplier, TextureStage textureStage,
							 Object2ObjectMap<String, TextureAccess> customTextureIds, Object2ObjectMap<String, TextureAccess> irisCustomTextures, Set<GlImage> customImages, ImmutableMap<Integer, Boolean> explicitPreFlips,
							 CustomUniforms customUniforms) {
		this.pipeline = pipeline;
		this.noiseTexture = noiseTexture;
		this.updateNotifier = updateNotifier;
		this.centerDepthSampler = centerDepthSampler;
		this.renderTargets = renderTargets;
		this.customTextureIds = customTextureIds;
		this.customUniforms = customUniforms;
		this.irisCustomTextures = irisCustomTextures;
		this.customImages = customImages;
		this.textureStage = textureStage;

		final PackRenderTargetDirectives renderTargetDirectives = packDirectives.getRenderTargetDirectives();
		final Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings =
			renderTargetDirectives.getRenderTargetSettings();

		final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
		final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

		explicitPreFlips.forEach((buffer, shouldFlip) -> {
			if (shouldFlip) {
				bufferFlipper.flip(buffer);
				// NB: Flipping deferred_pre or composite_pre does NOT cause the "flippedAtLeastOnce" flag to trigger
			}
		});

		for (int i = 0; i < sources.length; i++) {
			ProgramSource source = sources[i];

			ImmutableSet<Integer> flipped = bufferFlipper.snapshot();
			ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();

			if (source == null || !source.isValid()) {
				if (computes[i] != null) {
					ComputeOnlyPass pass = new ComputeOnlyPass();
					pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier, holder);
					passes.add(pass);
				}
				continue;
			}

			Pass pass = new Pass();
			pass.name = source.getName();
			pass.mipmapName = "mipmap/" + source.getName();
			ProgramDirectives directives = source.getDirectives();

			pass.program = createProgram(source, flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
			pass.blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(null);
			pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier, holder);
			int[] drawBuffers = directives.getDrawBuffers();


			int passWidth = 0, passHeight = 0;
			// Flip the buffers that this shader wrote to, and set pass width and height
			ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

			GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);

			for (int buffer : drawBuffers) {
				RenderTarget target = renderTargets.get(buffer);
				if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
					throw new IllegalStateException("Pass sizes must match for drawbuffers " + Arrays.toString(drawBuffers) + "\nOriginal width: " + passWidth + " New width: " + target.getWidth() + " Original height: " + passHeight + " New height: " + target.getHeight());
				}
				passWidth = target.getWidth();
				passHeight = target.getHeight();

				// compare with boxed Boolean objects to avoid NPEs
				if (explicitFlips.get(buffer) == Boolean.FALSE) {
					continue;
				}

				bufferFlipper.flip(buffer);
				flippedAtLeastOnce.add(buffer);
			}

			explicitFlips.forEach((buffer, shouldFlip) -> {
				if (shouldFlip) {
					bufferFlipper.flip(buffer);
					flippedAtLeastOnce.add(buffer);
				}
			});

			pass.drawBuffers = directives.getDrawBuffers();
			pass.viewWidth = passWidth;
			pass.viewHeight = passHeight;
			pass.stageReadsFromAlt = flipped;
			pass.framebuffer = framebuffer;
			pass.viewportScale = directives.getViewportScale();
			pass.mipmappedBuffers = directives.getMipmappedBuffers();
			pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;

			passes.add(pass);
		}

		this.passes = passes.build();
		for (Pass pass : this.passes) {
			if (!(pass instanceof ComputeOnlyPass)) {
				pass.unknownWrites = hasUnknownWrites(pass.program);
				describeResources(pass);
			}
		}
		this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

		GL_STATE_MANAGER.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);
	}

	private void describeResources(Pass pass) {
		var reads = new ArrayList<CompositeTextureVersions.Texture>();
		var writes = new ArrayList<CompositeTextureVersions.Texture>();
		for (int index : pass.mipmappedBuffers) {
			RenderTarget target = renderTargets.get(index);
			if (target != null) {
				reads.add(CompositeTextureVersions.Texture.read(index, target.getMainTexture(), target.getAltTexture(), pass.stageReadsFromAlt.contains(index)));
			}
		}
		for (int index : pass.drawBuffers) {
			RenderTarget target = renderTargets.get(index);
			writes.add(CompositeTextureVersions.Texture.write(index, target.getMainTexture(), target.getAltTexture(), pass.stageReadsFromAlt.contains(index)));
		}
		pass.resources = new CompositeTextureVersions.PassResources(reads, writes, pass.unknownWrites);
	}

	private static boolean hasUnknownWrites(Program program) {
		// Without full linked-program reflection, do not assume a raster-only shader.
		if (!GL.getCapabilities().OpenGL43) return true;
		int id = program.getProgramId();
		if (GL43C.glGetProgramInterfacei(id, GL43C.GL_SHADER_STORAGE_BLOCK, GL43C.GL_ACTIVE_RESOURCES) != 0) return true;
		int uniforms = GL20C.glGetProgrami(id, GL20C.GL_ACTIVE_UNIFORMS);
		for (int i = 0; i < uniforms; i++) {
			int type = GL31C.glGetActiveUniformsi(id, i, GL31C.GL_UNIFORM_TYPE);
			// The image types form a contiguous GL enum range, including signed/unsigned variants.
			// Reflection cannot prove readonly access, so even readonly images invalidate trust.
			if ((type >= GL42C.GL_IMAGE_1D && type <= GL42C.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY)
				|| type == GL42C.GL_UNSIGNED_INT_ATOMIC_COUNTER) return true;
		}
		return false;
	}

	private void setupMipmapping(CompositeTextureVersions.Texture read, String name) {
		GpuProfiler.count(GpuProfiler.Count.MIP_REQUESTS);
		RenderTarget target = renderTargets.get(read.logicalIndex());
		// Descriptors retain allocation IDs; resolve deferred level-zero ownership
		// before testing/generating mipmaps, not later during sampler binding.
		target.prepareMipmaps();
		int texture = read.physicalId();
		if (textureVersions.needsMipmaps(read)) {
			GpuProfiler.count(GpuProfiler.Count.MIP_GENERATED);
			int timer = GpuProfiler.begin(name);
			IrisRenderSystem.generateMipmaps(texture, GL20C.GL_TEXTURE_2D);
			GpuProfiler.end(timer);
			textureVersions.mipmapsGenerated(read);
		} else {
			GpuProfiler.count(GpuProfiler.Count.MIP_SKIPPED);
		}
		// Preserve explicit requests' filter updates, and stale mipmaps on ordinary reads.

		int filter = GL20C.GL_LINEAR_MIPMAP_LINEAR;
		if (target.getInternalFormat().getPixelFormat().isInteger()) {
			filter = GL20C.GL_NEAREST_MIPMAP_NEAREST;
		}

		IrisRenderSystem.texParameteri(texture, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, filter);
	}

	public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
		return this.flippedAtLeastOnceFinal;
	}

	public void recalculateSizes() {
		for (Pass pass : passes) {
			if (pass instanceof ComputeOnlyPass) {
				continue;
			}
			int passWidth = 0, passHeight = 0;
			for (int buffer : pass.drawBuffers) {
				RenderTarget target = renderTargets.get(buffer);
				if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
					throw new IllegalStateException("Pass widths must match");
				}
				passWidth = target.getWidth();
				passHeight = target.getHeight();
			}
			renderTargets.destroyFramebuffer(pass.framebuffer);
			pass.framebuffer = renderTargets.createColorFramebuffer(pass.stageReadsFromAlt, pass.drawBuffers);
			describeResources(pass);
			pass.viewWidth = passWidth;
			pass.viewHeight = passHeight;
		}
	}

	public void renderAll() {
		textureVersions.beginInvocation();
		RENDER_SYSTEM.disableBlend();

		FullScreenQuadRenderer.INSTANCE.begin();
        final int mcWidth = MINECRAFT_SHIM.getMainFramebufferWidth();
        final int mcHeight = MINECRAFT_SHIM.getMainFramebufferHeight();

		for (Pass renderPass : passes) {
			boolean ranCompute = false;
			for (ComputeProgram computeProgram : renderPass.computes) {
				if (computeProgram != null) {
					ranCompute = true;
					computeProgram.use();
					this.customUniforms.push(computeProgram);
					int timer = GpuProfiler.begin(computeNames.get(computeProgram));
					computeProgram.dispatch(mcWidth, mcHeight);
					GpuProfiler.end(timer);
				}
			}

			if (ranCompute) {
				IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
				textureVersions.unknownWrite();
			}

			Program.unbind();

			if (renderPass instanceof ComputeOnlyPass) {
				continue;
			}

			// Later pipeline stages can lazily create targets absent during this constructor.
			if (renderPass.resources.mipmapReads().size() != renderPass.mipmappedBuffers.size()) {
				describeResources(renderPass);
			}

			if (!renderPass.mipmappedBuffers.isEmpty()) {
				RENDER_SYSTEM.glActiveTexture(GL15C.GL_TEXTURE0);

				for (CompositeTextureVersions.Texture read : renderPass.resources.mipmapReads()) {
					setupMipmapping(read, renderPass.mipmapName);
				}
			}

			float scaledWidth = renderPass.viewWidth * renderPass.viewportScale.scale();
			float scaledHeight = renderPass.viewHeight * renderPass.viewportScale.scale();
			int beginWidth = (int) (renderPass.viewWidth * renderPass.viewportScale.viewportX());
			int beginHeight = (int) (renderPass.viewHeight * renderPass.viewportScale.viewportY());
			RENDER_SYSTEM.glViewport(beginWidth, beginHeight, (int) scaledWidth, (int) scaledHeight);

			renderPass.framebuffer.bind();
			renderPass.program.use();
			if (renderPass.blendModeOverride != null) {
				renderPass.blendModeOverride.apply();
			} else {
				RENDER_SYSTEM.disableBlend();
			}

			// program is the identifier for composite :shrug:
			this.customUniforms.push(renderPass.program);

			int timer = GpuProfiler.begin(renderPass.name);
			FullScreenQuadRenderer.INSTANCE.renderQuad();
			GpuProfiler.end(timer);
			textureVersions.rasterWritten(renderPass.resources);

			BlendModeOverride.restore();
		}

		FullScreenQuadRenderer.INSTANCE.end();

		// Make sure to reset the viewport to how it was before... Otherwise weird issues could occur.
		// Also bind the "main" framebuffer if it isn't already bound.
		MINECRAFT_SHIM.bindMainFramebuffer();
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GL_STATE_MANAGER.glUseProgram(0);

		// NB: Unbinding all of these textures is necessary for proper shaderpack reloading.
		// Pipelines (and these renderers) are recreated on reload, so one sweep suffices.
		if (!didInitialUnbindSweep) {
			didInitialUnbindSweep = true;
			for (int i = 0; i < SamplerLimits.get().getMaxTextureUnits(); i++) {
				// Unbind all textures that we may have used.
				// NB: This is necessary for shader pack reloading to work propely
				if (GL_STATE_MANAGER.getBoundTexture(i) != 0) { // GlStateManagerAccessor.getTEXTURES()[i].binding
					RENDER_SYSTEM.glActiveTexture(GL15C.GL_TEXTURE0 + i);
					RENDER_SYSTEM.bindTexture(0);
				}
			}
		}

		RENDER_SYSTEM.glActiveTexture(GL15C.GL_TEXTURE0);
	}

	// TODO: Don't just copy this from DeferredWorldRenderingPipeline
	private Program createProgram(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
								  Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
		// TODO: Properly handle empty shaders
        Preconditions.checkArgument(source.isValid());
		Map<ShaderType, String> transformed = TransformPatcherBridge.patchComposite(
			source.getName(),
			source.getSourcesMap(), textureStage, pipeline.getTextureMap());
		String vertex = transformed.get(ShaderType.VERTEX);
		String geometry = transformed.get(ShaderType.GEOMETRY);
		String fragment = transformed.get(ShaderType.FRAGMENT);

		ShaderPrinter.printProgram(source.getName()).addSources(transformed).print();

		Objects.requireNonNull(flipped);
		ProgramBuilder builder;

		try {
			builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
				IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (ShaderCompileException e) {
			throw e;
		} catch (RuntimeException e) {
			// TODO: Better error handling
			throw new RuntimeException("Shader compilation failed for " + source.getName() + "!", e);
		}


		CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
		this.customUniforms.assignTo(builder);

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true, pipeline);
		IrisSamplers.addCustomTextures(builder, irisCustomTextures);
		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);

		IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);
		IrisImages.addCustomImages(builder, customImages);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
			IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
		}

		// TODO: Don't duplicate this with FinalPassRenderer
		centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

		Program build = builder.build();

		// tell the customUniforms that those locations belong to this pass
		// this is just an object to index the internal map
		this.customUniforms.mapholderToPass(builder, build);

		return build;
	}

	private ComputeProgram[] createComputes(ComputeSource[] compute, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot, Supplier<ShadowRenderTargets> shadowTargetsSupplier, ShaderStorageBufferHolder holder) {
		ComputeProgram[] programs = new ComputeProgram[compute.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = compute[i];
			if (source == null || !source.getSource().isPresent()) {
				continue;
			} else {
				// TODO: Properly handle empty shaders
				Objects.requireNonNull(flipped);
				ProgramBuilder builder;

				try {
					String transformed = TransformPatcherBridge.patchCompute(source.getName(), source.getSource().orElse(null), textureStage, pipeline.getTextureMap());

					ShaderPrinter.printProgram(source.getName()).addSource(ShaderType.COMPUTE, transformed).print();

					builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (ShaderCompileException e) {
					throw e;
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed for compute " + source.getName() + "!", e);
				}

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

				CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);

				customUniforms.assignTo(builder);

				IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true, pipeline);
				IrisSamplers.addCustomTextures(builder, irisCustomTextures);
				IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);

				IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);
				IrisImages.addCustomImages(builder, customImages);

				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
				IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

				if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
					IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
					IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
				}

				// TODO: Don't duplicate this with FinalPassRenderer
				centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

				programs[i] = builder.buildCompute();
				computeNames.put(programs[i], "compute/" + source.getName());

				customUniforms.mapholderToPass(builder, programs[i]);

				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), FilledIndirectPointer.basedOff(holder, source.getIndirectPointer()));
			}
		}


		return programs;
	}

	public void destroy() {
		for (Pass renderPass : passes) {
			renderPass.destroy();
		}
	}

	private static class Pass {
		String name;
		String mipmapName;
		int[] drawBuffers;
		int viewWidth;
		int viewHeight;
		Program program;
		BlendModeOverride blendModeOverride;
		ComputeProgram[] computes;
		GlFramebuffer framebuffer;
		ImmutableSet<Integer> flippedAtLeastOnce;
		ImmutableSet<Integer> stageReadsFromAlt;
		ImmutableSet<Integer> mipmappedBuffers;
		ViewportData viewportScale;
		boolean unknownWrites;
		CompositeTextureVersions.PassResources resources;

		protected void destroy() {
			this.program.destroy();
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}
	}

	private static class ComputeOnlyPass extends Pass {
		@Override
		protected void destroy() {
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}
	}
}
