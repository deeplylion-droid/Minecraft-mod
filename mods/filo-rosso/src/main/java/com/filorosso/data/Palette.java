package com.filorosso.data;

/** One colour per couple (the heart after the names): 8 couples, 8 colours. */
public final class Palette {
    private Palette() {
    }

    public static final int[] COLORS = {0xFF3050, 0xFF70C0, 0xB060FF, 0xFF9020, 0xFFD030, 0x30D0E0, 0x50E070, 0x5080FF};

    public static int rgb(int index) {
        return COLORS[Math.floorMod(index, COLORS.length)];
    }
}
