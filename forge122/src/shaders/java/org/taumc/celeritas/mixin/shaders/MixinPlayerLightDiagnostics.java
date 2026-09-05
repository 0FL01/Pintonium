package org.taumc.celeritas.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shadows.CommonShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

/** Bounded readback to distinguish skin, armor/layers and subsequent post-processing. */
@Mixin(RenderLivingBase.class)
public class MixinPlayerLightDiagnostics {
    @Unique private boolean iris$capturePlayer;
    @Unique private long iris$nextCapture;
    @Unique private int iris$captures;

    @WrapMethod(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")
    private void iris$measurePlayer(EntityLivingBase entity, double x, double y, double z,
            float yaw, float ticks, Operation<Void> original) {
        var pipeline = IrisCommon.getPipelineManager().getPipelineNullable();
        long now = System.nanoTime();
        boolean capture = entity == Minecraft.getMinecraft().player && !CommonShadowRenderer.ACTIVE
                && pipeline != null && pipeline.getPhase() == WorldRenderingPhase.ENTITIES
                && IrisCommon.getIrisConfig().areDebugOptionsEnabled()
                && iris$captures < 90 && now >= iris$nextCapture;
        boolean previous = iris$capturePlayer;
        iris$capturePlayer = capture;
        if (capture) {
            iris$captures++;
            iris$nextCapture = now + 1_000_000_000L;
            IRIS_LOGGER.info("[Player light] sample={} cameraYaw={} cameraPitch={} entityPos={},{},{}",
                    iris$captures, Minecraft.getMinecraft().player.rotationYaw,
                    Minecraft.getMinecraft().player.rotationPitch, x, y, z);
        }
        try {
            if (capture) iris$samplePlayer("before-entity");
            original.call(entity, x, y, z, yaw, ticks);
            if (capture) iris$samplePlayer("after-layers");
        } finally {
            iris$capturePlayer = previous;
        }
    }

    @Inject(method = "renderModel", at = @At("HEAD"))
    private void iris$beforeSkin(EntityLivingBase entity, float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (iris$capturePlayer) iris$samplePlayer("before-skin");
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void iris$afterSkin(EntityLivingBase entity, float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (iris$capturePlayer) iris$samplePlayer("after-skin");
    }

    @Unique
    private void iris$samplePlayer(String stage) {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int buffer = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        IRIS_LOGGER.info("[Player light] sample={} stage={} program={} fbo={} buffer={} lightmap={},{} blend={} depthMask={} entityId={} heldLight={},{}",
                iris$captures, stage, program, draw, buffer, OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY,
                GL11.glIsEnabled(GL11.GL_BLEND), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                iris$uniformInt(program, "entityId"), iris$uniformInt(program, "heldBlockLightValue"), iris$uniformInt(program, "heldBlockLightValue2"));
        if (draw == 0 || buffer == GL11.GL_NONE || viewport[2] <= 0 || viewport[3] <= 0
                || GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_SAMPLES) != 0
                || GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING) != 0
                || GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH) != 0
                || GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS) != 0
                || GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS) != 0) return;
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        int oldBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return;
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, buffer, GL30.GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE);
            if (type == GL11.GL_INT || type == GL11.GL_UNSIGNED_INT) return;
            GL11.glReadBuffer(buffer);
            var pixel = BufferUtils.createFloatBuffer(4);
            for (int part = 0; part < 3; part++) {
                int sx = viewport[0] + viewport[2] / 2;
                int sy = viewport[1] + (int) ((viewport[3] - 1) * (0.35f + part * 0.1f));
                GL11.glReadPixels(sx, sy, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                IRIS_LOGGER.info("[Player light pixel] sample={} stage={} xy={},{} rgba={},{},{},{}",
                        iris$captures, stage, sx, sy, pixel.get(0), pixel.get(1), pixel.get(2), pixel.get(3));
            }
        } finally {
            GL11.glReadBuffer(oldBuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
        }
    }

    @Unique
    private static int iris$uniformInt(int program, String name) {
        if (program == 0) return -1;
        int location = GL20.glGetUniformLocation(program, name);
        return location < 0 ? -1 : GL20.glGetUniformi(program, location);
    }
}
