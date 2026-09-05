package org.embeddedt.embeddium.impl.texture;

import org.embeddedt.embeddium.compat.mc.MCDynamicTexture;
import org.embeddedt.embeddium.compat.mc.MCNativeImage;
import org.embeddedt.embeddium.compat.mc.NativeImage;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;

public class VintageDynamicTexture implements MCDynamicTexture {
    private int texture;
    private final NativeImage pixels;

    public VintageDynamicTexture(NativeImage pixels) {
        this.pixels = pixels;
        this.texture = GL11.glGenTextures();
        try (var ignored = new VintageTextureUpload()) {
            bind();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, pixels.getWidth(), pixels.getHeight(),
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
            pixels.upload(0, 0, 0, 0, 0, pixels.getWidth(), pixels.getHeight(), false, false, false, false);
        } catch (RuntimeException | Error e) {
            releaseId();
            throw e;
        }
    }

    @Override
    public MCNativeImage getPixels() {
        return this.pixels;
    }

    @Override
    public void upload() {
        try (var ignored = new VintageTextureUpload()) {
            bind();
            pixels.upload(0, 0, 0, 0, 0, pixels.getWidth(), pixels.getHeight(), false, false, false, false);
        }
    }

    @Override
    public int getId() {
        return this.texture;
    }

    @Override
    public void releaseId() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
    }

    @Override
    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, getId());
    }

    @Override
    public void close() {
        releaseId();
    }
}
