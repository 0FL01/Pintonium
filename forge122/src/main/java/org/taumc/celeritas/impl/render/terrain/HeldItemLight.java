package org.taumc.celeritas.impl.render.terrain;

import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Immutable light source; render-time positions are interpolated on the client thread. */
public record HeldItemLight(double x, double y, double z, int level) {
    public static final HeldItemLight NONE = new HeldItemLight(0, 0, 0, 0);

    public static int lightValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem() == Items.LAVA_BUCKET) {
            return 15;
        }
        if (stack.getItem() instanceof ItemBlock blockItem) {
            return Math.max(0, Math.min(15, blockItem.getBlock()
                    .getStateFromMeta(blockItem.getMetadata(stack.getMetadata())).getLightValue()));
        }
        return 0;
    }

    public int apply(int blockX, int blockY, int blockZ, int packedLight) {
        return this.apply((double) blockX, (double) blockY, (double) blockZ, packedLight);
    }

    public int apply(double blockX, double blockY, double blockZ, int packedLight) {
        if (this.level == 0) {
            return packedLight;
        }
        double dx = blockX - this.x;
        double dy = blockY - this.y;
        double dz = blockZ - this.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared >= this.level * this.level) {
            return packedLight;
        }
        int light = (int) ((this.level - Math.sqrt(distanceSquared)) * 16);
        return (packedLight & 0xFFFF0000) | Math.max(packedLight & 0xFFFF, light);
    }

    public void invalidate(CeleritasWorldRenderer renderer) {
        if (this.level == 0) {
            return;
        }
        // Include the two-block halo used by terrain neighbor/AO sampling.
        int radius = this.level + 2;
        renderer.scheduleRebuildForBlockArea((int) Math.floor(this.x) - radius, Math.max(0, (int) Math.floor(this.y) - radius), (int) Math.floor(this.z) - radius,
                (int) Math.floor(this.x) + radius, Math.min(255, (int) Math.floor(this.y) + radius), (int) Math.floor(this.z) + radius, false);
    }
}
