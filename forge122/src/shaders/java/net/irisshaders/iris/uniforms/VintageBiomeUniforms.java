package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;

public class VintageBiomeUniforms implements BiomeUniforms {
    @Override
    public void addBiomeUniforms(UniformHolder uniforms) {
        uniforms
                .uniform1i(PER_TICK, "biome", playerI(player -> Biome.getIdForBiome(currentBiome(player))))
                .uniform1i(PER_TICK, "biome_category", playerI(player -> currentBiome(player).getTempCategory().ordinal()))
                .uniform1i(PER_TICK, "biome_precipitation", playerI(player -> {
                    Biome biome = currentBiome(player);
                    if (biome.isSnowyBiome()) {
                        return 2;
                    }
                    return biome.canRain() ? 1 : 0;
                }))
                .uniform1f(PER_TICK, "rainfall", playerF(player -> currentBiome(player).getRainfall()))
                .uniform1f(PER_TICK, "temperature", playerF(player -> currentBiome(player).getTemperature(player.getPosition())));
    }

    private static Biome currentBiome(EntityPlayerSP player) {
        BlockPos pos = player.getPosition();
        return player.world.getBiome(pos);
    }

    private static IntSupplier playerI(ToIntFunction<EntityPlayerSP> function) {
        return () -> {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            return player == null ? 0 : function.applyAsInt(player);
        };
    }

    private static FloatSupplier playerF(ToFloatFunction<EntityPlayerSP> function) {
        return () -> {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            return player == null ? 0.0f : function.applyAsFloat(player);
        };
    }

    @FunctionalInterface
    private interface ToFloatFunction<T> {
        float applyAsFloat(T value);
    }
}
