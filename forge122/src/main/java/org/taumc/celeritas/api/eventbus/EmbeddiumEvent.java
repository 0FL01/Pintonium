package org.taumc.celeritas.api.eventbus;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Binary-compatible event base used by Embeddium/Celeritas addons.
 */
public abstract class EmbeddiumEvent extends Event {
    @Override
    public boolean isCancelable() {
        return false;
    }
}
