package com.filorosso.util;

import com.filorosso.FiloRosso;
import com.filorosso.data.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Small coloured icons drawn with the mod's own font (white glyphs, tinted by the text colour). */
public final class Text {
    private Text() {
    }

    public static final FontDescription ICONS = new FontDescription.Resource(FiloRosso.id("icons"));

    public static final String HEART = "";
    public static final String BROKEN = "";
    public static final String RING = "";
    public static final String STAR = "";
    public static final String SCISSORS = "";

    public static MutableComponent icon(String glyph, int rgb) {
        return Component.literal(glyph).withStyle(Style.EMPTY.withFont(ICONS).withColor(rgb).withBold(false).withItalic(false));
    }

    /** The couple's heart, in its colour. */
    public static MutableComponent heart(int colorIndex) {
        return icon(HEART, Palette.rgb(colorIndex));
    }

    public static MutableComponent names(String a, String b, int colorIndex) {
        return Component.literal(a).withStyle(ChatFormatting.WHITE).append(Component.literal(" ")).append(heart(colorIndex))
                .append(Component.literal(" ")).append(Component.literal(b).withStyle(ChatFormatting.WHITE));
    }
}
