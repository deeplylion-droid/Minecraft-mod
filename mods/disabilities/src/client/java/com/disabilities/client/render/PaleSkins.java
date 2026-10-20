package com.disabilities.client.render;

import com.disabilities.Disabilities;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Albinism: a whitened copy of the player's own skin. Skin turns pale pink, hair white, eyes
 * pink-red; clothes keep their colours.
 */
public final class PaleSkins {
    private PaleSkins() {
    }

    private static final Map<Identifier, Identifier> CACHE = new HashMap<>();
    private static final Set<Identifier> FAILED = new HashSet<>();
    private static final Map<Identifier, PlayerSkin> SKINS = new HashMap<>();
    /** Skins not downloaded yet: try again a bit later, not every frame. */
    private static final Map<Identifier, Long> RETRY = new HashMap<>();

    /** The pale version of this skin, or the skin itself while it is not available. */
    public static PlayerSkin pale(PlayerSkin skin) {
        Identifier source = skin.body().texturePath();
        Identifier pale = paleTexture(source);
        if (pale == null) {
            return skin;
        }
        PlayerSkin cached = SKINS.get(source);
        if (cached == null || cached.cape() != skin.cape() || cached.model() != skin.model()) {
            cached = new PlayerSkin(new ClientAsset.ResourceTexture(pale, pale), skin.cape(), skin.elytra(), skin.model(), skin.secure());
            SKINS.put(source, cached);
        }
        return cached;
    }

    private static Identifier paleTexture(Identifier source) {
        Identifier cached = CACHE.get(source);
        if (cached != null || FAILED.contains(source)) {
            return cached;
        }
        long now = System.currentTimeMillis();
        Long retry = RETRY.get(source);
        if (retry != null && now < retry) {
            return null;
        }
        NativeImage image = read(source);
        if (image == null) {
            RETRY.put(source, now + 1000L);
            return null;
        }
        RETRY.remove(source);
        try (NativeImage src = image) {
            NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
            int w = src.getWidth();
            int h = src.getHeight();
            // skins are 64 pixels wide (or a multiple): one skin pixel is w / 64 image pixels both ways
            float[] skin = skinColour(src, Math.max(1, w / 64));
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out.setPixel(x, y, whiten(src.getPixel(x, y), x * 64 / w, y * 64 / w, skin));
                }
            }
            String path = "pale/" + source.getNamespace() + "/" + source.getPath().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            Identifier id = Disabilities.id(path);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, out));
            CACHE.put(source, id);
            return id;
        } catch (RuntimeException e) {
            Disabilities.LOGGER.warn("Could not make a pale skin from {}", source, e);
            FAILED.add(source);
            return null;
        }
    }

    private static NativeImage read(Identifier source) {
        Minecraft mc = Minecraft.getInstance();
        try {
            AbstractTexture texture = mc.getTextureManager().getTexture(source);
            if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                NativeImage pixels = dynamic.getPixels();
                NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), true);
                copy.copyFrom(pixels);
                return copy;
            }
            Optional<Resource> resource = mc.getResourceManager().getResource(source);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    return NativeImage.read(in);
                }
            }
        } catch (Exception e) {
            Disabilities.LOGGER.debug("Skin {} not readable yet", source, e);
        }
        return null;
    }

    private static final int OTHER = 0;
    private static final int HEAD = 1;
    private static final int ARM = 2;

    /** Which part of the body a pixel of the 64x64 skin layout belongs to (old 64x32 skins have no left arm). */
    private static int region(int x, int y) {
        if (y < 16) {
            return HEAD;
        }
        boolean rightArm = x >= 40 && x < 56 && y >= 16 && y < 48;
        boolean leftArm = x >= 32 && y >= 48;
        return rightArm || leftArm ? ARM : OTHER;
    }

    /** Where the eyes usually are: the middle of the face, around row 12. */
    private static boolean eye(int x, int y) {
        return x >= 9 && x <= 14 && y >= 11 && y <= 13;
    }

    /** The lower middle of the face: nose and mouth. */
    private static boolean face(int x, int y) {
        return x >= 9 && x <= 14 && y >= 12 && y <= 15;
    }

    /** The skin's own colour: the most common colour of the lower face (r, g, b, luminance), or null. */
    private static float[] skinColour(NativeImage src, int scale) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = -1;
        int bestCount = 0;
        for (int y = 12; y < 15; y++) {
            for (int x = 9; x < 15; x++) {
                int argb = src.getPixel(x * scale, y * scale);
                if (argb >>> 24 < 128) {
                    continue;
                }
                int bin = argb & 0xE0E0E0;
                int n = counts.merge(bin, 1, Integer::sum);
                if (n > bestCount) {
                    bestCount = n;
                    best = bin;
                }
            }
        }
        if (best < 0) {
            return null;
        }
        float r = 0;
        float g = 0;
        float b = 0;
        int n = 0;
        for (int y = 12; y < 15; y++) {
            for (int x = 9; x < 15; x++) {
                int argb = src.getPixel(x * scale, y * scale);
                if (argb >>> 24 >= 128 && (argb & 0xE0E0E0) == best) {
                    r += (argb >> 16 & 255) / 255.0F;
                    g += (argb >> 8 & 255) / 255.0F;
                    b += (argb & 255) / 255.0F;
                    n++;
                }
            }
        }
        r /= n;
        g /= n;
        b /= n;
        return new float[]{r, g, b, 0.299F * r + 0.587F * g + 0.114F * b};
    }

    /**
     * ARGB in, ARGB out. Skin (anything close to the face's colour, on the head and the arms) turns
     * pale pink, keeping its shading, and so do nose and mouth; the eyes turn pink-red; the rest of
     * the head (hair, eyebrows, beard) turns white; clothes keep their colours.
     */
    private static int whiten(int argb, int x, int y, float[] skin) {
        int a = argb >>> 24;
        int region = region(x, y);
        if (a == 0 || region == OTHER) {
            return argb;
        }
        float r = (argb >> 16 & 255) / 255.0F;
        float g = (argb >> 8 & 255) / 255.0F;
        float b = (argb & 255) / 255.0F;
        float lum = 0.299F * r + 0.587F * g + 0.114F * b;
        if (skin != null) {
            float dr = r - skin[0];
            float dg = g - skin[1];
            float db = b - skin[2];
            float distance = (float) Math.sqrt(dr * dr + dg * dg + db * db);
            if (distance < (region == HEAD ? 0.2F : 0.17F)) {
                return paleSkin(a, lum, skin, 0.7F);
            }
        }
        if (region == ARM) {
            return argb;
        }
        if (skin != null && eye(x, y)) {
            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            if (y == 12 && lum > 0.7F && max - min < 0.15F) {
                // the white of the eye
                return colour(a, 0.97F, 0.96F, 0.96F);
            }
            if (y == 12 || r < g || r < b) {
                float shade = 0.8F + 0.2F * lum;
                return colour(a, 0.9F * shade, 0.34F * shade, 0.42F * shade);
            }
        }
        if (skin != null && face(x, y) && r > b && lum >= skin[3] * 0.4F) {
            return paleSkin(a, lum, skin, 0.6F);
        }
        float shade = 0.8F + 0.2F * lum;
        return colour(a, shade, 0.98F * shade, 0.91F * shade);
    }

    private static int paleSkin(int a, float lum, float[] skin, float darkest) {
        float shade = Math.max(darkest, Math.min(1.0F, 0.92F + (lum - skin[3]) * 0.8F));
        return colour(a, shade, 0.87F * shade, 0.84F * shade);
    }

    private static int colour(int a, float r, float g, float b) {
        return a << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0F)));
    }

    public static void clear() {
        CACHE.clear();
        FAILED.clear();
        SKINS.clear();
        RETRY.clear();
    }
}
