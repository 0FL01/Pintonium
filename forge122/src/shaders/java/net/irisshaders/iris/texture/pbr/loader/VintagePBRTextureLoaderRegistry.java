package net.irisshaders.iris.texture.pbr.loader;

import org.embeddedt.embeddium.impl.resource.VintageAbstractTexture;

import java.util.HashMap;
import java.util.Map;

public final class VintagePBRTextureLoaderRegistry implements PBRTextureLoaderRegistry {
    private final Map<Class<?>, PBRTextureLoader<?>> loaders = new HashMap<>();

    public VintagePBRTextureLoaderRegistry() {
        // TextureTracker holds the native adapter, not the wrapped TextureMap class.
        register(VintageAbstractTexture.class, new VintageAtlasPBRLoader());
    }

    @Override
    public <T> void register(Class<? extends T> clazz, PBRTextureLoader<T> loader) {
        loaders.put(clazz, loader);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> PBRTextureLoader<T> getLoader(Class<? extends T> clazz) {
        return (PBRTextureLoader<T>) loaders.get(clazz);
    }
}
