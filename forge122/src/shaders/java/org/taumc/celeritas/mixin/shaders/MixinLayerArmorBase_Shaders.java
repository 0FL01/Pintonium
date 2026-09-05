package org.taumc.celeritas.mixin.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.pipeline.VintageIrisRenderingPipeline;
import net.irisshaders.iris.shadows.CommonShadowRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LayerArmorBase.class)
public class MixinLayerArmorBase_Shaders {
    @WrapMethod(method = "renderEnchantedGlint")
    private static void iris$armorGlint(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model,
            float limbSwing, float limbSwingAmount, float partialTicks, float age, float yaw, float pitch,
            float scale, Operation<Void> original) {
        // Enchantment is a surface overlay, not an additional shadow caster.
        if (CommonShadowRenderer.ACTIVE) return;
        var current = IrisCommon.getPipelineManager().getPipelineNullable();
        var pipeline = current instanceof VintageIrisRenderingPipeline vintage ? vintage : null;
        boolean active = pipeline != null && pipeline.beginVintageArmorGlint();
        try {
            original.call(renderer, entity, model, limbSwing, limbSwingAmount, partialTicks, age, yaw, pitch, scale);
        } finally {
            if (active) pipeline.endVintageArmorGlint();
        }
    }
}
