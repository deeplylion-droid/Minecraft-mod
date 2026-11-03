package com.punizioni.server;

import com.punizioni.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.phys.Vec3;

/**
 * Feeding the poor: a bowl of gruel given to someone is eaten there and then (whatever their
 * hunger), the giver keeps the empty bowl. Whoever eats gruel pulls a face everyone sees and
 * their own screen turns green for a moment.
 */
public final class Gruel {
    private Gruel() {
    }

    public static void feed(ServerLevel level, Player giver, Player poor, ItemStack stack, InteractionHand hand) {
        ItemStack bite = stack.copyWithCount(1);
        Consumable consumable = bite.get(DataComponents.CONSUMABLE);
        if (consumable != null) {
            // as if they ate it themselves: the food, the burp, maybe the nausea
            consumable.onConsume(level, poor, bite);
        }
        Vec3 mouth = poor.getEyePosition().add(poor.getViewVector(1.0F).scale(0.3)).add(0.0, -0.15, 0.0);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1))), mouth.x, mouth.y,
                mouth.z, 14, 0.1, 0.1, 0.1, 0.05);
        if (!giver.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack bowl = new ItemStack(Items.BOWL);
            if (stack.isEmpty()) {
                giver.setItemInHand(hand, bowl);
            } else if (!giver.getInventory().add(bowl)) {
                giver.spawnAtLocation(level, bowl);
            }
        }
        disgust(level, poor);
        poor.sendOverlayMessage(Component.translatable("punizioni.msg.fed_by", giver.getDisplayName()).withStyle(ChatFormatting.GOLD));
        giver.sendOverlayMessage(Component.translatable("punizioni.msg.fed", poor.getDisplayName()).withStyle(ChatFormatting.YELLOW));
    }

    /** The grimace (seen by everyone around) and the green on their own screen. */
    public static void disgust(ServerLevel level, Player eater) {
        Gore.toAll(eater, new Net.Disgust(eater.getId()));
        Gore.hurtScreen(eater, Net.VictimFx.GRUEL, 1.0F);
    }
}
