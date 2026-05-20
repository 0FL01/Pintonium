package org.embeddedt.embeddium.compat.mc;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class NativeImage extends BufferedImage implements MCNativeImage {
    private final int width;
    private final int height;

    public NativeImage(int width, int height, boolean useCalloc) {
        super(width, height, BufferedImage.TYPE_INT_ARGB);
        this.width = width;
        this.height = height;
    }

    private NativeImage(BufferedImage image) {
        super(image.getColorModel(), image.getRaster(), image.isAlphaPremultiplied(), null);
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    public static NativeImage read(ByteBuffer buffer) throws IOException {
        return read(new ByteBufferBackedInputStream(buffer));
    }

    public static NativeImage read(InputStream stream) throws IOException {
        BufferedImage image = ImageIO.read(stream);
        if (image == null) {
            throw new IOException("Unable to decode image data");
        }

        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            converted.getGraphics().drawImage(image, 0, 0, null);
            image = converted;
        }

        return new NativeImage(image);
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void setPixelRGBA(int x, int y, int color) {
        setRGB(x, y, color);
    }

    @Override
    public int getPixelRGBA(int x, int y) {
        return getRGB(x, y);
    }

    @Override
    public void upload(int level, int xOffset, int yOffset, int unpackSkipPixels, int unpackSkipRows, int width, int height, boolean blur, boolean clamp, boolean mipmap, boolean autoClose) {
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);

        int[] pixels = new int[width * height];
        getRGB(unpackSkipPixels, unpackSkipRows, width, height, pixels, 0, width);

        IntBuffer buffer = BufferUtils.createIntBuffer(pixels.length);
        buffer.put(pixels);
        buffer.flip();

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, xOffset, yOffset, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
    }
}
