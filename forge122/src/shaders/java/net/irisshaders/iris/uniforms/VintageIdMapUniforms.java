package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.taumc.celeritas.impl.render.terrain.HeldItemLight;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class VintageIdMapUniforms implements IdMapUniforms {
    @Override
    public void addIdMapUniforms(FrameUpdateNotifier notifier, UniformHolder uniforms, IdMap idMap, boolean isOldHandLight) {
        uniforms.uniform1i(PER_FRAME, "heldItemId", () -> itemId(idMap, EnumHand.MAIN_HAND));
        uniforms.uniform1i(PER_FRAME, "heldItemId2", () -> itemId(idMap, EnumHand.OFF_HAND));
        uniforms.uniform1i(PER_FRAME, "heldBlockLightValue", () -> isOldHandLight
                ? Math.max(lightValue(EnumHand.MAIN_HAND), lightValue(EnumHand.OFF_HAND))
                : lightValue(EnumHand.MAIN_HAND));
        uniforms.uniform1i(PER_FRAME, "heldBlockLightValue2", () -> lightValue(EnumHand.OFF_HAND));
    }

    private static ItemStack heldStack(EnumHand hand) {
        var player = Minecraft.getMinecraft().player;
        return player == null ? ItemStack.EMPTY : player.getHeldItem(hand);
    }

    private static int itemId(IdMap idMap, EnumHand hand) {
        ItemStack stack = heldStack(hand);
        if (stack.isEmpty()) {
            return -1;
        }
        var name = stack.getItem().getRegistryName();
        return name == null ? -1 : idMap.getItemIdMap().getInt(
                new NamespacedId(name.getNamespace(), name.getPath()));
    }

    private static int lightValue(EnumHand hand) {
        return HeldItemLight.lightValue(heldStack(hand));
    }
}
