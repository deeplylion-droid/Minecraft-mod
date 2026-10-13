package com.wurstel.item;

import com.wurstel.config.WurstelConfig;
import com.wurstel.enchantment.TurgidoEnchantment;
import com.wurstel.registry.ModEnchantments;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A würstel belt (plain, diamond or netherite). Worn in the leggings slot; the giant würstel itself is
 * drawn by a render layer.
 */
public class WurstelBeltItem extends ArmorItem {

    public WurstelBeltItem(WurstelArmorMaterial material, Properties properties) {
        super(material, ArmorItem.Type.LEGGINGS, properties);
    }

    /** Material of this belt: it sets the würstel's colour and the strength of its slaps. */
    public WurstelArmorMaterial getWurstelMaterial() {
        return (WurstelArmorMaterial) getMaterial();
    }

    /** True when the entity wears the belt. */
    public static boolean isWearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof WurstelBeltItem;
    }

    /** True when the entity wears the belt and has an empty main hand, so its punches become slaps. */
    public static boolean slapsWithWurstel(LivingEntity entity) {
        return isWearing(entity) && entity.getMainHandItem().isEmpty();
    }

    /** The belt whose würstel does the entity's punches, or an empty stack if it does not slap. */
    public static ItemStack getSlappingBelt(LivingEntity entity) {
        return slapsWithWurstel(entity) ? entity.getItemBySlot(EquipmentSlot.LEGS) : ItemStack.EMPTY;
    }

    /** How many times harder than a punch the slaps of this belt hit: its material's multiplier times Turgido's. */
    public static float getDamageMultiplier(ItemStack belt) {
        WurstelArmorMaterial material = ((WurstelBeltItem) belt.getItem()).getWurstelMaterial();
        int turgido = belt.getEnchantmentLevel(ModEnchantments.TURGIDO.get());
        return material.damageMultiplier() * TurgidoEnchantment.damageMultiplier(turgido);
    }

    /** The belt never wears out, but it can still be enchanted at the enchanting table. */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public @Nullable String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return getWurstelMaterial().beltTexture();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wurstel.wurstel_belt.desc").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.wurstel.wurstel_belt.desc2").withStyle(ChatFormatting.GRAY));
        // The multipliers come from the server config, which only exists while a world is open.
        if (WurstelConfig.SERVER_SPEC.isLoaded()) {
            tooltip.add(Component.translatable("item.wurstel.wurstel_belt.damage",
                    format(getDamageMultiplier(stack))).withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("item.wurstel.wurstel_belt.mining",
                    format(getWurstelMaterial().miningSpeedMultiplier())).withStyle(ChatFormatting.BLUE));
        }
    }

    private static String format(float multiplier) {
        return ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(multiplier);
    }
}
