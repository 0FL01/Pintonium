package org.embeddedt.embeddium.impl.resource;

import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import org.embeddedt.embeddium.compat.mc.MCAbstractTexture;
import org.embeddedt.embeddium.compat.mc.MCResourceLocation;
import org.embeddedt.embeddium.compat.mc.MCTextureManager;

public class VintageTextureManager implements MCTextureManager {
    private final TextureManager textureManager;

    public VintageTextureManager(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    @Override
    public MCAbstractTexture getTexture(MCResourceLocation path) {
        ITextureObject texture = this.textureManager.getTexture(VintageResourceLocation.unwrap(path));
        return texture == null ? null : new VintageAbstractTexture(texture);
    }
}
