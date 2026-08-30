package net.irisshaders.iris.compat.dh;

import static com.mitchej123.glsm.GLStateManagerService.GL_STATE_MANAGER;
import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericObjectRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderPassEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeTextureClearEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiColorDepthTextureCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL43C;

public class LodRendererEvents {
	private static boolean eventHandlersBound = false;

	private static boolean atTranslucent = false;
	private static boolean warnedAboutCombinedPass = false;
	private static int textureWidth;
	private static int textureHeight;


	// constructor //

	public static void setupEventHandlers() {
		if (!eventHandlersBound) {
			eventHandlersBound = true;
			IRIS_LOGGER.info("Queuing DH event binding...");

			DhApiAfterDhInitEvent beforeCleanupEvent = new DhApiAfterDhInitEvent() {
				@Override
				public void afterDistantHorizonsInit(DhApiEventParam<Void> event) {
					IRIS_LOGGER.info("DH Ready, binding Iris event handlers...");

					DHCompatInternal.updateDeferredTransparency(getInstance().shouldOverride);
					IrisCommon.loadShaderpackWhenPossible();

					setupSetDeferredBeforeRenderingEvent();
					setupReconnectDepthTextureEvent();
					setupGenericEvent();
					setupCreateDepthTextureEvent();
					setupTransparentRendererEventCancling();
					setupBeforeBufferClearEvent();
					setupBeforeRenderCleanupEvent();
					beforeBufferRenderEvent();
					setupBeforeRenderFrameBufferBinding();
					setupBeforeRenderPassEvent();
					setupBeforeApplyShaderEvent();
					DHCompatInternal.dhEnabled = DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
					IRIS_LOGGER.info("DH Iris events bound.");
				}
			};
			DhApi.events.bind(DhApiAfterDhInitEvent.class, beforeCleanupEvent);
		}
	}


	// setup event handlers //


	private static void setupSetDeferredBeforeRenderingEvent() {
		DhApiBeforeRenderEvent beforeRenderEvent = new DhApiBeforeRenderEvent() {
			// this event is called before DH starts any rendering prep
			// canceling it will prevent DH from rendering for that frame
			@Override
			public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
				DHCompatInternal instance = getInstance();
				resetPassState();
				DHCompat.resetProjection();
				DHCompatInternal.updateDeferredTransparency(instance.shouldOverride);
				DhApi.Delayed.configs.graphics().fog().drawMode().setValue(instance.shouldOverride ? EDhApiFogDrawMode.FOG_DISABLED : EDhApiFogDrawMode.FOG_ENABLED);
			}
		};

		DhApi.events.bind(DhApiBeforeRenderEvent.class, beforeRenderEvent);
	}

	private static void setupReconnectDepthTextureEvent() {
		DhApiBeforeTextureClearEvent beforeRenderEvent = new DhApiBeforeTextureClearEvent() {
			@Override
			public void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event) {
				var getResult = DhApi.Delayed.renderProxy.getDhDepthTextureId();
				if (getResult.success) {
					int depthTextureId = getResult.payload;
					getInstance().reconnectDHTextures(depthTextureId);
				}
			}
		};

		DhApi.events.bind(DhApiBeforeTextureClearEvent.class, beforeRenderEvent);
	}

	private static void setupGenericEvent() {
		DhApiBeforeGenericRenderSetupEvent beforeRenderEvent = new DhApiBeforeGenericRenderSetupEvent() {
			@Override
			public void beforeSetup(DhApiEventParam<DhApiRenderParam> dhApiEventParam) {
				DHCompatInternal instance = getInstance();
				if (instance.shouldOverride && instance.getGenericFB() != null) {
					instance.getGenericFB().bind();
				}
			}
		};

		DhApiBeforeGenericObjectRenderEvent beforeDrawEvent = new DhApiBeforeGenericObjectRenderEvent() {
			@Override
			public void beforeRender(DhApiCancelableEventParam<EventParam> dhApiCancelableEventParam) {
				if (dhApiCancelableEventParam.value.resourceLocationPath.equalsIgnoreCase("Clouds")) {
					if (getInstance().avoidRenderingClouds()) {
						dhApiCancelableEventParam.cancelEvent();
					}
				}
			}
		};

		DhApi.events.bind(DhApiBeforeGenericRenderSetupEvent.class, beforeRenderEvent);
		DhApi.events.bind(DhApiBeforeGenericObjectRenderEvent.class, beforeDrawEvent);
	}

	static DHCompatInternal getInstance() {
		return (DHCompatInternal) IrisCommon.getPipelineManager().getPipeline().map(WorldRenderingPipeline::getDHCompat).map(DHCompat::getInstance).orElse(DHCompatInternal.SHADERLESS);
	}

	private static boolean isRenderingShadows() {
		return false;
	}

	static void resetPassState() {
		atTranslucent = false;
	}

	private static boolean rendersOpaque(EDhApiRenderPass renderPass) {
		return renderPass == EDhApiRenderPass.OPAQUE || renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT;
	}

	private static Matrix4f createLodProjection(DhApiRenderParam renderParam) {
		Matrix4f gbufferProjection = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
		float nearPlane = renderParam.nearClipPlane;
		float farPlane = renderParam.farClipPlane;
		if (!Float.isFinite(nearPlane) || !Float.isFinite(farPlane) || nearPlane <= 0.0f || farPlane <= nearPlane) {
			return gbufferProjection;
		}

		// DH vertices are camera-relative, so use the same FOV and aspect ratio as the
		// g-buffer while extending only its clip range. Reconstructing a clean perspective
		// matrix avoids carrying fixed-function offsets into DH's temporal depth history.
		return new Matrix4f().setPerspective(
			gbufferProjection.perspectiveFov(),
			gbufferProjection.m11() / gbufferProjection.m00(),
			nearPlane,
			farPlane);
	}

	private static void setupCreateDepthTextureEvent() {
		DhApiColorDepthTextureCreatedEvent beforeRenderEvent = new DhApiColorDepthTextureCreatedEvent() {
			@Override
			public void onResize(DhApiEventParam<EventParam> input) {
				textureWidth = input.value.newWidth;
				textureHeight = input.value.newHeight;
			}
		};

		DhApi.events.bind(DhApiColorDepthTextureCreatedEvent.class, beforeRenderEvent);
	}

	private static void setupTransparentRendererEventCancling() {
		DhApiBeforeRenderEvent beforeRenderEvent = new DhApiBeforeRenderEvent() {
			@Override
			public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
				if (isRenderingShadows() && (!getInstance().shouldOverrideShadow)) {
					event.cancelEvent();
				}
			}
		};
		DhApiBeforeDeferredRenderEvent beforeRenderEvent2 = new DhApiBeforeDeferredRenderEvent() {
			@Override
			public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
				if (isRenderingShadows() && (!getInstance().shouldOverrideShadow)) {
					event.cancelEvent();
				}
			}
		};

		DhApi.events.bind(DhApiBeforeRenderEvent.class, beforeRenderEvent);
		DhApi.events.bind(DhApiBeforeDeferredRenderEvent.class, beforeRenderEvent2);
	}

	private static void setupBeforeRenderCleanupEvent() {
		DhApiBeforeRenderCleanupEvent beforeCleanupEvent = new DhApiBeforeRenderCleanupEvent() {
			@Override
			public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event) {
				if (getInstance().shouldOverride) {
					getInstance().getSolidShader().unbind();
				}
			}
		};

		DhApi.events.bind(DhApiBeforeRenderCleanupEvent.class, beforeCleanupEvent);
	}

	private static void setupBeforeBufferClearEvent() {
		DhApiBeforeTextureClearEvent beforeCleanupEvent = new DhApiBeforeTextureClearEvent() {
			@Override
			public void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event) {
				if (rendersOpaque(event.value.renderPass)) {
					if (isRenderingShadows()) {
						event.cancelEvent();
					} else if (getInstance().shouldOverride) {
						// This handler cancels DH's own clear, including the glClearDepth(1.0)
						// immediately preceding it. Never inherit a clear value left by another
						// shader pass: sky pixels in dhDepthTex must be exactly 1.0 so packs do
						// not mistake the entire background for DH geometry.
						GL43C.glClearDepth(1.0);
						GL43C.glClear(GL43C.GL_DEPTH_BUFFER_BIT);
						event.cancelEvent();
					}
				}
			}
		};

		DhApi.events.bind(DhApiBeforeTextureClearEvent.class, beforeCleanupEvent);
	}

	private static void beforeBufferRenderEvent() {
		DhApiBeforeBufferRenderEvent beforeCleanupEvent = new DhApiBeforeBufferRenderEvent() {
			@Override
			public void beforeRender(DhApiEventParam<EventParam> input) {
				DHCompatInternal instance = getInstance();
				if (instance.shouldOverride) {
					DhApiVec3f modelPos = input.value.modelPos;
					if (atTranslucent) {
						instance.getTranslucentShader().bind();
						instance.getTranslucentShader().setModelPos(modelPos);
					} else {
						instance.getSolidShader().bind();
						instance.getSolidShader().setModelPos(modelPos);
					}
				}
			}
		};

		DhApi.events.bind(DhApiBeforeBufferRenderEvent.class, beforeCleanupEvent);
	}

	private static void setupBeforeRenderFrameBufferBinding() {
		DhApiBeforeRenderSetupEvent beforeRenderPassEvent = new DhApiBeforeRenderSetupEvent() {
			@Override
			public void beforeSetup(DhApiEventParam<DhApiRenderParam> event) {
				DHCompatInternal instance = getInstance();

				OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());
				OverrideInjector.INSTANCE.unbind(IDhApiGenericObjectShaderProgram.class, instance.getGenericShader());

				if (instance.shouldOverride) {
					if (instance.getGenericShader() != null) {
						OverrideInjector.INSTANCE.bind(IDhApiGenericObjectShaderProgram.class, instance.getGenericShader());
					}

					OverrideInjector.INSTANCE.bind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());
				}
			}
		};
		DhApi.events.bind(DhApiBeforeRenderSetupEvent.class, beforeRenderPassEvent);

	}

	private static void setupBeforeRenderPassEvent() {
		DhApiBeforeRenderPassEvent beforeCleanupEvent = new DhApiBeforeRenderPassEvent() {
			@Override
			public void beforeRender(DhApiEventParam<DhApiRenderParam> event) {
				DHCompatInternal instance = getInstance();

				// config overrides
				if (instance.shouldOverride) {
					DhApi.Delayed.configs.graphics().ambientOcclusion().enabled().setValue(false);
					DhApi.Delayed.configs.graphics().fog().drawMode().setValue(EDhApiFogDrawMode.FOG_DISABLED);

					if (event.value.renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT && !warnedAboutCombinedPass) {
						warnedAboutCombinedPass = true;
						IRIS_LOGGER.warn("Distant Horizons still supplied a combined opaque/translucent pass after early deferral was enabled; using the opaque DH program as a safe fallback for this frame.");
					}

				} else {
					DhApi.Delayed.configs.graphics().ambientOcclusion().enabled().clearValue();
					DhApi.Delayed.configs.graphics().fog().drawMode().clearValue();
				}


				// cleanup
				if (rendersOpaque(event.value.renderPass)) {
					if (instance.shouldOverride) {
						instance.getSolidShader().bind();
						atTranslucent = false;
					}
				}


				// opaque
				if (rendersOpaque(event.value.renderPass)) {
					float partialTicks = event.value.partialTicks;

					if (instance.shouldOverride) {
						Matrix4f projection = createLodProjection(event.value);
						Matrix4f modelView = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
						DHCompat.setProjection(projection);
						instance.getSolidShader().fillUniformData(
							projection,
							modelView,
							-1000,
							partialTicks);
					}
				}


				// transparent
				if (event.value.renderPass == EDhApiRenderPass.TRANSPARENT) {
					if (instance.shouldOverride && instance.getTranslucentFB() != null) {
						float partialTicks = event.value.partialTicks;
						instance.copyTranslucents(textureWidth, textureHeight);
						instance.getTranslucentShader().bind();
                        GL_STATE_MANAGER.disableCullFace();
						Matrix4f projection = createLodProjection(event.value);
						Matrix4f modelView = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
						DHCompat.setProjection(projection);

						instance.getTranslucentShader().fillUniformData(
							projection,
							modelView,
							-1000,
							partialTicks);

						instance.getTranslucentFB().bind();
					}

					atTranslucent = true;
				}

			}
		};

		DhApi.events.bind(DhApiBeforeRenderPassEvent.class, beforeCleanupEvent);
	}

	private static void setupBeforeApplyShaderEvent() {
		DhApiBeforeApplyShaderRenderEvent beforeApplyShaderEvent = new DhApiBeforeApplyShaderRenderEvent() {
			@Override
			public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
				DHCompatInternal instance = getInstance();
				if (instance.shouldOverride) {

					OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());

					event.cancelEvent();
				}
			}
		};

		DhApi.events.bind(DhApiBeforeApplyShaderRenderEvent.class, beforeApplyShaderEvent);
	}


}
