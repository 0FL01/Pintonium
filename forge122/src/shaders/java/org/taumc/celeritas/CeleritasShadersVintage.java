package org.taumc.celeritas;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = CeleritasShadersVintage.MODID, name = "Pintonium Shaders", clientSideOnly = true)
public class CeleritasShadersVintage {
    public static final String MODID = "celeritas_shaders";
    private static final Logger LOGGER = LogManager.getLogger("Pintonium-Shaders");

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        LOGGER.info("Loaded the Pintonium 1.12.2 shader bridge.");
    }
}
