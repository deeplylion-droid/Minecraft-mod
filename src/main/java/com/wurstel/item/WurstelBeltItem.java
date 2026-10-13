package com.wurstel.item;

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

/** The würstel belt. Worn in the leggings slot; the giant würstel itself is drawn by a render layer. */
public class WurstelBeltItem extends ArmorItem {
    private static final String TEXTURE = "wurstel:textures/models/armor/wurstel_belt_layer_2.png";

    public WurstelBeltItem(Properties properties) {
        super(WurstelArmorMaterial.WURSTEL, ArmorItem.Type.LEGGINGS, properties);
    }

    /** True when the entity wears the belt. */
    public static boolean isWearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof WurstelBeltItem;
    }

    /** True when the entity wears the belt and has an empty main hand, so its punches become slaps. */
    public static boolean slapsWithWurstel(LivingEntity entity) {
        return isWearing(entity) && entity.getMainHandItem().isEmpty();
    }

    @Override
    public @Nullable String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return TEXTURE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wurstel.wurstel_belt.desc").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.wurstel.wurstel_belt.desc2").withStyle(ChatFormatting.GRAY));
    }
}
