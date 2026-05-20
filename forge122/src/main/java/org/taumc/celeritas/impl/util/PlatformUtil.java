package org.taumc.celeritas.impl.util;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

import java.io.File;

public class PlatformUtil {

    public static boolean isLoadValid() {
        //TODO Implement. Doesn't seem to be used outside of shaders
        return true;
    }

    public static boolean modPresent(String modid) {
        try {
            return Loader.isModLoaded(modid);
        } catch (RuntimeException e) {
            return Loader.instance().getIndexedModList().containsKey(modid);
        }
    }

    public static String getModName(String modId) {
        return Loader.instance().getIndexedModList().get(modId).getName();
    }

    public static File getConfigDir() {
        File configDir = Loader.instance().getConfigDir();
        return configDir != null ? configDir : new File(getInjectedGameDir(), "config");
    }

    public static File getGameDir() {
        File injectedGameDir = getInjectedGameDir();
        if (injectedGameDir != null) {
            return injectedGameDir;
        }

        Minecraft client = Minecraft.getMinecraft();
        if (client != null && client.gameDir != null) {
            return client.gameDir;
        }

        return new File(".");
    }

    private static File getInjectedGameDir() {
        Object[] injectionData = FMLInjectionData.data();
        if (injectionData.length > 6 && injectionData[6] instanceof File) {
            return (File) injectionData[6];
        }

        return null;
    }
}
