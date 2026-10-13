package com.rainbowyoyo.util;

/** Rainbow colours. */
public final class Rainbow {
    private Rainbow() {
    }

    /** RGB (0xRRGGBB) of a hue (0 to 1, wraps around) with the given saturation and brightness. */
    public static int hsv(double hue, double saturation, double value) {
        double h = (hue - Math.floor(hue)) * 6.0;
        int sector = (int) h % 6;
        double f = h - Math.floor(h);
        double p = value * (1 - saturation);
        double q = value * (1 - saturation * f);
        double t = value * (1 - saturation * (1 - f));
        double r;
        double g;
        double b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    /** Fully saturated, bright rainbow colour. */
    public static int rgb(double hue) {
        return hsv(hue, 1.0, 1.0);
    }

    private static int to255(double c) {
        return Math.max(0, Math.min(255, (int) Math.round(c * 255)));
    }
}
