package com.frusta.item;

import com.frusta.FrustaMod;
import com.frusta.config.FrustaConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The three whips: black leather, and its diamond and netherite upgrades. A material sets how long
 * the whip is, how hard it hits (server config), the look of its lash and the colour of its glow.
 */
public enum WhipMaterial {
    LEATHER("whip", 5.0, 0xE9E6FF, 0xFFFFFF, 1.06F, 12),
    DIAMOND("diamond_whip", 5.5, 0x4FF0FF, 0xB8FFFF, 1.0F, 10),
    NETHERITE("netherite_whip", 6.0, 0xFF6A1E, 0xC23CFF, 0.88F, 15);

    private final String name;
    private final double length;
    private final int glow;
    private final int glow2;
    private final float pitch;
    private final int enchantmentValue;
    private final ResourceLocation lashTexture;

    WhipMaterial(String name, double length, int glow, int glow2, float pitch, int enchantmentValue) {
        this.name = name;
        this.length = length;
        this.glow = glow;
        this.glow2 = glow2;
        this.pitch = pitch;
        this.enchantmentValue = enchantmentValue;
        this.lashTexture = FrustaMod.id("textures/entity/" + name + "_lash.png");
    }

    /** Length of the whip in blocks: how far it reaches. */
    public double length() {
        return length;
    }

    /** Main colour (RGB) of the trail and of the shockwave of its crack. */
    public int glow() {
        return glow;
    }

    /** Second colour (RGB) of the trail, towards its end. */
    public int glow2() {
        return glow2;
    }

    /** Pitch of its sounds: the heavier, the deeper. */
    public float pitch() {
        return pitch;
    }

    public int enchantmentValue() {
        return enchantmentValue;
    }

    /** Texture of the lash (braided leather; 16 around, 512 along from handle to tip). */
    public ResourceLocation lashTexture() {
        return lashTexture;
    }

    /** Damage of a lash before Sottomissione (server config). */
    public float damage() {
        FrustaConfig.Server config = FrustaConfig.SERVER;
        ForgeConfigSpec.DoubleValue value = switch (this) {
            case LEATHER -> config.leatherDamage;
            case DIAMOND -> config.diamondDamage;
            case NETHERITE -> config.netheriteDamage;
        };
        return value.get().floatValue();
    }

    /** Damage of a lash before Sottomissione with the default config (for tooltips without a world). */
    public float defaultDamage() {
        return switch (this) {
            case LEATHER -> 4.0F;
            case DIAMOND -> 6.0F;
            case NETHERITE -> 8.0F;
        };
    }
}
