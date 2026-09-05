package net.irisshaders.iris;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.KHRDebug;

import java.util.concurrent.atomic.AtomicInteger;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

/** Client render-thread setup; the driver callback must not query or mutate GL state. */
final class VintageShaderDiagnostics {
    private static final int MESSAGE_LIMIT = 2000;
    private static final AtomicInteger messages = new AtomicInteger();
    private static GLDebugMessageCallback callback;
    private static boolean enabledOutput;

    private VintageShaderDiagnostics() {
    }

    static void configure() {
        boolean enabled = IrisCommon.getIrisConfig().areDebugOptionsEnabled();
        var caps = GL.getCapabilities();
        if (!caps.OpenGL43 && !caps.GL_KHR_debug) {
            if (enabled) {
                IRIS_LOGGER.warn("[Shader diagnostics] KHR_debug unavailable; shader source dumps and pipeline logs remain available.");
            }
            return;
        }

        if (!enabled) {
            if (callback != null) {
                // Do not unregister a callback installed later by another mod.
                if (GL43C.glGetPointer(KHRDebug.GL_DEBUG_CALLBACK_FUNCTION) == callback.address()) {
                    KHRDebug.glDebugMessageCallback(null, 0L);
                    if (enabledOutput) {
                        GL11.glDisable(KHRDebug.GL_DEBUG_OUTPUT);
                    }
                }
                callback.free();
                callback = null;
            }
            return;
        }

        messages.set(0);
        IRIS_LOGGER.info("[Shader diagnostics] pack={} Java={} GPU={} renderer={} OpenGL={} GLSL={}",
                IrisCommon.getIrisConfig().getShaderPackName().orElse("none"),
                System.getProperty("java.version"), GL11.glGetString(GL11.GL_VENDOR),
                GL11.glGetString(GL11.GL_RENDERER), GL11.glGetString(GL11.GL_VERSION),
                GL11.glGetString(org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION));
        if (callback != null) {
            if (GL43C.glGetPointer(KHRDebug.GL_DEBUG_CALLBACK_FUNCTION) != callback.address()) {
                IRIS_LOGGER.warn("[Shader diagnostics] GL callback replaced by another mod; not overwriting it.");
            }
            return;
        }
        if (GL43C.glGetPointer(KHRDebug.GL_DEBUG_CALLBACK_FUNCTION) != 0L) {
            IRIS_LOGGER.warn("[Shader diagnostics] Another GL callback is already installed; not overwriting it.");
            return;
        }

        callback = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
            int count = messages.incrementAndGet();
            if (count <= MESSAGE_LIMIT) {
                IRIS_LOGGER.warn("[Shader GL] source={} type={} id={} severity={} {}",
                        Integer.toHexString(source), Integer.toHexString(type), Integer.toHexString(id),
                        Integer.toHexString(severity), GLDebugMessageCallback.getMessage(length, message));
            } else if (count == MESSAGE_LIMIT + 1) {
                IRIS_LOGGER.warn("[Shader diagnostics] GL message limit reached ({}); reload shaders to resume logging.", MESSAGE_LIMIT);
            }
        });
        enabledOutput = !GL11.glIsEnabled(KHRDebug.GL_DEBUG_OUTPUT);
        KHRDebug.glDebugMessageCallback(callback, 0L);
        KHRDebug.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE,
                GL11.GL_DONT_CARE, (int[]) null, true);
        GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT);
        IRIS_LOGGER.info("[Shader diagnostics] GL callback enabled; maximum {} messages per shader reload.", MESSAGE_LIMIT);
    }
}
