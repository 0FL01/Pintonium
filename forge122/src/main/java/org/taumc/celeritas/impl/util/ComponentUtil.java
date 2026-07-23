package org.taumc.celeritas.impl.util;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

public class ComponentUtil {

    public static TextComponent empty() {
        return TextComponent.literal("");
    }

    public static TextComponent literal(String text) {
        return TextComponent.literal(text);
    }

    public static TextComponent translatable(String key, Object... args) {
        return TextComponent.translatable(key, args);
    }
}
