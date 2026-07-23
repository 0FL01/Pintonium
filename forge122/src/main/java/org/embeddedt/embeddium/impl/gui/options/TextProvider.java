package org.embeddedt.embeddium.impl.gui.options;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

public interface TextProvider extends org.taumc.celeritas.impl.gui.options.TextProvider {
    @Override
    TextComponent getLocalizedName();
}
