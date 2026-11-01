package com.punizioni.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.punizioni.Punizioni;
import com.punizioni.data.Body;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The player's own skin with what the punishments left on it, painted at four times the skin's
 * resolution so the marks have edges and shading: lash welts across the back (fresh ones bright and
 * dripping, old ones dark scabs), rat bites all over, the charring of the stake. One texture per
 * player, redrawn only when something changes. Where marks go comes from the player's UUID, so every
 * client paints the same marks in the same places.
 */
public final class MarkedSkins {
    private MarkedSkins() {
    }

    private static final int UPSCALE = 4;
    /** Lashes stay fresh (bright red, dripping) this long. */
    private static final long FRESH_TICKS = 20 * 40;

    private record Key(Identifier base, int lashes, int bites, int burn, int fresh) {
    }

    private static final class Entry {
        Key key;
        Identifier id;
        DynamicTexture texture;
        PlayerSkin skin;
        PlayerSkin from;
    }

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static final Map<Identifier, Long> RETRY = new HashMap<>();
    private static final Map<Identifier, NativeImage> SOURCES = new HashMap<>();

    /** The marked version of this player's skin (the skin itself while the original cannot be read). */
    public static PlayerSkin marked(PlayerSkin skin, UUID player, Body body, long now) {
        Identifier base = skin.body().texturePath();
        long since = now - body.lastLash();
        int fresh = body.lastLash() <= 0L || since > FRESH_TICKS ? 0 : 1 + (int) Math.min(4L, since / 25L);
        Key key = new Key(base, body.lashes(), body.bites(), Math.round(body.burn() * 12.0F), fresh);
        Entry e = ENTRIES.get(player);
        if (e != null && e.key.equals(key)) {
            if (e.from != skin) {
                e.from = skin;
                e.skin = new PlayerSkin(new ClientAsset.ResourceTexture(e.id, e.id), skin.cape(), skin.elytra(), skin.model(), skin.secure());
            }
            return e.skin;
        }
        NativeImage src = source(base);
        if (src == null) {
            return e != null && e.key.base().equals(base) ? e.skin : skin;
        }
        try {
            int w = src.getWidth() * UPSCALE;
            int h = src.getHeight() * UPSCALE;
            if (e == null || e.texture.getPixels() == null || e.texture.getPixels().getWidth() != w || e.texture.getPixels().getHeight() != h) {
                if (e != null) {
                    Minecraft.getInstance().getTextureManager().release(e.id);
                } else {
                    e = new Entry();
                    ENTRIES.put(player, e);
                }
                e.id = Punizioni.id("marked/" + player.toString().toLowerCase(Locale.ROOT));
                NativeImage out = new NativeImage(w, h, true);
                final Identifier id = e.id;
                e.texture = new DynamicTexture(id::toString, out);
                Minecraft.getInstance().getTextureManager().register(e.id, e.texture);
            }
            paint(src, e.texture.getPixels(), player, body, fresh);
            e.texture.upload();
            e.key = key;
            e.from = skin;
            e.skin = new PlayerSkin(new ClientAsset.ResourceTexture(e.id, e.id), skin.cape(), skin.elytra(), skin.model(), skin.secure());
            return e.skin;
        } catch (RuntimeException ex) {
            Punizioni.LOGGER.warn("Could not mark the skin {}", base, ex);
            return skin;
        }
    }

    // ------------------------------------------------------------------ painting

    /** The skin's regions (in 64x64 skin pixels): x, y, w, h of the faces marks can go on. */
    private static final int[][] BACK = {{32, 20, 8, 12}, {32, 36, 8, 12}};
    private static final int[][] SIDES_OF_BACK = {{16, 20, 4, 12}, {28, 20, 4, 12}};
    /** Everywhere a rat can bite: the faces of the legs, arms, body and the face (base layer). */
    private static final int[][] BITES = {
            // right leg: front, sides, back
            {4, 20, 4, 12}, {0, 20, 4, 12}, {8, 20, 4, 12}, {12, 20, 4, 12},
            // left leg
            {20, 52, 4, 12}, {16, 52, 4, 12}, {24, 52, 4, 12}, {28, 52, 4, 12},
            // right arm, left arm (fronts and outsides)
            {44, 20, 4, 12}, {40, 20, 4, 12}, {36, 52, 4, 12}, {40, 52, 4, 12},
            // body front and sides
            {20, 20, 8, 12}, {16, 20, 4, 12}, {28, 20, 4, 12},
            // face
            {8, 8, 8, 8},
            // feet (bottoms of the legs)
            {8, 16, 4, 4}, {24, 48, 4, 4}};

    private static void paint(NativeImage src, NativeImage out, UUID player, Body body, int fresh) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        // one skin pixel of the 64-wide layout = scale image pixels (HD skins are bigger)
        int scale = Math.max(1, sw / 64) * UPSCALE;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int c = src.getPixel(x, y);
                for (int dy = 0; dy < UPSCALE; dy++) {
                    for (int dx = 0; dx < UPSCALE; dx++) {
                        out.setPixel(x * UPSCALE + dx, y * UPSCALE + dy, c);
                    }
                }
            }
        }
        boolean wide = sh >= sw;
        long seed = player.getMostSignificantBits() ^ 0x5DEECE66DL;
        if (body.burn() > 0.0F) {
            burn(out, scale, body.burn(), new Random(player.getLeastSignificantBits()), wide);
        }
        // every mark has its own random numbers: the old ones never move when new ones come
        for (int i = 0; i < body.lashes(); i++) {
            lash(out, scale, new Random(seed * 31L + i), i, fresh, wide, i >= body.lashes() - 3);
        }
        for (int i = 0; i < body.bites(); i++) {
            Random b = new Random(player.getLeastSignificantBits() * 31L + 7L * i + 3L);
            int[] face = BITES[b.nextInt(BITES.length)];
            if (!wide && face[1] >= 32) {
                face = BITES[b.nextInt(4)];
            }
            float fx = face[0] + 0.5F + b.nextFloat() * (face[2] - 1.0F);
            float fy = face[1] + 0.5F + b.nextFloat() * (face[3] - 1.0F);
            bite(out, scale, fx, fy, 0.45F + b.nextFloat() * 0.35F, b);
        }
    }

    /** One welt: a slanted stripe across the back, wrapping a little round the sides. */
    private static void lash(NativeImage out, int scale, Random r, int index, int fresh, boolean wide, boolean newest) {
        // each lash lands at its own height and angle; the nine tails make bundles of thin cuts
        float y0 = 0.5F + r.nextFloat() * 11.0F;
        float slope = (r.nextFloat() - 0.5F) * 1.3F;
        int tails = 2 + r.nextInt(2);
        float spread = 0.55F + r.nextFloat() * 0.5F;
        boolean bleeding = fresh > 0 && newest;
        int drip = bleeding ? fresh : 0;
        for (int layer = 0; layer < (wide ? 2 : 1); layer++) {
            int[] face = BACK[layer];
            for (int t = 0; t < tails; t++) {
                float off = (t - (tails - 1) * 0.5F) * spread + (r.nextFloat() - 0.5F) * 0.3F;
                float wob = r.nextFloat() * 6.28F;
                // from a little beyond one side to the other, in fine steps
                for (float u = -1.2F; u <= face[2] + 1.2F; u += 0.25F / UPSCALE * 4.0F) {
                    float v = y0 + off + (u - face[2] * 0.5F) * slope + Mth.sin(u * 1.7F + wob) * 0.12F;
                    float px;
                    float py = face[1] + v;
                    int[] onto = face;
                    if (u < 0.0F) {
                        // onto the side of the body (the body's left side is next to the back's left edge in the layout)
                        onto = layer == 0 ? SIDES_OF_BACK[1] : null;
                        px = onto != null ? onto[0] + onto[2] + u : -1;
                        if (onto != null) {
                            py = onto[1] + v;
                        }
                    } else if (u >= face[2]) {
                        onto = layer == 0 ? SIDES_OF_BACK[0] : null;
                        px = onto != null ? onto[0] + (u - face[2]) : -1;
                        if (onto != null) {
                            py = onto[1] + v;
                        }
                    } else {
                        px = face[0] + u;
                    }
                    if (onto == null || py < onto[1] || py >= onto[1] + onto[3]) {
                        continue;
                    }
                    weltPixel(out, scale, px, py, fresh, layer == 1);
                }
            }
            if (drip > 0 && layer == 0) {
                // fresh blood runs down from the cuts
                int drips = 2 + r.nextInt(3);
                for (int d = 0; d < drips; d++) {
                    float u = 0.5F + r.nextFloat() * (face[2] - 1.0F);
                    float v = y0 + (u - face[2] * 0.5F) * slope;
                    float len = (0.6F + r.nextFloat() * 1.6F) * drip;
                    for (float k = 0.0F; k < len; k += 0.25F) {
                        float py = face[1] + v + k;
                        if (py >= face[1] + face[3]) {
                            break;
                        }
                        float a = 1.0F - k / (len + 0.5F) * 0.5F;
                        paintDot(out, scale, face[0] + u, py, 0.18F, mix(0xFF8A0A0A, 0xFF5A0505, k / (len + 0.01F)), a, false);
                    }
                }
            }
        }
    }

    /** A pixel of a welt: torn, raw and red in the middle, swollen purple-red at the edges. */
    private static void weltPixel(NativeImage out, int scale, float px, float py, int fresh, boolean overlay) {
        int core = fresh > 0 ? 0xFFB01212 : 0xFF5E1A16;
        int edge = fresh > 0 ? 0xFF8A2A3A : 0xFF6B3A36;
        paintDot(out, scale, px, py, 0.32F, edge, 0.75F, overlay);
        paintDot(out, scale, px, py, 0.17F, core, 1.0F, overlay);
        if (fresh > 0) {
            paintDot(out, scale, px, py - 0.05F, 0.06F, 0xFFE04040, 0.8F, overlay);
        }
    }

    /** A round rat bite: a dark hole, a torn red ring, a bruise. */
    private static void bite(NativeImage out, int scale, float fx, float fy, float size, Random r) {
        paintDot(out, scale, fx, fy, size, 0xFF6B3040, 0.55F, false);
        paintDot(out, scale, fx, fy, size * 0.62F, 0xFF9A1414, 1.0F, false);
        paintDot(out, scale, fx + (r.nextFloat() - 0.5F) * 0.1F, fy, size * 0.32F, 0xFF2A0505, 1.0F, false);
        if (r.nextInt(3) == 0) {
            // a trickle of blood
            for (float k = 0.0F; k < 1.2F; k += 0.25F) {
                paintDot(out, scale, fx, fy + size * 0.5F + k, 0.12F, 0xFF7A0808, 0.9F - k * 0.4F, false);
            }
        }
    }

    /**
     * Charring: skin first reddens and blisters, then blackens in patches that spread (burn 0..1),
     * with glowing orange cracks in the black.
     */
    private static void burn(NativeImage out, int scale, float burn, Random r, boolean wide) {
        int w = out.getWidth();
        int h = out.getHeight();
        float cell = scale * 1.6F;
        // a few random octaves of value noise, the same for this player every time
        float[] seeds = {r.nextFloat() * 100.0F, r.nextFloat() * 100.0F, r.nextFloat() * 100.0F};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = out.getPixel(x, y);
                if (c >>> 24 == 0) {
                    continue;
                }
                float n = noise(x / cell, y / cell, seeds[0]) * 0.55F + noise(x / (cell * 0.4F), y / (cell * 0.4F), seeds[1]) * 0.3F
                        + noise(x / (cell * 0.15F), y / (cell * 0.15F), seeds[2]) * 0.15F;
                float red = Mth.clamp(burn * 2.5F, 0.0F, 1.0F) * 0.45F;
                float black = Mth.clamp((burn * 1.25F - n) * 5.0F, 0.0F, 1.0F);
                int col = mix(c, 0xFFA03020, red);
                // the charred crust, almost black, faintly brown
                int crust = mix(0xFF1A0F0A, 0xFF2E1A10, n);
                col = mix(col, crust, black);
                // cracks of glowing embers along the edges of the black
                float crack = black > 0.2F && black < 0.75F && noise(x / (cell * 0.25F), y / (cell * 0.25F), seeds[2] + 9.0F) > 0.62F ? 1.0F : 0.0F;
                col = mix(col, 0xFFE06A10, crack * 0.85F * Mth.clamp(burn * 2.0F - 0.6F, 0.0F, 1.0F));
                out.setPixel(x, y, (c & 0xFF000000) | (col & 0x00FFFFFF));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Paints a soft round dot of radius {@code rad} (skin pixels) at skin pixel (px, py). On the
     * overlay layer only pixels that are already there (clothes) are painted: the marks are tears.
     */
    private static void paintDot(NativeImage out, int scale, float px, float py, float rad, int color, float alpha, boolean overlay) {
        float cx = px * scale;
        float cy = py * scale;
        float rr = Math.max(0.6F, rad * scale);
        int x0 = Mth.floor(cx - rr);
        int x1 = Mth.ceil(cx + rr);
        int y0 = Mth.floor(cy - rr);
        int y1 = Mth.ceil(cy + rr);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (x < 0 || y < 0 || x >= out.getWidth() || y >= out.getHeight()) {
                    continue;
                }
                float dx = x + 0.5F - cx;
                float dy = y + 0.5F - cy;
                float d = Mth.sqrt(dx * dx + dy * dy) / rr;
                if (d > 1.0F) {
                    continue;
                }
                int c = out.getPixel(x, y);
                if (overlay && c >>> 24 < 16) {
                    continue;
                }
                float k = alpha * (d < 0.6F ? 1.0F : 1.0F - (d - 0.6F) / 0.4F);
                int m = mix(c, color, k);
                out.setPixel(x, y, (c >>> 24 == 0 ? 0xFF000000 : c & 0xFF000000) | (m & 0x00FFFFFF));
            }
        }
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;
        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    private static float noise(float x, float y, float seed) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float a = hash(ix, iy, seed);
        float b = hash(ix + 1, iy, seed);
        float c = hash(ix, iy + 1, seed);
        float d = hash(ix + 1, iy + 1, seed);
        float sx = fx * fx * (3.0F - 2.0F * fx);
        float sy = fy * fy * (3.0F - 2.0F * fy);
        return Mth.lerp(sy, Mth.lerp(sx, a, b), Mth.lerp(sx, c, d));
    }

    private static float hash(int x, int y, float seed) {
        float h = Mth.sin(x * 127.1F + y * 311.7F + seed * 74.7F) * 43758.5453F;
        return h - Mth.floor(h);
    }

    // ------------------------------------------------------------------ the original skin

    /** The original skin's pixels (kept: they are needed again at every new mark). */
    private static NativeImage source(Identifier base) {
        NativeImage cached = SOURCES.get(base);
        if (cached != null) {
            return cached;
        }
        long now = System.currentTimeMillis();
        Long retry = RETRY.get(base);
        if (retry != null && now < retry) {
            return null;
        }
        NativeImage image = read(base);
        if (image == null) {
            RETRY.put(base, now + 1000L);
            return null;
        }
        RETRY.remove(base);
        SOURCES.put(base, image);
        return image;
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
            Punizioni.LOGGER.debug("Skin {} not readable yet", source, e);
        }
        return null;
    }

    /** Leaving a world: everything goes. */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : ENTRIES.values()) {
            if (e.id != null) {
                mc.getTextureManager().release(e.id);
            }
        }
        ENTRIES.clear();
        for (NativeImage image : SOURCES.values()) {
            image.close();
        }
        SOURCES.clear();
        RETRY.clear();
    }

    /** A player's body healed (death): their texture is not needed any more. */
    public static void forget(UUID player) {
        Entry e = ENTRIES.remove(player);
        if (e != null && e.id != null) {
            Minecraft.getInstance().getTextureManager().release(e.id);
        }
    }
}
