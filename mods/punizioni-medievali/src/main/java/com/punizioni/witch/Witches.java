package com.punizioni.witch;

import com.punizioni.item.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Who is a witch: whoever wears the pointed hat. */
public final class Witches {
    private Witches() {
    }

    public static boolean isWitch(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.WITCH_HAT);
    }
}
