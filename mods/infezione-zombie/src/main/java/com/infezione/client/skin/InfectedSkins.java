package com.infezione.client.skin;

import com.infezione.InfezioneMod;
import com.infezione.client.ClientInfections;
import com.infezione.config.InfezioneConfig;
import com.infezione.infection.Stages;
import com.infezione.skin.SkinZombifier;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Gives infected players their infected skin, on this client: the player's own skin is read back from
 * the graphics card, changed by {@link SkinZombifier} for the stage, and put in the player's entry of the
 * player list in place of the skin. Everything that draws the player (the body, the hand in first
 * person, the face in the Tab list) then shows it. When the player is cured the skin goes back.
 */
public final class InfectedSkins {
    /** A player's skin while infected. */
    private static final class Swap {
        /** The skin the player had: null for the default one. */
        @Nullable
        ResourceLocation original;
        ResourceLocation infected;
        @Nullable
        ResourceLocation glow;
        int stage;
    }

    private static final Map<UUID, Swap> SWAPS = new HashMap<>();
    @Nullable
    private static Field textureLocations;
    private static boolean broken;

    private InfectedSkins() {
    }

    /** What glows in the dark on an infected player (the eyes, from stage 4), or null. */
    @Nullable
    public static ResourceLocation glow(UUID player) {
        Swap swap = SWAPS.get(player);
        return swap == null ? null : swap.glow;
    }

    /** Every client tick: each player's skin matches their stage. */
    public static void tick(Minecraft mc) {
        ClientPacketListener connection = mc.getConnection();
        if (mc.level == null || connection == null || broken) {
            return;
        }
        boolean enabled = InfezioneConfig.CLIENT.skins.get();
        for (AbstractClientPlayer player : mc.level.players()) {
            int stage = enabled ? ClientInfections.stage(player) : Stages.HEALTHY;
            if (stage > Stages.HEALTHY || SWAPS.containsKey(player.getUUID())) {
                update(mc, connection, player.getUUID(), stage);
            }
        }
        // players out of sight keep their look in the Tab list, until they are cured
        Iterator<Map.Entry<UUID, Swap>> it = SWAPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Swap> entry = it.next();
            PlayerInfo info = connection.getPlayerInfo(entry.getKey());
            if (info == null) {
                release(mc.getTextureManager(), entry.getValue());
                it.remove();
            } else if (!enabled || ClientInfections.stage(entry.getKey()) == Stages.HEALTHY) {
                restore(mc.getTextureManager(), info, entry.getValue());
                it.remove();
            }
        }
    }

    private static void update(Minecraft mc, ClientPacketListener connection, UUID uuid, int stage) {
        PlayerInfo info = connection.getPlayerInfo(uuid);
        Map<MinecraftProfileTexture.Type, ResourceLocation> textures = info == null ? null : textures(info);
        if (textures == null) {
            return;
        }
        Swap swap = SWAPS.get(uuid);
        if (stage == Stages.HEALTHY) {
            if (swap != null) {
                restore(mc.getTextureManager(), info, swap);
                SWAPS.remove(uuid);
            }
            return;
        }
        ResourceLocation current = textures.get(MinecraftProfileTexture.Type.SKIN);
        boolean ours = swap != null && swap.infected.equals(current);
        if (ours && swap.stage == stage) {
            return;
        }
        // the skin under ours: the one we replaced, or a new one the game just loaded (it replaced ours)
        ResourceLocation original = ours ? swap.original : current;
        ResourceLocation source = original != null ? original : DefaultPlayerSkin.getDefaultSkin(uuid);
        Pixels pixels = read(mc.getTextureManager(), source);
        if (pixels == null) {
            return;
        }
        SkinZombifier.Result result = SkinZombifier.zombify(pixels.argb, pixels.width, pixels.height, stage,
                uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        if (swap == null) {
            swap = new Swap();
            String name = uuid.toString().toLowerCase(Locale.ROOT);
            swap.infected = InfezioneMod.id("skins/" + name);
            SWAPS.put(uuid, swap);
        }
        TextureManager manager = mc.getTextureManager();
        manager.register(swap.infected, new DynamicTexture(image(result.skin(), pixels.width, pixels.height)));
        ResourceLocation glowLocation = InfezioneMod.id("skins/" + uuid.toString().toLowerCase(Locale.ROOT) + "_glow");
        if (anyVisible(result.glow())) {
            manager.register(glowLocation, new DynamicTexture(image(result.glow(), pixels.width, pixels.height)));
            swap.glow = glowLocation;
        } else if (swap.glow != null) {
            manager.release(swap.glow);
            swap.glow = null;
        }
        swap.original = original;
        swap.stage = stage;
        textures.put(MinecraftProfileTexture.Type.SKIN, swap.infected);
    }

    /** The player gets their skin back. */
    private static void restore(TextureManager manager, PlayerInfo info, Swap swap) {
        Map<MinecraftProfileTexture.Type, ResourceLocation> textures = textures(info);
        if (textures != null && swap.infected.equals(textures.get(MinecraftProfileTexture.Type.SKIN))) {
            if (swap.original != null) {
                textures.put(MinecraftProfileTexture.Type.SKIN, swap.original);
            } else {
                textures.remove(MinecraftProfileTexture.Type.SKIN);
            }
        }
        release(manager, swap);
    }

    private static void release(TextureManager manager, Swap swap) {
        manager.release(swap.infected);
        if (swap.glow != null) {
            manager.release(swap.glow);
        }
    }

    /** Leaving the server: every texture goes. */
    public static void clear() {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        for (Swap swap : SWAPS.values()) {
            release(manager, swap);
        }
        SWAPS.clear();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<MinecraftProfileTexture.Type, ResourceLocation> textures(PlayerInfo info) {
        try {
            if (textureLocations == null) {
                textureLocations = ObfuscationReflectionHelper.findField(PlayerInfo.class, "f_105299_");
            }
            return (Map<MinecraftProfileTexture.Type, ResourceLocation>) textureLocations.get(info);
        } catch (Exception e) {
            broken = true;
            InfezioneMod.LOGGER.error("Cannot reach the skins of the player list: infected players keep their skin", e);
            return null;
        }
    }

    private record Pixels(int[] argb, int width, int height) {
    }

    /** A texture's pixels as the game uses them (a skin is turned to 64x64 and cleaned when it is loaded). */
    @Nullable
    private static Pixels read(TextureManager manager, ResourceLocation location) {
        AbstractTexture texture = manager.getTexture(location);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture.getId());
        int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0 || width > 1024 || height > 1024) {
            return null;
        }
        try (NativeImage image = new NativeImage(width, height, false)) {
            image.downloadTexture(0, false);
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    argb[y * width + x] = swapRedBlue(image.getPixelRGBA(x, y));
                }
            }
            return new Pixels(argb, width, height);
        }
    }

    private static NativeImage image(int[] argb, int width, int height) {
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, swapRedBlue(argb[y * width + x]));
            }
        }
        return image;
    }

    /** ARGB to the ABGR of {@link NativeImage}, and back. */
    private static int swapRedBlue(int c) {
        return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
    }

    private static boolean anyVisible(int[] argb) {
        for (int c : argb) {
            if ((c >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    /** The stage of the infected skin a player wears on this client (0 = their own skin). */
    public static int stageShown(UUID player) {
        Swap swap = SWAPS.get(player);
        return swap == null ? Stages.HEALTHY : swap.stage;
    }
}
