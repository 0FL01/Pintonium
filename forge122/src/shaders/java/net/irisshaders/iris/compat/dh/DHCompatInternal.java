package net.irisshaders.iris.compat.dh;

import static com.mitchej123.glsm.RenderSystemService.RENDER_SYSTEM;
import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.pipeline.CommonIrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.irisshaders.iris.targets.DepthTexture;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import org.taumc.celeritas.CeleritasShaderVersionService;
import org.lwjgl.opengl.GL20C;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class DHCompatInternal {
	public static final DHCompatInternal SHADERLESS = new DHCompatInternal(null, false);
	static boolean dhEnabled;
	private static int guiScale = -1;
	private static MethodHandle deferredLodRenderer;
	private final CommonIrisRenderingPipeline pipeline;
	public boolean shouldOverrideShadow;
	public boolean shouldOverride;
	private GlFramebuffer dhGenericFramebuffer;
	private IrisLodRenderProgram solidProgram;
	private IrisGenericRenderProgram genericShader;
	private IrisLodRenderProgram translucentProgram;
	private IrisLodRenderProgram shadowProgram;
	private GlFramebuffer dhTerrainFramebuffer;
	private DhFrameBufferWrapper dhTerrainFramebufferWrapper;
	private GlFramebuffer dhWaterFramebuffer;
	private GlFramebuffer dhShadowFramebuffer;
	private DhFrameBufferWrapper dhShadowFramebufferWrapper;
	private DepthTexture depthTexNoTranslucent;
	private boolean translucentDepthDirty;
	private int depthTextureWidth = -1;
	private int depthTextureHeight = -1;
	private int storedDepthTex = -1;
	private boolean incompatible = false;

	public DHCompatInternal(CommonIrisRenderingPipeline pipeline, boolean dhShadowEnabled) {
		this.pipeline = pipeline;

		if (pipeline == null || !DhApi.Delayed.configs.graphics().renderingEnabled().getValue()) {
			return;
		}

		if (pipeline.getDHTerrainShader().isEmpty() && pipeline.getDHWaterShader().isEmpty()) {
			IRIS_LOGGER.warn("No DH shader found in this pack.");
			incompatible = true;
			return;
		}

		createDepthTex(
			Minecraft.getMinecraft().getFramebuffer().framebufferWidth,
			Minecraft.getMinecraft().getFramebuffer().framebufferHeight);

		ProgramSource terrain = pipeline.getDHTerrainShader().get();
		solidProgram = IrisLodRenderProgram.createProgram(terrain.getName(), false, false, terrain, pipeline.getCustomUniforms(), pipeline);

		ProgramSource generic = pipeline.getDHGenericShader().orElse(terrain);
		genericShader = IrisGenericRenderProgram.createProgram(generic.getName() + "_g", false, false, generic, pipeline.getCustomUniforms(), pipeline);
		dhGenericFramebuffer = pipeline.createDHFramebuffer(generic, false);

		if (pipeline.getDHWaterShader().isPresent()) {
			ProgramSource water = pipeline.getDHWaterShader().get();
			translucentProgram = IrisLodRenderProgram.createProgram(water.getName(), false, true, water, pipeline.getCustomUniforms(), pipeline);
			dhWaterFramebuffer = pipeline.createDHFramebuffer(water, true);
		}

		shouldOverrideShadow = false;

		dhTerrainFramebuffer = pipeline.createDHFramebuffer(terrain, false);
		dhTerrainFramebufferWrapper = new DhFrameBufferWrapper(dhTerrainFramebuffer);

		if (translucentProgram == null) {
			translucentProgram = solidProgram;
		}

		shouldOverride = true;
		updateDeferredTransparency(true);
	}

	public static int getDhBlockRenderDistance() {
		if (DhApi.Delayed.configs == null) {
			// Called before DH has finished setup
			return 0;
		}

		return DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16;
	}

	public static int getRenderDistance() {
		return getDhBlockRenderDistance();
	}

	public static float getFarPlane() {
		if (DhApi.Delayed.configs == null) {
			// Called before DH has finished setup
			return 0;
		}

		int lodChunkDist = DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue();
		int lodBlockDist = lodChunkDist * 16;
		// sqrt 2 to prevent the corners from being cut off
		return (float) ((lodBlockDist + 512) * Math.sqrt(2));
	}

	public static float getNearPlane() {
		if (DhApi.Delayed.renderProxy == null) {
			// Called before DH has finished setup
			return 0;
		}

		return DhApi.Delayed.renderProxy.getNearClipPlaneDistanceInBlocks(CapturedRenderingState.INSTANCE.getRealTickDelta());
	}

	public static boolean checkFrame() {
		updateDeferredTransparency(LodRendererEvents.getInstance().shouldOverride);

		if (guiScale == -1) {
			guiScale = Minecraft.getMinecraft().gameSettings.guiScale;
		}

		if (DhApi.Delayed.configs == null) return dhEnabled;

		if ((dhEnabled != DhApi.Delayed.configs.graphics().renderingEnabled().getValue() || guiScale != Minecraft.getMinecraft().gameSettings.guiScale)
			&& IrisCommon.getPipelineManager().getPipelineNullable() instanceof CommonIrisRenderingPipeline) {
			guiScale = Minecraft.getMinecraft().gameSettings.guiScale;
			dhEnabled = DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
			CeleritasShaderVersionService.INSTANCE.reload();
		}

		return dhEnabled;
	}

	public static void renderDeferredLods() {
		DHCompatInternal instance = LodRendererEvents.getInstance();
		if (!instance.shouldOverride
			|| DhApi.Delayed.renderProxy == null
			|| !DhApi.Delayed.renderProxy.getDeferTransparentRendering()) {
			return;
		}

		try {
			if (deferredLodRenderer == null) {
				Class<?> clientApiClass = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi");
				Object clientApi = clientApiClass.getField("INSTANCE").get(null);
				deferredLodRenderer = MethodHandles.lookup()
					.unreflect(clientApiClass.getMethod("renderDeferredLodsForShaders"))
					.bindTo(clientApi);
			}

			deferredLodRenderer.invokeExact();
		} catch (Throwable e) {
			throw new RuntimeException("Unable to invoke Distant Horizons' deferred LOD renderer.", e);
		}
	}

	static void updateDeferredTransparency(boolean defer) {
		if (DhApi.Delayed.renderProxy == null || DhApi.Delayed.renderProxy.getDeferTransparentRendering() == defer) {
			return;
		}

		// DH 3.2 decides whether to combine opaque and translucent geometry before
		// firing its before-render event, so this must be set ahead of that event.
		DhApi.Delayed.renderProxy.setDeferTransparentRendering(defer);
		IRIS_LOGGER.info("{} deferred DH transparency for {} rendering.",
			defer ? "Enabled" : "Disabled",
			defer ? "shader-compatible" : "shaderless");
	}

	public boolean incompatiblePack() {
		return incompatible;
	}

	public void reconnectDHTextures(int depthTex) {
		if (pipeline == null || dhTerrainFramebuffer == null) {
			return;
		}

		int width = Minecraft.getMinecraft().getFramebuffer().framebufferWidth;
		int height = Minecraft.getMinecraft().getFramebuffer().framebufferHeight;
		if (depthTexNoTranslucent == null || depthTextureWidth != width || depthTextureHeight != height) {
			createDepthTex(width, height);
		}

		if (storedDepthTex != depthTex) {
			storedDepthTex = depthTex;
			dhTerrainFramebuffer.addDepthAttachmentBypass(depthTex);
			if (dhWaterFramebuffer != null) {
				dhWaterFramebuffer.addDepthAttachmentBypass(depthTex);
			}
			if (dhGenericFramebuffer != null) {
				dhGenericFramebuffer.addDepthAttachmentBypass(depthTex);
			}
		}
	}

	private void createDepthTex(int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}

		if (depthTexNoTranslucent != null) {
			depthTexNoTranslucent.destroy();
		}

		depthTexNoTranslucent = new DepthTexture("DH depth tex", width, height, DepthBufferFormat.DEPTH32F);
		depthTextureWidth = width;
		depthTextureHeight = height;
		translucentDepthDirty = true;
	}

	public void clear() {
		OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, dhTerrainFramebufferWrapper);
		OverrideInjector.INSTANCE.unbind(IDhApiGenericObjectShaderProgram.class, genericShader);
		OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, dhShadowFramebufferWrapper);

		IrisLodRenderProgram oldSolidProgram = solidProgram;
		IrisLodRenderProgram oldTranslucentProgram = translucentProgram;
		IrisLodRenderProgram oldShadowProgram = shadowProgram;
		if (oldSolidProgram != null) {
			oldSolidProgram.free();
		}
		if (oldTranslucentProgram != null && oldTranslucentProgram != oldSolidProgram) {
			oldTranslucentProgram.free();
		}
		if (oldShadowProgram != null && oldShadowProgram != oldSolidProgram && oldShadowProgram != oldTranslucentProgram) {
			oldShadowProgram.free();
		}
		if (genericShader != null) {
			genericShader.free();
		}
		if (depthTexNoTranslucent != null) {
			depthTexNoTranslucent.destroy();
			depthTexNoTranslucent = null;
		}
		solidProgram = null;
		translucentProgram = null;
		shadowProgram = null;
		genericShader = null;
		shouldOverrideShadow = false;
		shouldOverride = false;
		dhTerrainFramebuffer = null;
		dhWaterFramebuffer = null;
		dhShadowFramebuffer = null;
		dhGenericFramebuffer = null;
		storedDepthTex = -1;
		depthTextureWidth = -1;
		depthTextureHeight = -1;
		translucentDepthDirty = true;

		dhTerrainFramebufferWrapper = null;
		dhShadowFramebufferWrapper = null;
		updateDeferredTransparency(false);
		DhApi.Delayed.configs.graphics().ambientOcclusion().enabled().clearValue();
		DhApi.Delayed.configs.graphics().fog().drawMode().clearValue();
		LodRendererEvents.resetPassState();
	}

	public void setModelPos(DhApiVec3f modelPos) {
		solidProgram.bind();
		solidProgram.setModelPos(modelPos);
		translucentProgram.bind();
		translucentProgram.setModelPos(modelPos);
		solidProgram.bind();
	}

	public IrisLodRenderProgram getSolidShader() {
		return solidProgram;
	}

	public GlFramebuffer getSolidFB() {
		return dhTerrainFramebuffer;
	}

	public DhFrameBufferWrapper getSolidFBWrapper() {
		return dhTerrainFramebufferWrapper;
	}

	public IrisLodRenderProgram getShadowShader() {
		return shadowProgram;
	}

	public GlFramebuffer getShadowFB() {
		return dhShadowFramebuffer;
	}

	public DhFrameBufferWrapper getShadowFBWrapper() {
		return dhShadowFramebufferWrapper;
	}

	public IrisLodRenderProgram getTranslucentShader() {
		if (translucentProgram == null) {
			return solidProgram;
		}
		return translucentProgram;
	}

	public int getStoredDepthTex() {
		return storedDepthTex;
	}

	public void copyTranslucents(int width, int height) {
		if (dhTerrainFramebuffer == null || storedDepthTex <= 0) {
			return;
		}

		if (width <= 0 || height <= 0) {
			width = Minecraft.getMinecraft().getFramebuffer().framebufferWidth;
			height = Minecraft.getMinecraft().getFramebuffer().framebufferHeight;
		}

		if (depthTexNoTranslucent == null || depthTextureWidth != width || depthTextureHeight != height) {
			createDepthTex(width, height);
		}
		if (depthTexNoTranslucent == null) {
			return;
		}

		if (translucentDepthDirty) {
			translucentDepthDirty = false;
			RENDER_SYSTEM.bindTexture(depthTexNoTranslucent.getTextureId());
			dhTerrainFramebuffer.bindAsReadBuffer();
			IrisRenderSystem.copyTexImage2D(
				GL20C.GL_TEXTURE_2D,
				0,
				DepthBufferFormat.DEPTH32F.getGlInternalFormat(),
				0,
				0,
				width,
				height,
				0);
		} else {
			DepthCopyStrategy.fastest(false).copy(
				dhTerrainFramebuffer,
				storedDepthTex,
				null,
				depthTexNoTranslucent.getTextureId(),
				width,
				height);
		}
	}

	public GlFramebuffer getTranslucentFB() {
		return dhWaterFramebuffer;
	}

	public GlFramebuffer getGenericFB() {
		return dhGenericFramebuffer;
	}

	public int getDepthTexNoTranslucent() {
		return depthTexNoTranslucent == null ? 0 : depthTexNoTranslucent.getTextureId();
	}

	public IDhApiGenericObjectShaderProgram getGenericShader() {
		return genericShader;
	}

	public boolean avoidRenderingClouds() {
		return pipeline != null && (pipeline.getDHCloudSetting() == CloudSetting.OFF || (pipeline.getDHCloudSetting() == CloudSetting.DEFAULT && pipeline.getCloudSetting() == CloudSetting.OFF));
	}
}
