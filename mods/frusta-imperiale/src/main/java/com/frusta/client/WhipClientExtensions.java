package com.frusta.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Client side of the whip item: the arm holding it follows the moves. */
public class WhipClientExtensions implements IClientItemExtensions {
    static {
        // The whip's arm poses are new values of vanilla's ArmPose enum: they must exist before the game first
        // switches over that enum (the first player it draws), or that switch is too short for them and crashes.
        // This class is made while the items are registered, early enough.
        WhipPoses.init();
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return hand == InteractionHand.MAIN_HAND && entity instanceof Player ? WhipPoses.WHIP : null;
    }
}
