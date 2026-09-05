package net.irisshaders.iris.texture.pbr.loader;

import net.irisshaders.iris.texture.format.LabPBRTextureFormat;
import net.irisshaders.iris.texture.mipmap.ChannelMipmapGenerator;
import net.irisshaders.iris.texture.mipmap.CustomMipmapGenerator;
import net.irisshaders.iris.texture.mipmap.LinearBlendFunction;
import net.irisshaders.iris.texture.pbr.PBRType;
import net.irisshaders.iris.texture.pbr.VintagePBRAtlasTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.embeddedt.embeddium.api.util.ColorABGR;
import org.embeddedt.embeddium.compat.mc.MCNativeImage;
import org.embeddedt.embeddium.compat.mc.MCResourceManager;
import org.embeddedt.embeddium.compat.mc.NativeImage;
import org.embeddedt.embeddium.impl.resource.VintageAbstractTexture;
import org.embeddedt.embeddium.impl.resource.VintageResourceManager;
import org.embeddedt.embeddium.impl.texture.VintageTextureUpload;
import org.lwjgl.opengl.GL11;
import org.taumc.celeritas.mixin.shaders.texture.MixinPBRTextureMapAccessor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

public final class VintageAtlasPBRLoader implements PBRTextureLoader<VintageAbstractTexture> {
    private static final int FLUID_SPECULAR = ColorABGR.pack(240, 10, 0, 0);
    private static final CustomMipmapGenerator NORMAL_MIPMAPS = new ChannelMipmapGenerator(
            LinearBlendFunction.INSTANCE, LinearBlendFunction.INSTANCE,
            LinearBlendFunction.INSTANCE, LinearBlendFunction.INSTANCE);

    @Override
    public void load(VintageAbstractTexture texture, MCResourceManager resources, PBRTextureConsumer consumer) {
        if (!(texture.unwrap() instanceof TextureMap atlas) || atlas != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return;
        }

        Set<String> fluidSprites = new HashSet<>();
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            // Vanilla water/lava remain shaderpack-owned. No block/material IDs are reassigned.
            if (fluid == FluidRegistry.WATER || fluid == FluidRegistry.LAVA) {
                continue;
            }
            if (fluid.getStill() != null) fluidSprites.add(fluid.getStill().toString());
            if (fluid.getFlowing() != null) fluidSprites.add(fluid.getFlowing().toString());
        }

        try (var ignored = new VintageTextureUpload()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getId());
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width == 0 || height == 0) return;

            int levels = atlas.getMipmapLevels();
            for (PBRType type : PBRType.values()) {
                VintagePBRAtlasTexture materialAtlas = null;
                int authored = 0;
                var generated = new ArrayList<String>();
                try {
                    for (TextureAtlasSprite sprite : ((MixinPBRTextureMapAccessor) atlas).celeritas$getUploadedSprites().values()) {
                        if (sprite == atlas.getMissingSprite()) continue;
                        NativeImage image = readAuthored(((VintageResourceManager) resources).unwrap(), atlas, sprite, type);
                        if (image != null) {
                            authored++;
                        } else if (type == PBRType.SPECULAR && fluidSprites.contains(sprite.getIconName())) {
                            image = new NativeImage(sprite.getIconWidth(), sprite.getIconHeight(), false);
                            for (int y = 0; y < image.getHeight(); y++) {
                                for (int x = 0; x < image.getWidth(); x++) {
                                    image.setPixelRGBA(x, y, FLUID_SPECULAR);
                                }
                            }
                            generated.add(sprite.getIconName());
                        } else {
                            continue;
                        }

                        if (materialAtlas == null) materialAtlas = new VintagePBRAtlasTexture(width, height, levels, type);
                        materialAtlas.bind();
                        CustomMipmapGenerator generator = type == PBRType.SPECULAR
                                ? LabPBRTextureFormat.SPECULAR_MIPMAP_GENERATOR : NORMAL_MIPMAPS;
                        MCNativeImage[] mipmaps = generator.generateMipLevels(new MCNativeImage[]{image}, levels);
                        for (int level = 0; level <= levels; level++) {
                            MCNativeImage mip = mipmaps[level];
                            mip.upload(level, sprite.getOriginX() >> level, sprite.getOriginY() >> level,
                                    0, 0, mip.getWidth(), mip.getHeight(), false, true, levels > 0, false);
                        }
                    }
                    if (materialAtlas != null) {
                        // NativeImage.upload configures a non-mipped filter for each subimage.
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                                levels > 0 ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_NEAREST);
                        if (type == PBRType.NORMAL) consumer.acceptNormalTexture(materialAtlas);
                        else consumer.acceptSpecularTexture(materialAtlas);
                        materialAtlas = null; // Ownership transferred to the manager.
                    }
                    IRIS_LOGGER.info("[Fluid PBR] {} atlas {}x{}, mipLevels={}, authored={}, generated fluid sprites={}",
                            type, width, height, levels, authored, generated);
                } finally {
                    if (materialAtlas != null) materialAtlas.close();
                }
            }
        }
    }

    private static NativeImage readAuthored(IResourceManager resources, TextureMap atlas, TextureAtlasSprite sprite, PBRType type) {
        ResourceLocation name = new ResourceLocation(sprite.getIconName());
        ResourceLocation location = new ResourceLocation(name.getNamespace(),
                atlas.getBasePath() + "/" + type.appendSuffix(name.getPath()) + ".png");
        try (IResource resource = resources.getResource(location)) {
            // Never freeze a material animation at frame zero or squash its sheet into one frame.
            if (resource.getMetadata("animation") != null) {
                IRIS_LOGGER.warn("[Fluid PBR] Animated authored map {} is not supported; using fluid fallback/neutral material", location);
                return null;
            }
            NativeImage source = NativeImage.read(resource.getInputStream());
            int width = sprite.getIconWidth(), height = sprite.getIconHeight();
            if ((long) source.getWidth() * height != (long) source.getHeight() * width) {
                IRIS_LOGGER.warn("[Fluid PBR] Non-static dimensions in {} ({}x{}, sprite {}x{}); using fluid fallback/neutral material",
                        location, source.getWidth(), source.getHeight(), width, height);
                return null;
            }
            if (source.getWidth() == width && source.getHeight() == height) return source;
            NativeImage scaled = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    scaled.setPixelRGBA(x, y, source.getPixelRGBA(x * source.getWidth() / width, y * source.getHeight() / height));
                }
            }
            return scaled;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException | RuntimeException e) {
            IRIS_LOGGER.warn("[Fluid PBR] Cannot load authored map {}; using fluid fallback/neutral material", location, e);
            return null;
        }
    }
}
