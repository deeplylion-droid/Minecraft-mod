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
 * Albinism: a whitened copy of the player's own skin. Hair and face (the head) turn white and
 * pale, bare skin elsewhere turns pale; clothes keep their colours.
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
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // skins are 64 pixels wide (or a multiple): one skin pixel is w / 64 image pixels both ways
                    out.setPixel(x, y, whiten(src.getPixel(x, y), region(x * 64 / w, y * 64 / w)));
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

    /** ARGB in, ARGB out: the whole head (hair and face) turns white and pale, bare arms turn pale. */
    private static int whiten(int argb, int region) {
        int a = argb >>> 24;
        if (a == 0 || region == OTHER) {
            return argb;
        }
        boolean head = region == HEAD;
        float r = (argb >> 16 & 255) / 255.0F;
        float g = (argb >> 8 & 255) / 255.0F;
        float b = (argb & 255) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float lum = 0.299F * r + 0.587F * g + 0.114F * b;
        boolean skinTone = r >= g && g >= b && max > 0.3F && (max - min) / Math.max(max, 1.0E-4F) < 0.65F;
        if (!head && !skinTone) {
            return argb;
        }
        // light, almost colourless, a touch of pink
        float target = 0.68F + 0.32F * lum;
        float keep = head ? 0.12F : 0.25F;
        float nr = target * (1.0F - keep) + r * keep + 0.03F;
        float ng = target * (1.0F - keep) + g * keep - 0.01F;
        float nb = target * (1.0F - keep) + b * keep - 0.01F;
        return a << 24 | channel(nr) << 16 | channel(ng) << 8 | channel(nb);
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
