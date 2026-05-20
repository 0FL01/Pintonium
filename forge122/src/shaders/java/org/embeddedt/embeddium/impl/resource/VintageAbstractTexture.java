package org.embeddedt.embeddium.impl.resource;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import org.embeddedt.embeddium.compat.mc.MCAbstractTexture;

public class VintageAbstractTexture implements MCAbstractTexture {
    private final ITextureObject texture;

    public VintageAbstractTexture(ITextureObject texture) {
        this.texture = texture;
    }

    @Override
    public int getId() {
        return this.texture.getGlTextureId();
    }

    @Override
    public void releaseId() {
        // Textures returned by Minecraft's texture manager are not owned by Iris.
    }

    @Override
    public void bind() {
        GlStateManager.bindTexture(this.getId());
    }

    @Override
    public void close() {
        // Textures returned by Minecraft's texture manager are not owned by Iris.
    }
}
