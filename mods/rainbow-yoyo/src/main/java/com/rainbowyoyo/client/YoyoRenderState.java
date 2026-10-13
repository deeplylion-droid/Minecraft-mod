package com.rainbowyoyo.client;

import com.rainbowyoyo.entity.YoyoEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Which living entity is being drawn right now, so its held yo-yo can hide while it is thrown. */
public final class YoyoRenderState {
    @Nullable
    private static LivingEntity current;

    static void begin(LivingEntity entity) {
        current = entity;
    }

    static void end() {
        current = null;
    }

    /** True if the yo-yo item being drawn in a hand is the one currently out of that hand. */
    public static boolean heldYoyoIsOut(boolean firstPerson) {
        LivingEntity holder = firstPerson ? Minecraft.getInstance().player : current;
        return holder != null && YoyoEntity.clientYoyoOf(holder) != null;
    }

    private YoyoRenderState() {
    }
}
