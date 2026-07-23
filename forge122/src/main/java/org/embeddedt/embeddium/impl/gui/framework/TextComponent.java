package org.embeddedt.embeddium.impl.gui.framework;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

public interface TextComponent extends ITextComponent {
    static TextComponent literal(String text) {
        return new LiteralTextComponent(text);
    }

    static TextComponent translatable(String key, Object... args) {
        return new TranslatableTextComponent(key, args);
    }

    default TextComponent withStyle(TextFormattingStyle style, TextFormattingStyle... styles) {
        Style vanillaStyle = this.getStyle();
        if (vanillaStyle == null) {
            vanillaStyle = new Style();
        }

        applyStyle(vanillaStyle, style);
        for (TextFormattingStyle extraStyle : styles) {
            applyStyle(vanillaStyle, extraStyle);
        }

        this.setStyle(vanillaStyle);
        return this;
    }

    static void applyStyle(Style vanillaStyle, TextFormattingStyle style) {
        if (style != null) {
            vanillaStyle.setColor(style.asVanilla());
        }
    }

    class TranslatableTextComponent extends TextComponentTranslation implements TextComponent {
        public TranslatableTextComponent(String key, Object... args) {
            super(key, args);
        }
    }

    class LiteralTextComponent extends TextComponentString implements TextComponent {
        public LiteralTextComponent(String text) {
            super(text);
        }
    }
}
