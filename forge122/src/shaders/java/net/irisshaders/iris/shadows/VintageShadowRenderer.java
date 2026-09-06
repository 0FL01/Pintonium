package net.irisshaders.iris.shadows;

import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.layer.GbufferPrograms;
import net.irisshaders.iris.pipeline.VintageIrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.materialmap.VintageWorldRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.ShadowCullState;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.CommonFrustum;
import net.irisshaders.iris.shadows.frustum.CommonFrustumHolder;
import net.irisshaders.iris.shadows.frustum.CullEverythingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.embeddedt.embeddium.compat.mc.MCCamera;
import org.embeddedt.embeddium.compat.mc.MCLevelRenderer;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.taumc.celeritas.impl.render.GlMatrixSnapshot;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;
import org.taumc.celeritas.mixin.shaders.accessor.MixinRenderManagerShadowAccessor;

import java.util.ArrayList;
import java.util.List;

/** Legacy fixed-function casters and Celeritas terrain share the pack's shadow targets. */
public final class VintageShadowRenderer extends CommonShadowRenderer {
    private final VintageIrisRenderingPipeline pipeline;
    private final ShadowCompositeRenderer composite;
    private final GlFramebuffer framebuffer;
    private final BlendModeOverride blend;
    private final AlphaTest alphaTest;
    private final List<BufferBlendOverride> bufferBlends = new ArrayList<>();
    private Program casterProgram;
    private int entityAttribute = -1;
    private int blockEntityUniform = -1;
    private int frame;
    private static final ResourceLocation PLAYER_KEY = new ResourceLocation("minecraft", "player");
    private final List<TileEntity> shadowTiles = new ArrayList<>();
    private WorldRenderingPhase lastCasterPhase;
    private int lastCasterEntity;
    private int lastCasterBlockEntity;
    private boolean casterStatePushed;

    public VintageShadowRenderer(VintageIrisRenderingPipeline pipeline, ProgramSource source, PackDirectives directives,
                                 ShadowRenderTargets targets, ShadowCompositeRenderer composite, boolean separateSamplers) {
        super(source, directives, targets, separateSamplers);
        this.pipeline = pipeline;
        this.composite = composite;
        int[] buffers = source == null || source.getDirectives().hasUnknownDrawBuffers()
                ? new int[]{0, 1} : source.getDirectives().getDrawBuffers();
        this.framebuffer = targets.createFramebufferWritingToMain(buffers);
        this.blend = source == null ? BlendModeOverride.OFF
                : source.getDirectives().getBlendModeOverride().orElse(ProgramId.Shadow.getBlendModeOverride());
        this.alphaTest = source == null ? AlphaTests.ONE_TENTH_ALPHA
                : source.getDirectives().getAlphaTestOverride().orElse(AlphaTests.ONE_TENTH_ALPHA);
        if (source != null) {
            source.getDirectives().getBufferBlendOverrides().forEach(info -> {
                for (int i = 0; i < buffers.length; i++) {
                    if (buffers[i] == info.index()) bufferBlends.add(new BufferBlendOverride(i, info.blendMode()));
                }
            });
            casterProgram = pipeline.createVintageShadowProgram(source);
            entityAttribute = GL20.glGetAttribLocation(casterProgram.getProgramId(), "mc_Entity");
            blockEntityUniform = GL20.glGetUniformLocation(casterProgram.getProgramId(), "blockEntityId");
            packHasVoxelization |= casterProgram.getActiveImages() > 0;
        }
        configureSamplingSettings(directives.getShadowDirectives());
    }

    @Override
    protected void initFrustumHolders() {
        terrainFrustumHolder = new CommonFrustumHolder();
        entityFrustumHolder = new CommonFrustumHolder();
    }

    private CommonFrustum createFrustum(float multiplier, CommonFrustumHolder holder) {
        double maxDistance = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0;
        boolean reversed = packCullingState == ShadowCullState.REVERSED;
        if (reversed && multiplier < 0) multiplier = 1;
        double distance = multiplier < 0 ? IrisVideoSettings.shadowDistance * 16.0
                : (reversed ? voxelDistance : halfPlaneLength) * multiplier;
        boolean distanceOnly = packCullingState == ShadowCullState.DISTANCE
                || (packCullingState == ShadowCullState.DEFAULT && packHasVoxelization);
        if (distanceOnly && distance <= 0) distance = maxDistance;
        distance = Math.min(distance, maxDistance);
        BoxCuller box = new BoxCuller(distance);
        CommonFrustum frustum;
        if (distance == 0 && !reversed) {
            frustum = new CullEverythingFrustum();
        } else if (distanceOnly) {
            frustum = distance >= maxDistance ? new NonCullingFrustum() : new BoxCullingFrustum(box);
        } else {
            var light = new CelestialUniforms(sunPathRotation).getShadowLightPositionInWorldSpace();
            var direction = new Vector3f(light.x, light.y, light.z).normalize();
            var state = CapturedRenderingState.INSTANCE;
            frustum = reversed
                    ? new ReversedAdvancedShadowCullingFrustum(state.getGbufferModelView(), state.getGbufferProjection(),
                            direction, box, new BoxCuller(halfPlaneLength * (multiplier < 0 ? 1 : multiplier)))
                    : new AdvancedShadowCullingFrustum(state.getGbufferModelView(), state.getGbufferProjection(), direction, box);
        }
        holder.setInfo(frustum, distance + " blocks", distanceOnly ? "distance" : reversed ? "reversed" : "advanced");
        return frustum;
    }

    public static Matrix4f createModelView(float sunPathRotation, float intervalSize) {
        Vector3d camera = CameraUniforms.getUnshiftedCameraPosition();
        float skyAngle = getShadowAngle();
        skyAngle = skyAngle < 0.25f ? skyAngle + 0.75f : skyAngle - 0.25f;
        Matrix4f matrix = new Matrix4f().translate(0, 0, -100).rotateX((float) Math.toRadians(90))
                .rotateZ((float) Math.toRadians(-360 * skyAngle)).rotateX((float) Math.toRadians(sunPathRotation));
        if (intervalSize != 0) {
            matrix.translate((float) camera.x % intervalSize - intervalSize / 2,
                    (float) camera.y % intervalSize - intervalSize / 2,
                    (float) camera.z % intervalSize - intervalSize / 2);
        }
        return matrix;
    }

    public Matrix4f createProjection() {
        float clipDistance = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
        return fov != null ? new Matrix4f().perspective((float) Math.toRadians(fov), 1, 0.05f, 256)
                : new Matrix4f().ortho(-halfPlaneLength, halfPlaneLength, -halfPlaneLength, halfPlaneLength,
                        nearPlane < 0 ? -clipDistance : nearPlane, farPlane < 0 ? clipDistance : farPlane);
    }

    @Override
    public void renderShadows(MCLevelRenderer ignoredRenderer, MCCamera ignoredCamera) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null
                || IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance) == 0) return;
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.instance();
        Viewport playerViewport = renderer.getLastViewport();
        if (playerViewport == null) return;
        var state = CapturedRenderingState.INSTANCE;
        WorldRenderingPhase previousPhase = pipeline.getPhase();
        int previousPass = MinecraftForgeClient.getRenderPass();
        int previousEntity = state.getCurrentRenderedEntity();
        int previousBlockEntity = state.getCurrentRenderedBlockEntity();
        int previousItem = state.getCurrentRenderedItem();
        float previousAlpha = state.getCurrentAlphaTest();
        boolean previousActive = ACTIVE;
        boolean previousShadow = GlMatrixSnapshot.isRenderingShadowPass();
        float ticks = state.getTickDelta();
        Vector3d camera = CameraUniforms.getUnshiftedCameraPosition();
        var dispatcher = mc.getRenderManager();
        var dispatcherAccess = (MixinRenderManagerShadowAccessor) dispatcher;
        double oldRenderX = dispatcherAccess.iris$getRenderPosX();
        double oldRenderY = dispatcherAccess.iris$getRenderPosY();
        double oldRenderZ = dispatcherAccess.iris$getRenderPosZ();
        boolean oldDebugBoxes = dispatcher.isDebugBoundingBox();
        double oldTileX = TileEntityRendererDispatcher.staticPlayerX;
        double oldTileY = TileEntityRendererDispatcher.staticPlayerY;
        double oldTileZ = TileEntityRendererDispatcher.staticPlayerZ;
        boolean oldEntityShadows = dispatcher.isRenderShadow();
        // Close the snapshot before releasing its scratch memory, including exceptional exits.
        try (MemoryStack stack = MemoryStack.stackPush(); VintageShadowState ignored = new VintageShadowState(stack)) {
            ACTIVE = true;
            GlMatrixSnapshot.setRenderingShadowPass(true);
            renderDistance = renderDistanceMultiplier < 0 ? IrisVideoSettings.shadowDistance
                    : (int) Math.ceil(halfPlaneLength * renderDistanceMultiplier / 16);
            MODELVIEW = createModelView(sunPathRotation, intervalSize);
            PROJECTION = createProjection();
            new GlMatrixSnapshot(PROJECTION, MODELVIEW).restore();
            var terrainFrustum = createFrustum(renderDistanceMultiplier, terrainFrustumHolder);
            terrainFrustum.prepare(camera.x, camera.y, camera.z);
            Viewport terrainViewport = terrainFrustum.sodium$createViewport();
            renderer.setCurrentViewport(terrainViewport);
            RenderDevice.enterManagedCode();
            try {
                renderer.getRenderSectionManager().updateShadowVisibility(terrainViewport, ++frame);
            } finally {
                RenderDevice.exitManagedCode();
            }
            GL11.glViewport(0, 0, resolution, resolution);
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.colorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (shouldRenderTerrain) {
                drawTerrain(mc, BlockRenderLayer.SOLID, ticks);
                drawTerrain(mc, BlockRenderLayer.CUTOUT_MIPPED, ticks);
                drawTerrain(mc, BlockRenderLayer.CUTOUT, ticks);
            }
            var entityFrustum = entityShadowDistanceMultiplier < 0 || entityShadowDistanceMultiplier == 1
                    ? terrainFrustum : createFrustum(renderDistanceMultiplier < 0
                            ? IrisVideoSettings.shadowDistance * 16 / halfPlaneLength * entityShadowDistanceMultiplier
                            : renderDistanceMultiplier * entityShadowDistanceMultiplier, entityFrustumHolder);
            if (entityFrustum == terrainFrustum) {
                entityFrustumHolder.setInfo(terrainFrustum, terrainFrustumHolder.getDistanceInfo(), terrainFrustumHolder.getCullingInfo());
            }
            entityFrustum.prepare(camera.x, camera.y, camera.z);
            Viewport entityViewport = entityFrustum.sodium$createViewport();
            dispatcher.cacheActiveRenderInfo(mc.world, mc.fontRenderer, mc.getRenderViewEntity(), mc.pointedEntity, mc.gameSettings, ticks);
            dispatcher.setRenderPosition(camera.x, camera.y, camera.z);
            dispatcher.setRenderShadow(false);
            dispatcher.setDebugBoundingBox(false);
            TileEntityRendererDispatcher.instance.prepare(mc.world, mc.getTextureManager(), mc.fontRenderer,
                    mc.getRenderViewEntity(), mc.objectMouseOver, ticks);
            TileEntityRendererDispatcher.staticPlayerX = camera.x;
            TileEntityRendererDispatcher.staticPlayerY = camera.y;
            TileEntityRendererDispatcher.staticPlayerZ = camera.z;
            renderedShadowEntities = 0;
            renderedShadowBlockEntities = 0;
            casterStatePushed = false;
            List<Entity> entities = new ArrayList<>(mc.world.loadedEntityList);
            shadowTiles.clear();
            renderer.forEachVisibleBlockEntity(shadowTiles::add);
            // Both Forge entity passes precede the depth snapshot, just like modern buffered casters.
            for (int pass = 0; pass < 2; pass++) {
                ForgeHooksClient.setRenderPass(pass);
                pipeline.setPhase(WorldRenderingPhase.ENTITIES);
                for (Entity entity : entities) {
                    boolean player = entity == mc.player;
                    if (player ? !shouldRenderPlayer : !shouldRenderEntities) continue;
                    if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) continue;
                    if (!entity.shouldRenderInPass(pass) || !visible(entityViewport, entity.getRenderBoundingBox())) continue;
                    ResourceLocation key = player ? PLAYER_KEY : EntityList.getKey(entity);
                    var ids = WorldRenderingSettings.INSTANCE.getEntityIds();
                    int id = ids == null || key == null ? 0 : ids.getInt(new NamespacedId(key.getNamespace(), key.getPath()));
                    state.setCurrentEntity(id);
                    state.setCurrentBlockEntity(0);
                    state.setCurrentRenderedItem(0);
                    prepareCaster(mc, ticks, id);
                    dispatcher.renderEntityStatic(entity, ticks, false);
                    if (dispatcher.isRenderMultipass(entity)) {
                        prepareCaster(mc, ticks, id);
                        dispatcher.renderMultipass(entity, ticks);
                    }
                    renderedShadowEntities++;
                }
                pipeline.setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
                for (TileEntity tile : shadowTiles) {
                    if (tile.isInvalid() || !tile.shouldRenderInPass(pass)) continue;
                    if (TileEntityRendererDispatcher.instance.getRenderer(tile) == null) continue;
                    BlockPos tilePos = tile.getPos();
                    IBlockState tileState = mc.world.getBlockState(tilePos);
                    if (!shouldRenderBlockEntities && (!shouldRenderLightBlockEntities
                            || tileState.getLightValue(mc.world, tilePos) == 0)) continue;
                    if (!visible(entityViewport, tile.getRenderBoundingBox())) continue;
                    var blockIds = VintageWorldRenderingSettings.INSTANCE.getBlockStateIds();
                    int id = blockIds == null ? 0 : blockIds.getInt(tileState);
                    state.setCurrentEntity(0);
                    state.setCurrentBlockEntity(id);
                    prepareCaster(mc, ticks, id);
                    int light = mc.world.getCombinedLight(tilePos, 0);
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
                    // Flush fast TESRs individually: mc_Entity and blockEntityId are draw constants.
                    TileEntityRendererDispatcher.instance.preDrawBatch();
                    try {
                        TileEntityRendererDispatcher.instance.render(tile, tilePos.getX() - camera.x,
                                tilePos.getY() - camera.y, tilePos.getZ() - camera.z, ticks, -1, 1);
                        renderedShadowBlockEntities++;
                    } finally {
                        try {
                            prepareCaster(mc, ticks, id);
                        } finally {
                            TileEntityRendererDispatcher.instance.drawBatch(pass);
                        }
                    }
                }
            }
            state.setCurrentEntity(0);
            state.setCurrentBlockEntity(0);
            state.setCurrentRenderedItem(0);
            Program.unbind();
            BlendModeOverride.restore();
            targets.copyPreTranslucentDepth();
            if (shouldRenderTranslucent) drawTerrain(mc, BlockRenderLayer.TRANSLUCENT, ticks);
            debugStringTerrain = renderer.getVisibleChunkCount() + " sections";
            generateMipmaps();
            pipeline.removePhaseIfNeeded();
            composite.renderAll();
        } finally {
            // CPU selectors must be restored even when a mod renderer or shader throws.
            ACTIVE = previousActive;
            GlMatrixSnapshot.setRenderingShadowPass(previousShadow);
            renderer.setCurrentViewport(playerViewport);
            ForgeHooksClient.setRenderPass(previousPass);
            dispatcher.setRenderShadow(oldEntityShadows);
            dispatcher.setDebugBoundingBox(oldDebugBoxes);
            dispatcher.setRenderPosition(oldRenderX, oldRenderY, oldRenderZ);
            TileEntityRendererDispatcher.staticPlayerX = oldTileX;
            TileEntityRendererDispatcher.staticPlayerY = oldTileY;
            TileEntityRendererDispatcher.staticPlayerZ = oldTileZ;
            state.setCurrentEntity(previousEntity);
            state.setCurrentBlockEntity(previousBlockEntity);
            state.setCurrentRenderedItem(previousItem);
            state.setCurrentAlphaTest(previousAlpha);
            pipeline.setPhase(previousPhase);
            GbufferPrograms.runPhaseChangeNotifier();
        }
    }

    private static boolean visible(Viewport viewport, AxisAlignedBB box) {
        return box == TileEntity.INFINITE_EXTENT_AABB || viewport.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private void drawTerrain(Minecraft mc, BlockRenderLayer layer, float ticks) {
        framebuffer.bind();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.disableCull();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        mc.renderGlobal.renderBlockLayer(layer, ticks, 2, mc.getRenderViewEntity());
    }

    private void prepareCaster(Minecraft mc, float ticks, int id) {
        framebuffer.bind();
        GL11.glViewport(0, 0, resolution, resolution);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(alphaTest.function().getGlId(), alphaTest.reference());
        CapturedRenderingState.INSTANCE.setCurrentAlphaTest(alphaTest.reference());
        GlStateManager.color(1, 1, 1, 1);
        RenderHelper.disableStandardItemLighting();
        mc.entityRenderer.enableLightmap();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.03125f, 0.03125f, 0.03125f);
        GL11.glScalef(0.00390625f, 0.00390625f, 0.00390625f);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        if (blend != null) blend.apply();
        bufferBlends.forEach(BufferBlendOverride::apply);
        if (casterProgram != null) {
            casterProgram.use();
            // Full GL/framebuffer state above is restored per caster because entity
            // rendering clobbers it, but uniform uploads only depend on phase and ids.
            int blockEntity = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
            WorldRenderingPhase casterPhase = pipeline.getPhase();
            if (!casterStatePushed || casterPhase != lastCasterPhase || id != lastCasterEntity
                    || blockEntity != lastCasterBlockEntity) {
                casterStatePushed = true;
                lastCasterPhase = casterPhase;
                lastCasterEntity = id;
                lastCasterBlockEntity = blockEntity;
                GbufferPrograms.runFallbackEntityListener();
                GbufferPrograms.runPhaseChangeNotifier();
                if (blockEntityUniform >= 0) {
                    GL20.glUniform1i(blockEntityUniform, blockEntity);
                }
                pipeline.getCustomUniforms().push(casterProgram);
                if (entityAttribute >= 0) GL20.glVertexAttrib3f(entityAttribute, id, 0, 0);
            }
        } else {
            // A pack without shadow.vsh uses the real fixed-function depth path.
            Program.unbind();
        }
    }

    @Override protected String getEntitiesDebugString() { return Integer.toString(renderedShadowEntities); }
    @Override protected String getBlockEntitiesDebugString() { return Integer.toString(renderedShadowBlockEntities); }
    @Override protected void addBuffersDebugText(List<String> messages) { }

    @Override
    public void destroy() {
        if (casterProgram != null) casterProgram.delete();
        targets.destroy();
    }
}
