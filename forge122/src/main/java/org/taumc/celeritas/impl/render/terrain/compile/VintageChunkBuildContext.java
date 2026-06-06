package org.taumc.celeritas.impl.render.terrain.compile;

//? if 1.10.2 {
//?}
import lombok.Getter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.shaderpack.materialmap.VintageWorldRenderingSettings;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLilyPad;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.BlockVine;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexExtendedData;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.impl.extensions.SpriteExtension;
import org.taumc.celeritas.impl.extensions.TextureMapExtension;
import org.taumc.celeritas.impl.world.WorldSlice;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class VintageChunkBuildContext extends ChunkBuildContext {
    public static final BlockRenderLayer[] LAYERS = new BlockRenderLayer[] {
            BlockRenderLayer.SOLID,
            BlockRenderLayer.CUTOUT_MIPPED,
            BlockRenderLayer.CUTOUT,
            BlockRenderLayer.TRANSLUCENT
    };
    private final TextureMapExtension textureAtlas;
    private final net.minecraft.client.renderer.BufferBuilder[] worldRenderers = new net.minecraft.client.renderer.BufferBuilder[LAYERS.length];
    private final boolean[] usedWorldRenderers = new boolean[LAYERS.length];
    private final ArrayList<QuadMetadata>[] quadMetadataByLayer = new ArrayList[LAYERS.length];
    private int offX, offY, offZ;
    @Getter
    private final WorldSlice worldSlice;
    private final RenderPassConfiguration<?> renderPassConfiguration;
    private final boolean useRenderPassOptimization;

    public VintageChunkBuildContext(WorldClient world, RenderPassConfiguration renderPassConfiguration) {
        super(renderPassConfiguration);
        this.renderPassConfiguration = renderPassConfiguration;
        this.worldSlice = new WorldSlice(world);
        this.textureAtlas = (TextureMapExtension) Minecraft.getMinecraft().getTextureMapBlocks();
        this.useRenderPassOptimization = CeleritasVintage.options().performance.useRenderPassOptimization;
        for (int i = 0; i < LAYERS.length; i++) {
            this.quadMetadataByLayer[i] = new ArrayList<>();
        }
    }

    public void setupTranslation(int x, int y, int z) {
        this.offX = x;
        this.offY = y;
        this.offZ = z;
    }

    public net.minecraft.client.renderer.BufferBuilder getBufferForLayer(BlockRenderLayer layer) {
        var builder = this.worldRenderers[layer.ordinal()];
        if (builder == null) {
            builder = new net.minecraft.client.renderer.BufferBuilder(131072);
            this.worldRenderers[layer.ordinal()] = builder;
        }
        if (!this.usedWorldRenderers[layer.ordinal()]) {
            builder.begin(GL11C.GL_QUADS, DefaultVertexFormats.BLOCK);
            builder.setTranslation(-this.offX, -this.offY, -this.offZ);
            this.usedWorldRenderers[layer.ordinal()] = true;
        }
        return builder;
    }

    public void recordRenderedQuads(BlockRenderLayer layer, int startVertex, int endVertex, IBlockState state, BlockPos pos) {
        int startQuad = startVertex / 4;
        int endQuad = endVertex / 4;

        if (endQuad <= startQuad) {
            return;
        }

        this.quadMetadataByLayer[layer.ordinal()].add(new QuadMetadata(
                startQuad,
                endQuad,
                shaderBlockId(state),
                shaderRenderType(state),
                pos.getX() - this.offX,
                pos.getY() - this.offY,
                pos.getZ() - this.offZ,
                (byte) state.getLightValue()));
    }

    public void convertVanillaDataToCeleritasData(ChunkBuildBuffers buffers) {
        var renderers = this.worldRenderers;
        var used = this.usedWorldRenderers;
        for (int i = 0; i < renderers.length; i++) {
            if(!used[i]) {
                continue;
            }
            var bufferBuilder = Objects.requireNonNull(renderers[i]);
            bufferBuilder.finishDrawing();
            used[i] = false;
            ByteBuffer rawBuffer = bufferBuilder.getByteBuffer();
            var material = buffers.getRenderPassConfiguration().getMaterialForRenderType(LAYERS[i]);
            copyBlockData(rawBuffer, buffers, material, this.quadMetadataByLayer[i]);
            this.quadMetadataByLayer[i].clear();
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        this.worldSlice.reset();
        for (int i = 0; i < LAYERS.length; i++) {
            if (this.usedWorldRenderers[i]) {
                this.worldRenderers[i].finishDrawing();
                this.usedWorldRenderers[i] = false;
            }
            this.quadMetadataByLayer[i].clear();
        }
    }

    private Material selectMaterial(Material material, TextureAtlasSprite sprite) {
        if (sprite != null && sprite.getClass() == TextureAtlasSprite.class && !sprite.hasAnimationMetadata() && this.useRenderPassOptimization) {
            var transparencyLevel = ((SpriteExtension)sprite).celeritas$getTransparencyLevel();
            if (transparencyLevel == SpriteTransparencyLevel.OPAQUE) {
                // Downgrade to solid
                return this.renderPassConfiguration.defaultSolidMaterial();
            } else if (material == this.renderPassConfiguration.defaultTranslucentMaterial() && transparencyLevel != SpriteTransparencyLevel.TRANSLUCENT) {
                // Downgrade to cutout
                return this.renderPassConfiguration.defaultCutoutMippedMaterial();
            }
        }
        return material;
    }

    private static final int BLOCK_VERTEX_FORMAT_SIZE;

    static {
        var format = DefaultVertexFormats.BLOCK;
        int size = 0;
        for (int i = 0; i < format.getElementCount(); i++) {
            size += format.getElement(i).getSize();
        }
        BLOCK_VERTEX_FORMAT_SIZE = size;
    }

    private void copyBlockData(ByteBuffer source, ChunkBuildBuffers buffers, Material material, ArrayList<QuadMetadata> quadMetadata) {
        int vsize = BLOCK_VERTEX_FORMAT_SIZE;
        int numQuads = source.limit() / (vsize * 4);
        long ptr = MemoryUtil.memAddress(source);
        var quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        var animatedSpritesList = ((MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity>)buffers.getSectionContextBundle()).animatedSprites;
        int metadataIndex = 0;
        for(int q = 0; q < numQuads; q++) {
            float uSum = 0, vSum = 0;
            for(int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.x = MemoryUtil.memGetFloat(ptr);
                vertex.y = MemoryUtil.memGetFloat(ptr + 4);
                vertex.z = MemoryUtil.memGetFloat(ptr + 8);
                vertex.color = MemoryUtil.memGetInt(ptr + 12);
                vertex.u = MemoryUtil.memGetFloat(ptr + 16);
                vertex.v = MemoryUtil.memGetFloat(ptr + 20);
                uSum += vertex.u;
                vSum += vertex.v;
                vertex.light = MemoryUtil.memGetInt(ptr + 24);
                ptr += vsize;
            }
            TextureAtlasSprite sprite = this.textureAtlas.celeritas$findFromUV(uSum * 0.25f, vSum * 0.25f);
            if (sprite != null && sprite.hasAnimationMetadata()) {
                animatedSpritesList.add(sprite);
            }
            int trueNormal = QuadUtil.calculateNormal(quad);
            for (int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.vanillaNormal = trueNormal;
                vertex.trueNormal = trueNormal;
            }
            ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            Material correctMaterial = selectMaterial(material, sprite);
            QuadMetadata metadata = findMetadata(quadMetadata, q, metadataIndex);
            if (metadata != null) {
                while (metadataIndex + 1 < quadMetadata.size() && q >= quadMetadata.get(metadataIndex).endQuad) {
                    metadataIndex++;
                }
                ChunkVertexExtendedData.set(
                        metadata.blockId,
                        metadata.renderType,
                        ChunkVertexExtendedData.encodeMidTexCoord(uSum * 0.25f, vSum * 0.25f),
                        trueNormal,
                        ChunkVertexExtendedData.computeTangent(quad, trueNormal),
                        metadata.localX,
                        metadata.localY,
                        metadata.localZ,
                        metadata.lightValue);
            } else {
                ChunkVertexExtendedData.clear();
            }
            buffers.get(correctMaterial).getVertexBuffer(facing).push(quad, correctMaterial);
        }
        ChunkVertexExtendedData.clear();
    }

    private static QuadMetadata findMetadata(ArrayList<QuadMetadata> metadata, int quad, int startIndex) {
        for (int i = startIndex; i < metadata.size(); i++) {
            QuadMetadata entry = metadata.get(i);
            if (quad < entry.startQuad) {
                return null;
            }
            if (quad < entry.endQuad) {
                return entry;
            }
        }
        return null;
    }

    private static int shaderBlockId(IBlockState state) {
        Object2IntMap<IBlockState> mappedBlockIds = VintageWorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (mappedBlockIds.containsKey(state)) {
            int mappedId = mappedBlockIds.getInt(state);
            if (mappedId != -1) {
                return mappedId;
            }
        }

        Block block = state.getBlock();
        int fallbackId = shaderFallbackBlockId(state, block, mappedBlockIds);
        if (fallbackId != -1) {
            return fallbackId;
        }

        int id = Block.getIdFromBlock(block);

        if (id == 8) {
            return 9;
        }
        if (id == 10) {
            return 11;
        }

        return id;
    }

    private static int shaderFallbackBlockId(IBlockState state, Block block, Object2IntMap<IBlockState> mappedBlockIds) {
        ResourceLocation registryName = Block.REGISTRY.getNameForObject(block);
        if (registryName == null || "minecraft".equals(registryName.getNamespace())) {
            return -1;
        }

        String path = registryName.getPath().toLowerCase(Locale.ROOT);

        if (isLeavesLike(state, block, path)) {
            return mappedIdForBlocks(mappedBlockIds, Blocks.LEAVES, Blocks.LEAVES2);
        }
        if (isVineLike(state, block, path)) {
            return mappedIdForBlocks(mappedBlockIds, Blocks.VINE);
        }
        if (isLilyPadLike(block, path)) {
            return mappedIdForBlocks(mappedBlockIds, Blocks.WATERLILY);
        }
        if (isPlantLike(state, block)) {
            return mappedIdForBlocks(mappedBlockIds, Blocks.TALLGRASS, Blocks.SAPLING, Blocks.YELLOW_FLOWER, Blocks.RED_FLOWER);
        }

        return genericMaterialFallbackId();
    }

    private static int genericMaterialFallbackId() {
        // Unknown modded blocks should not be guessed as stone/iron/wood here:
        // shaderpacks attach PBR, POM, emissive, and lighting rules to those IDs.
        // Material 0 keeps the texture in the shader's neutral path.
        return 0;
    }

    private static int mappedIdForBlocks(Object2IntMap<IBlockState> mappedBlockIds, Block... blocks) {
        for (Block block : blocks) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                if (mappedBlockIds.containsKey(state)) {
                    int mappedId = mappedBlockIds.getInt(state);
                    if (mappedId != -1) {
                        return mappedId;
                    }
                }
            }
        }

        return -1;
    }

    private static boolean isLeavesLike(IBlockState state, Block block, String path) {
        return block instanceof BlockLeaves
                || state.getMaterial() == net.minecraft.block.material.Material.LEAVES
                || containsAny(path, "leaves", "leaf");
    }

    private static boolean isVineLike(IBlockState state, Block block, String path) {
        return block instanceof BlockVine
                || state.getMaterial() == net.minecraft.block.material.Material.VINE
                || containsAny(path, "vine", "ivy");
    }

    private static boolean isLilyPadLike(Block block, String path) {
        return block instanceof BlockLilyPad
                || containsAny(path, "lily_pad", "lilypad", "waterlily", "water_lily", "frogbit", "duckweed");
    }

    private static boolean isPlantLike(IBlockState state, Block block) {
        net.minecraft.block.material.Material material = state.getMaterial();
        if (material == net.minecraft.block.material.Material.GRASS
                || material == net.minecraft.block.material.Material.GROUND
                || material == net.minecraft.block.material.Material.ROCK) {
            return false;
        }

        return block instanceof BlockBush
                || block instanceof BlockCrops
                || block instanceof BlockDoublePlant
                || block instanceof BlockReed
                || block instanceof BlockSapling
                || block instanceof BlockTallGrass
                || material == net.minecraft.block.material.Material.PLANTS
                || material == net.minecraft.block.material.Material.CACTUS;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }

        return false;
    }

    private static short shaderRenderType(IBlockState state) {
        return state.getMaterial().isLiquid() ? (short) 1 : (short) 0;
    }

    private record QuadMetadata(int startQuad, int endQuad, int blockId, short renderType,
                                int localX, int localY, int localZ, byte lightValue) {
    }
}
