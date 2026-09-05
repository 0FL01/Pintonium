package net.irisshaders.iris.texture.pbr;

import org.embeddedt.embeddium.compat.mc.MCAbstractTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

/** Parallel atlas with the same layout as albedo, owned exclusively by the PBR manager. */
public final class VintagePBRAtlasTexture implements MCAbstractTexture {
    private int id;

    public VintagePBRAtlasTexture(int width, int height, int levels, PBRType type) {
        id = GL11.glGenTextures();
        try {
            bind();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                    levels > 0 ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            for (int level = 0; level <= levels; level++) {
                int w = width >> level, h = height >> level;
                IntBuffer pixels = MemoryUtil.memAllocInt(Math.multiplyExact(w, h));
                try {
                    int neutral = Integer.reverseBytes(type.getDefaultValue());
                    for (int i = 0; i < pixels.capacity(); i++) {
                        pixels.put(i, neutral);
                    }
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, w, h, 0,
                            GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    @Override
    public void releaseId() {
        if (id != 0) {
            GL11.glDeleteTextures(id);
            id = 0;
        }
    }

    @Override
    public void close() {
        releaseId();
    }
}
