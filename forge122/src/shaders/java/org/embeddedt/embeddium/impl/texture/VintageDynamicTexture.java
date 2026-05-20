package org.embeddedt.embeddium.impl.texture;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.embeddedt.embeddium.compat.mc.MCDynamicTexture;
import org.embeddedt.embeddium.compat.mc.MCNativeImage;
import org.embeddedt.embeddium.compat.mc.NativeImage;

public class VintageDynamicTexture implements MCDynamicTexture {
    private final DynamicTexture texture;
    private final NativeImage pixels;

    public VintageDynamicTexture(NativeImage pixels) {
        this.pixels = pixels;
        this.texture = new DynamicTexture(pixels);
    }

    @Override
    public MCNativeImage getPixels() {
        return this.pixels;
    }

    @Override
    public void upload() {
        int[] data = this.texture.getTextureData();
        this.pixels.getRGB(0, 0, this.pixels.getWidth(), this.pixels.getHeight(), data, 0, this.pixels.getWidth());
        this.texture.updateDynamicTexture();
    }

    @Override
    public int getId() {
        return this.texture.getGlTextureId();
    }

    @Override
    public void releaseId() {
        this.texture.deleteGlTexture();
    }

    @Override
    public void bind() {
        GlStateManager.bindTexture(getId());
    }

    @Override
    public void close() {
        releaseId();
    }
}
