package com.frusta.enchantment;

import com.frusta.config.FrustaConfig;
import com.frusta.item.WhipItem;
import com.frusta.motion.Whip;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Sottomissione (I-V): goes on whips. Every level doubles the whip's damage (server config) and makes
 * whoever is whipped fall to their knees for a moment, longer at higher levels. Found on enchanted
 * books (enchanting table, librarians, loot) and at the enchanting table on the whips themselves.
 */
public class SottomissioneEnchantment extends Enchantment {
    /** The items Sottomissione can go on: the whips. */
    public static final EnchantmentCategory WHIP =
            EnchantmentCategory.create("FRUSTA_WHIP", item -> item instanceof WhipItem);

    public SottomissioneEnchantment() {
        super(Rarity.RARE, WHIP, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /** Damage multiplier at this level (1 without Sottomissione). */
    public static float damageMultiplier(int level) {
        double perLevel = FrustaConfig.SERVER_SPEC.isLoaded() ? FrustaConfig.SERVER.multiplierPerLevel.get() : 2.0;
        return Whip.damage(1.0, level, perLevel);
    }

    @Override
    public int getMaxLevel() {
        return Whip.MAX_LEVEL;
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
