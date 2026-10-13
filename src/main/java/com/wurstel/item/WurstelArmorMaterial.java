package com.wurstel.item;

import com.wurstel.WurstelMod;
import com.wurstel.registry.ModItems;
import com.wurstel.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Armor material of the würstel belt: one point of armor, never wears out. */
public enum WurstelArmorMaterial implements ArmorMaterial {
    WURSTEL;

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 0; // 0 = unbreakable
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return type == ArmorItem.Type.LEGGINGS ? 1 : 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public SoundEvent getEquipSound() {
        return ModSounds.SQUISH.get();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ModItems.WURSTEL.get());
    }

    @Override
    public String getName() {
        return WurstelMod.MODID + ":wurstel_belt";
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
