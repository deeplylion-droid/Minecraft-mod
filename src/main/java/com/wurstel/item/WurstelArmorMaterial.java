package com.wurstel.item;

import com.wurstel.WurstelMod;
import com.wurstel.config.WurstelConfig;
import com.wurstel.registry.ModItems;
import com.wurstel.registry.ModSounds;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.TierSortingRegistry;

/**
 * Materials of the würstel belts: the plain one and its diamond and netherite upgrades. Besides the
 * armor stats, a material sets the colour of its würstel and how hard and how fast its slaps are.
 * Belts never wear out.
 */
public enum WurstelArmorMaterial implements ArmorMaterial {
    WURSTEL("wurstel_belt", "wurstel", 1, 0.0F, 0.0F, 12, null,
            () -> Ingredient.of(ModItems.WURSTEL.get())),
    DIAMOND("diamond_wurstel_belt", "diamond_wurstel", 2, 1.0F, 0.0F, 10, Tiers.DIAMOND,
            () -> Ingredient.of(Items.DIAMOND)),
    NETHERITE("netherite_wurstel_belt", "netherite_wurstel", 3, 2.0F, 0.1F, 15, Tiers.NETHERITE,
            () -> Ingredient.of(Items.NETHERITE_INGOT));

    private final String beltName;
    private final String beltTexture;
    private final ResourceLocation wurstelTexture;
    private final int defense;
    private final float toughness;
    private final float knockbackResistance;
    private final int enchantmentValue;
    /** Tier of the tools whose drops the slaps get, null for none (like a bare hand). */
    @Nullable
    private final Tier harvestTier;
    private final Supplier<Ingredient> repairIngredient;

    WurstelArmorMaterial(String beltName, String wurstelTexture, int defense, float toughness, float knockbackResistance,
                         int enchantmentValue, @Nullable Tier harvestTier, Supplier<Ingredient> repairIngredient) {
        this.beltName = beltName;
        this.beltTexture = WurstelMod.MODID + ":textures/models/armor/" + beltName + "_layer_2.png";
        this.wurstelTexture = new ResourceLocation(WurstelMod.MODID, "textures/entity/" + wurstelTexture + ".png");
        this.defense = defense;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.enchantmentValue = enchantmentValue;
        this.harvestTier = harvestTier;
        this.repairIngredient = repairIngredient;
    }

    /** Texture of the belt drawn on the legs. */
    public String beltTexture() {
        return beltTexture;
    }

    /** Texture of the giant würstel: every material has its own colour. */
    public ResourceLocation wurstelTexture() {
        return wurstelTexture;
    }

    /** Item whose bits fly off when the würstel hits something. */
    public Item particleItem() {
        return switch (this) {
            case WURSTEL -> ModItems.WURSTEL.get();
            case DIAMOND -> Items.DIAMOND;
            case NETHERITE -> Items.NETHERITE_INGOT;
        };
    }

    /** How many times harder than a punch the slaps hit, before Turgido (server config). */
    public float damageMultiplier() {
        WurstelConfig.Server config = WurstelConfig.SERVER;
        ForgeConfigSpec.DoubleValue value = switch (this) {
            case WURSTEL -> config.damageMultiplier;
            case DIAMOND -> config.diamondDamageMultiplier;
            case NETHERITE -> config.netheriteDamageMultiplier;
        };
        return value.get().floatValue();
    }

    /** How many times faster than a bare hand the slaps break blocks (server config). */
    public float miningSpeedMultiplier() {
        WurstelConfig.Server config = WurstelConfig.SERVER;
        ForgeConfigSpec.DoubleValue value = switch (this) {
            case WURSTEL -> config.miningSpeedMultiplier;
            case DIAMOND -> config.diamondMiningSpeedMultiplier;
            case NETHERITE -> config.netheriteMiningSpeedMultiplier;
        };
        return value.get().floatValue();
    }

    /** True when slapping a block gets its drops, like the right tool of the same tier would. */
    public boolean canHarvest(BlockState state) {
        return harvestTier != null && TierSortingRegistry.isCorrectTierForDrops(harvestTier, state);
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 0; // 0 = unbreakable
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return type == ArmorItem.Type.LEGGINGS ? defense : 0;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return ModSounds.SQUISH.get();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }

    @Override
    public String getName() {
        return WurstelMod.MODID + ":" + beltName;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
