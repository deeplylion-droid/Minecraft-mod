package com.infezione.skin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Turns a player's skin into the skin of an infected player at a stage of the infection (the "Cadavere"
 * look chosen by the user): the skin goes grey, black veins climb from a bite on the jaw, the eyes get
 * bloodshot and then milky, dark rings under them, blood on the mouth and on the clothes, torn clothes.
 *
 * <p>Pure Java on ARGB pixels (no Minecraft classes), so it can be tried on any skin outside the game.
 * Works on 64x64 skins and their HD multiples; anything else is returned unchanged. The random parts
 * (stains, tears) come from a seed, the same at every stage, so each stage only adds to the one before.</p>
 */
public final class SkinZombifier {
    /** Stage 5 is the zombie. */
    public static final int STAGES = 5;

    /** How far the skin has turned grey at each stage (0 = healthy). */
    private static final double[] GREY = {0, 0.18, 0.38, 0.6, 0.82, 1.0};
    /** Veins: pixel paths on the 8x8 face (x, y), shown a few at a time, stage by stage. */
    private static final int[][][] FACE_VEINS = {
            {{5, 7}, {5, 6}, {4, 6}, {4, 5}},          // from the bite towards the mouth
            {{7, 5}, {6, 5}, {7, 4}},                  // from the bite up to the eye
            {{3, 6}, {2, 7}, {1, 6}, {0, 5}},          // the other cheek
            {{3, 2}, {4, 2}, {2, 2}},                  // the forehead
    };
    private static final int[] VEIN_BUDGET = {0, 0, 2, 6, 10, 15};
    /** Veins on the backs of the hands (pixels of a 64x64 skin), two more at each stage from the third. */
    private static final int[][] HAND_VEINS = {{44, 30}, {45, 31}, {46, 30}, {36, 62}, {37, 61}, {38, 62}};
    /** The bite, on the jaw (face pixels): it is there from the first stage. */
    private static final int[][] BITE = {{6, 6}, {7, 6}, {6, 7}, {7, 7}};
    /** Blood from the mouth (face pixels), at stages 4 and 5. */
    private static final int[][] MOUTH_BLOOD = {{3, 7}, {4, 7}, {4, 6}};

    private static final int VEIN = 0x3A1F3D;
    private static final int RING = 0x4A3A48;
    private static final int BITE_LIGHT = 0x7A0C0C;
    private static final int BITE_DARK = 0x3D0505;
    private static final int BLOODSHOT = 0xD08080;
    private static final int MILKY = 0xE9E6D2;
    private static final int GREY_IRIS = 0x6A6A5A;
    private static final int MILKY_IRIS = 0xCFCAB4;
    private static final int BLOOD = 0x5C0A0A;
    private static final int MOUTH = 0x6E0B0B;

    /** Regions of a 64x64 skin: x0, y0, x1, y1. */
    private static final int[][] HEAD = {{0, 0, 32, 16}, {32, 0, 64, 16}};
    private static final int[][] ARMS = {{40, 16, 56, 32}, {40, 32, 56, 48}, {32, 48, 48, 64}, {48, 48, 64, 64}};
    private static final int[][] CLOTHES = {
            {16, 16, 40, 32}, {40, 16, 56, 32}, {0, 16, 16, 32}, {16, 48, 32, 64}, {32, 48, 48, 64},
            {16, 32, 40, 48}, {40, 32, 56, 48}, {0, 32, 16, 48}, {0, 48, 16, 64}, {48, 48, 64, 64}};
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;

    /** The infected skin, and what of it glows in the dark (transparent elsewhere). */
    public record Result(int[] skin, int[] glow) {
    }

    private SkinZombifier() {
    }

    /**
     * @param argb   the healthy skin, {@code width * height} ARGB pixels, row by row
     * @param stage  0 (healthy, unchanged) to 5 (zombie)
     * @param seed   the same for the same player at every stage (stains and tears stay where they were)
     */
    public static Result zombify(int[] argb, int width, int height, int stage, long seed) {
        int[] out = argb.clone();
        int[] glow = new int[argb.length];
        int s = width / 64;
        if (stage <= 0 || s < 1 || width != 64 * s || height != width || argb.length != width * height) {
            return new Result(out, glow);
        }
        stage = Math.min(stage, STAGES);
        Skin skin = new Skin(argb, width, s);

        // 1. the skin goes grey
        double k = GREY[stage];
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (skin.isSkin[i]) {
                    out[i] = withAlpha(argb[i], mix(argb[i] & 0xFFFFFF, grey(argb[i]), k));
                }
            }
        }
        // 2. dark rings under the eyes
        if (stage >= 3) {
            for (int x = 1; x <= 6; x++) {
                paintFace(out, skin, x, 5, RING, 0.35 + 0.1 * stage, true);
            }
        }
        // 3. veins from the bite, then on the hands
        int budget = VEIN_BUDGET[stage];
        int n = 0;
        for (int[][] path : FACE_VEINS) {
            for (int[] p : path) {
                if (n++ >= budget) {
                    break;
                }
                paintFace(out, skin, p[0], p[1], VEIN, 0.55 + 0.09 * stage, true);
            }
        }
        if (stage >= 3) {
            for (int v = 0; v < Math.min(HAND_VEINS.length, 2 * (stage - 2)); v++) {
                paint(out, skin, HAND_VEINS[v][0], HAND_VEINS[v][1], VEIN, 0.55 + 0.09 * stage, true);
            }
        }
        // 4. the bite
        for (int[] p : BITE) {
            paintFace(out, skin, p[0], p[1], (p[0] + p[1]) % 2 == 1 ? BITE_LIGHT : BITE_DARK, 1.0, false);
        }
        // 5. the eyes: bloodshot, then milky (they glow in the dark from stage 4)
        if (stage >= 2) {
            for (int y = FACE_Y + 3; y <= FACE_Y + 4; y++) {
                for (int x = FACE_X; x < FACE_X + 8; x++) {
                    int cell = skin.cell(x, y);
                    if (skin.sclera[cell]) {
                        int c = stage >= 4 ? MILKY : BLOODSHOT;
                        fill(out, skin, x, y, c, 1.0);
                        if (stage >= 4) {
                            fill(glow, skin, x, y, stage == 5 ? MILKY : 0xC4C0AA, 1.0);
                        }
                    } else if (skin.pupil[cell] && stage >= 4) {
                        int c = stage == 5 ? MILKY_IRIS : GREY_IRIS;
                        fill(out, skin, x, y, c, 1.0);
                        fill(glow, skin, x, y, stage == 5 ? MILKY_IRIS : 0x8A887A, 1.0);
                    }
                }
            }
        }
        // 6. blood on the clothes, more at each stage
        Random random = new Random(seed);
        List<int[]> cloth = skin.clothCells();
        if (!cloth.isEmpty()) {
            int stains = 3 * Math.max(0, stage - 1);
            for (int b = 0; b < 3 * (STAGES - 1); b++) {
                int[] at = cloth.get(random.nextInt(cloth.size()));
                int size = 2 + random.nextInt(3);
                if (b >= stains) {
                    continue;
                }
                int[][] blot = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
                for (int j = 0; j < size; j++) {
                    int x = at[0] + blot[j][0];
                    int y = at[1] + blot[j][1];
                    if (x < 64 && y < 64 && skin.isCloth[skin.cell(x, y)]) {
                        fill(out, skin, x, y, BLOOD, 0.85);
                    }
                }
            }
        }
        // 7. blood from the mouth
        if (stage >= 4) {
            for (int m = 0; m < (stage == 5 ? MOUTH_BLOOD.length : 1); m++) {
                paintFace(out, skin, MOUTH_BLOOD[m][0], MOUTH_BLOOD[m][1], MOUTH, 1.0, false);
            }
        }
        // 8. torn clothes, with grey skin showing through
        if (stage >= 3) {
            Random tears = new Random(seed * 31 + 7);
            double threshold = 1 - 0.03 * (stage - 2);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    double noise = tears.nextDouble();
                    if (noise > threshold && skin.isCloth[skin.cell(x, y)]) {
                        int c = skin.average(x, y);
                        int g = grey(c);
                        fill(out, skin, x, y, scale(g, 0.75), 1.0);
                    }
                }
            }
        }
        return new Result(out, glow);
    }

    // =====================================================================================
    // Looking at the skin
    // =====================================================================================

    /** What every cell of the 64x64 grid is (a cell is s x s pixels of an HD skin). */
    private static final class Skin {
        final int[] argb;
        final int width;
        final int s;
        final boolean[] isSkin;
        final boolean[] isCloth = new boolean[64 * 64];
        final boolean[] sclera = new boolean[64 * 64];
        final boolean[] pupil = new boolean[64 * 64];

        Skin(int[] argb, int width, int s) {
            this.argb = argb;
            this.width = width;
            this.s = s;
            this.isSkin = new boolean[argb.length];
            float[] reference = referenceSkin();
            for (int i = 0; i < argb.length; i++) {
                if (reference == null) {
                    isSkin[i] = skinTone(argb[i]);
                } else {
                    // clothes are usually on the body and the legs: there only a closer colour counts as skin
                    int x = (i % width) / s;
                    int y = (i / width) / s;
                    isSkin[i] = closeTo(argb[i], reference, !inside(HEAD, x, y) && !inside(ARMS, x, y));
                }
            }
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int c = average(x, y);
                    boolean opaque = (c >>> 24) > 127;
                    if (opaque && !inside(HEAD, x, y) && inside(CLOTHES, x, y) && !isSkin[(y * s) * width + x * s]) {
                        isCloth[cell(x, y)] = true;
                    }
                    if (opaque && x >= FACE_X && x < FACE_X + 8 && y >= FACE_Y + 3 && y <= FACE_Y + 4) {
                        float[] hsv = hsv(c);
                        sclera[cell(x, y)] = hsv[2] > 0.92F && hsv[1] < 0.12F;
                        pupil[cell(x, y)] = hsv[2] < 0.22F;
                    }
                }
            }
        }

        /**
         * The colour of this player's skin, as HSV: the commonest skin-like colour of the lower face (under the
         * eyes) and of the hands, any tone from very pale to very dark. Null if there is none to be seen there
         * (a mask, a helmet...): then a warm, not too saturated colour counts as skin.
         */
        float[] referenceSkin() {
            // the cheeks and the chin count three times, the backs of the hands once (they may be gloves)
            int[][] areas = {{9, 13, 15, 16}, {9, 13, 15, 16}, {9, 13, 15, 16}, {44, 29, 48, 32}, {36, 61, 40, 64}};
            Map<Integer, List<float[]>> buckets = new HashMap<>();
            for (int[] a : areas) {
                for (int y = a[1]; y < a[3]; y++) {
                    for (int x = a[0]; x < a[2]; x++) {
                        int c = average(x, y);
                        if ((c >>> 24) <= 127) {
                            continue;
                        }
                        float[] hsv = hsv(c);
                        float hue = hsv[0] * 360.0F;
                        boolean warm = hue <= 55.0F || hue >= 340.0F;
                        if (!warm || hsv[1] < 0.08F || hsv[1] > 0.9F || hsv[2] < 0.1F) {
                            continue;
                        }
                        int key = ((int) (((hue + 20.0F) % 360.0F) / 12.0F)) * 10000 + (int) (hsv[1] / 0.15F) * 100 + (int) (hsv[2] / 0.15F);
                        buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(hsv);
                    }
                }
            }
            List<float[]> best = null;
            for (List<float[]> list : buckets.values()) {
                if (best == null || list.size() > best.size()) {
                    best = list;
                }
            }
            if (best == null || best.size() < 4) {
                return null;
            }
            float hx = 0;
            float hy = 0;
            float sat = 0;
            float val = 0;
            for (float[] hsv : best) {
                hx += (float) Math.cos(hsv[0] * Math.PI * 2);
                hy += (float) Math.sin(hsv[0] * Math.PI * 2);
                sat += hsv[1];
                val += hsv[2];
            }
            float hue = (float) (Math.atan2(hy, hx) / (Math.PI * 2));
            return new float[]{hue < 0 ? hue + 1 : hue, sat / best.size(), val / best.size()};
        }

        int cell(int x, int y) {
            return y * 64 + x;
        }

        /** The colour of a cell (its top-left pixel: HD skins are drawn in blocks anyway). */
        int average(int x, int y) {
            return argb[(y * s) * width + x * s];
        }

        List<int[]> clothCells() {
            List<int[]> list = new ArrayList<>();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    if (isCloth[cell(x, y)]) {
                        list.add(new int[]{x, y});
                    }
                }
            }
            return list;
        }
    }

    private static boolean inside(int[][] rects, int x, int y) {
        for (int[] r : rects) {
            if (x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) {
                return true;
            }
        }
        return false;
    }

    /** A colour close enough to the player's skin to be skin too (lighter or darker with the shading). */
    static boolean closeTo(int argb, float[] reference, boolean strict) {
        if ((argb >>> 24) <= 127) {
            return false;
        }
        float[] hsv = hsv(argb);
        float ratio = hsv[2] / Math.max(0.01F, reference[2]);
        if (ratio < 0.55F || ratio > 1.4F || Math.abs(hsv[1] - reference[1]) > (strict ? 0.12F : 0.2F)) {
            return false;
        }
        if (reference[1] < 0.12F) {
            return hsv[1] < 0.2F;
        }
        float dh = Math.abs(hsv[0] - reference[0]) * 360.0F;
        return Math.min(dh, 360.0F - dh) <= (strict ? 9.0F : 15.0F);
    }

    /** A warm, not too saturated colour that is not gold or yellow (blue at least 62% of green). */
    static boolean skinTone(int argb) {
        if ((argb >>> 24) <= 127) {
            return false;
        }
        float[] hsv = hsv(argb);
        float hue = hsv[0] * 360.0F;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return hue >= 5 && hue <= 45 && hsv[1] >= 0.1F && hsv[1] <= 0.65F && hsv[2] >= 0.35F && b >= 0.62 * g;
    }

    private static float[] hsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = ((g - b) / d) % 6;
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    // =====================================================================================
    // Painting
    // =====================================================================================

    /** The grey of a corpse, as light as the colour it replaces. */
    private static int grey(int argb) {
        double l = (0.3 * ((argb >> 16) & 0xFF) + 0.59 * ((argb >> 8) & 0xFF) + 0.11 * (argb & 0xFF)) / 255.0;
        return rgb(0.42 + 0.40 * l, 0.46 + 0.40 * l, 0.45 + 0.40 * l);
    }

    private static void paintFace(int[] out, Skin skin, int fx, int fy, int rgb, double k, boolean skinOnly) {
        paint(out, skin, FACE_X + fx, FACE_Y + fy, rgb, k, skinOnly);
    }

    /** Paints a cell (s x s pixels), mixing in {@code rgb} by {@code k}; with {@code skinOnly}, only its skin pixels. */
    private static void paint(int[] out, Skin skin, int x, int y, int rgb, double k, boolean skinOnly) {
        for (int dy = 0; dy < skin.s; dy++) {
            for (int dx = 0; dx < skin.s; dx++) {
                int i = (y * skin.s + dy) * skin.width + x * skin.s + dx;
                if (skinOnly && !skin.isSkin[i]) {
                    continue;
                }
                if (!skinOnly && (skin.argb[i] >>> 24) <= 127) {
                    out[i] = 0xFF000000 | rgb;
                    continue;
                }
                out[i] = withAlpha(out[i], mix(out[i] & 0xFFFFFF, rgb, k));
            }
        }
    }

    private static void fill(int[] out, Skin skin, int x, int y, int rgb, double k) {
        for (int dy = 0; dy < skin.s; dy++) {
            for (int dx = 0; dx < skin.s; dx++) {
                int i = (y * skin.s + dy) * skin.width + x * skin.s + dx;
                out[i] = 0xFF000000 | mix(out[i] & 0xFFFFFF, rgb, k);
            }
        }
    }

    private static int withAlpha(int original, int rgb) {
        return (original & 0xFF000000) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, double k) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = (int) Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int rgb, double k) {
        return rgb(((rgb >> 16) & 0xFF) / 255.0 * k, ((rgb >> 8) & 0xFF) / 255.0 * k, (rgb & 0xFF) / 255.0 * k);
    }

    private static int rgb(double r, double g, double b) {
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
