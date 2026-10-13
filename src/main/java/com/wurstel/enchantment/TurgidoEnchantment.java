package com.wurstel.enchantment;

import com.wurstel.config.WurstelConfig;
import com.wurstel.item.WurstelBeltItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Turgido (I-V): goes on würstel belts and multiplies the damage of their slaps, doubling it at every
 * level by default. Found on enchanted books (enchanting table, librarians, loot) and at the
 * enchanting table on the belts themselves.
 */
public class TurgidoEnchantment extends Enchantment {
    /** The items Turgido can go on: the würstel belts. */
    public static final EnchantmentCategory WURSTEL_BELT =
            EnchantmentCategory.create("WURSTEL_BELT", item -> item instanceof WurstelBeltItem);
    public static final int MAX_LEVEL = 5;

    public TurgidoEnchantment() {
        super(Rarity.RARE, WURSTEL_BELT, new EquipmentSlot[]{EquipmentSlot.LEGS});
    }

    /** Slap damage multiplier at this level (1 without Turgido). */
    public static float damageMultiplier(int level) {
        if (level <= 0) {
            return 1.0F;
        }
        // Levels above V (only possible with commands) count as V, so the damage stays finite.
        int effective = Math.min(level, MAX_LEVEL);
        return (float) Math.pow(WurstelConfig.SERVER.turgidoMultiplierPerLevel.get(), effective);
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int level) {
        return 1 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }
}
