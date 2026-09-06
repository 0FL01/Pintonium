package org.taumc.celeritas.impl.render.terrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.ShaderModBridge;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.joml.Matrix4f;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.impl.render.GlMatrixSnapshot;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;

import java.util.*;

/**
 * Provides an extension to vanilla's {@link net.minecraft.client.renderer.RenderGlobal}.
 */
public class CeleritasWorldRenderer extends SimpleWorldRenderer<WorldClient, VintageRenderSectionManager, BlockRenderLayer, TileEntity, CeleritasWorldRenderer.TileEntityRenderContext>  {
    private HeldItemLight heldItemLight = HeldItemLight.NONE;
    private HeldItemLight terrainHeldItemLight = HeldItemLight.NONE;

    public HeldItemLight getHeldItemLight(World world) {
        return this.world == world ? this.heldItemLight : HeldItemLight.NONE;
    }

    public HeldItemLight getTerrainHeldItemLight(World world) {
        return this.world == world ? this.terrainHeldItemLight : HeldItemLight.NONE;
    }

    @Override
    public void setWorld(WorldClient world) {
        if (this.world != world) {
            this.heldItemLight = HeldItemLight.NONE;
            this.terrainHeldItemLight = HeldItemLight.NONE;
        }
        super.setWorld(world);
    }

    public record TileEntityRenderContext(Map<Integer, DestroyBlockProgress> damagedBlocks, float partialTicks, Runnable prepareRenderState) {}

    /**
     * @return The CeleritasWorldRenderer based on the current dimension
     */
    public static CeleritasWorldRenderer instance() {
        return SimpleWorldRenderer.Provider.getWorldRenderer(Minecraft.getMinecraft().renderGlobal);
    }

    /**
     * @return The CeleritasWorldRenderer based on the current dimension, or null if none is attached
     */
    public static CeleritasWorldRenderer instanceNullable() {
        return SimpleWorldRenderer.Provider.getWorldRendererNullable(Minecraft.getMinecraft().renderGlobal);
    }

    public void setCurrentViewport(org.embeddedt.embeddium.impl.render.viewport.Viewport viewport) {
        this.currentViewport = viewport;
    }

    @Override
    public int getEffectiveRenderDistance() {
        return Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
    }

    @Override
    protected ChunkRenderMatrices createChunkRenderMatrices() {
        if (GlMatrixSnapshot.isRenderingShadowPass()) {
            // Shadow terrain must use the live sun camera established by the pack.
            // Reusing the saved player camera here produces an invalid/empty shadow
            // map, which looks like directional light leaking through the world.
            GlMatrixSnapshot shadowCamera = GlMatrixSnapshot.capture();
            return new ChunkRenderMatrices(shadowCamera.projection(), shadowCamera.modelView());
        }

        GlMatrixSnapshot mainCamera = GlMatrixSnapshot.getMainCamera();
        if (mainCamera != null) {
            // ActiveRenderInfo's static buffers are overwritten by the shader shadow
            // camera. Use the player camera captured before shadows so the terrain
            // normal matrix and the pack's celestial uniforms share the same space.
            return new ChunkRenderMatrices(
                    new Matrix4f(mainCamera.projection()),
                    new Matrix4f(mainCamera.modelView()));
        }

        return new ChunkRenderMatrices(
                new Matrix4f(ActiveRenderInfoAccessor.getProjectionMatrix()),
                new Matrix4f(ActiveRenderInfoAccessor.getModelViewMatrix()));
    }

    @Override
    protected VintageRenderSectionManager createRenderSectionManager(CommandList commandList) {
        return VintageRenderSectionManager.create(chooseVertexType(), this.world, this.getEffectiveRenderDistance(), commandList);
    }

    /**
     * Performs a render pass for the given {@link BlockRenderLayer} and draws all visible chunks for it.
     */
    public void drawChunkLayer(BlockRenderLayer renderLayer, double x, double y, double z) {
        super.drawChunkLayer(renderLayer, x, y, z);

        GlStateManager.resetColor();
    }

    @Override
    protected CameraState captureCameraState(float ticks) {
        var player = Minecraft.getMinecraft().player;
        int level = player == null || player.world != this.world || player.isDead || player.isSpectator() ? 0
                : Math.max(HeldItemLight.lightValue(player.getHeldItemMainhand()),
                        HeldItemLight.lightValue(player.getHeldItemOffhand()));
        this.heldItemLight = level == 0 ? HeldItemLight.NONE : new HeldItemLight(
                player.lastTickPosX + (player.posX - player.lastTickPosX) * ticks,
                player.lastTickPosY + (player.posY - player.lastTickPosY) * ticks + player.getEyeHeight(),
                player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * ticks, level);
        // Only shaderpacks need baked light. Native terrain follows the source every frame on the GPU.
        HeldItemLight light = level == 0 || !ShaderModBridge.areShadersEnabled() ? HeldItemLight.NONE : new HeldItemLight(
                MathHelper.floor(player.posX), MathHelper.floor(player.posY + player.getEyeHeight()),
                MathHelper.floor(player.posZ), level);
        if (!light.equals(this.terrainHeldItemLight) && !light.closeEnoughForRebake(this.terrainHeldItemLight)) {
            HeldItemLight previous = this.terrainHeldItemLight;
            this.terrainHeldItemLight = light;
            previous.invalidate(this);
            light.invalidate(this);
        }

        Entity viewEntity = Objects.requireNonNull(Minecraft.getMinecraft().getRenderViewEntity(), "Client must have view entity");

        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * ticks;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * ticks + (double) viewEntity.getEyeHeight();
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * ticks;

        float pitch = viewEntity.rotationPitch;
        float yaw = viewEntity.rotationYaw;
        float fogDistance = ChunkShaderFogComponent.FOG_SERVICE.getFogCutoff();

        return new CameraState(x, y, z, pitch, yaw, fogDistance);
    }


    @Override
    protected int renderBlockEntityList(List<TileEntity> list, TileEntityRenderContext tileEntityRenderContext) {
        int pass = MinecraftForgeClient.getRenderPass();
        float partialTicks = tileEntityRenderContext.partialTicks;
        int rendered = 0;

        for (TileEntity tileEntity : list) {
            if(!tileEntity.shouldRenderInPass(pass))
                continue;

            try {
                tileEntityRenderContext.prepareRenderState.run();
                this.prepareBlockEntityLightmapCoordinates(tileEntity);
                TileEntityRendererDispatcher.instance.render(tileEntity, partialTicks, -1);
                rendered++;
            } catch(RuntimeException e) {
                if(tileEntity.isInvalid()) {
                    CeleritasVintage.logger().error("Suppressing crash from invalid tile entity", e);
                } else {
                    throw e;
                }
            }
        }

        return rendered;
    }

    @Override
    public int renderBlockEntities(TileEntityRenderContext tileEntityRenderContext) {
        int pass = MinecraftForgeClient.getRenderPass();
        TileEntityRendererDispatcher.instance.preDrawBatch();
        int rendered = super.renderBlockEntities(tileEntityRenderContext);
        tileEntityRenderContext.prepareRenderState.run();
        TileEntityRendererDispatcher.instance.drawBatch(pass);
        return rendered;
    }

    private void prepareBlockEntityLightmapCoordinates(TileEntity tileEntity) {
        if (tileEntity == null || tileEntity.getWorld() == null || tileEntity.getPos() == null) {
            return;
        }

        BlockPos pos = tileEntity.getPos();
        if (!tileEntity.getWorld().isBlockLoaded(pos, false)) {
            return;
        }

        int packedLight = tileEntity.getWorld().getCombinedLight(pos, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (packedLight & 0xFFFF),
                (float) (packedLight >> 16));
    }

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!CeleritasVintage.options().performance.useEntityCulling || this.renderSectionManager.isInShadowPass()) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        if (entity.isGlowing() || entity.getAlwaysRenderNameTagForRender()) {
            return true;
        }

        //? if <1.21.2
        AxisAlignedBB box = entity.getRenderBoundingBox();
        //? if >=1.21.2
        /*AABB box = renderer.getBoundingBoxForCulling(entity);*/

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private ChunkVertexType chooseVertexType() {
        if (!CeleritasVintage.options().performance.useCompactVertexFormat) {
            return ChunkMeshFormats.VANILLA_LIKE;
        }

        return ChunkMeshFormats.COMPACT;
    }
}
