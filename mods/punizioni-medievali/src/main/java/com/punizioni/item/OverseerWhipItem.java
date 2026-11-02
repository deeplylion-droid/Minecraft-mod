package com.punizioni.item;

import com.punizioni.ModSounds;
import com.punizioni.Punizioni;
import com.punizioni.data.Body;
import com.punizioni.data.ModData;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

/**
 * The overseer's bullwhip, to make the peasants work in the fields: a long single lash (drawn and
 * simulated on the client) that reaches two blocks further than a fist. A crack on the back hurts a
 * little, leaves a welt on the skin (like the cat-o'-nine-tails) and spurs: 30 seconds of Speed and
 * Haste, so they walk, dig and harvest faster.
 */
public class OverseerWhipItem extends Item {
    /** How long the spur lasts (ticks). */
    public static final int SPUR_TICKS = 30 * 20;
    /** Extra reach over the vanilla 3 blocks. */
    public static final double EXTRA_REACH = 2.0;

    public OverseerWhipItem(Item.Properties properties) {
        super(properties);
    }

    /** 1 of damage over the bare hand's 1 (one heart in all), a sword's pace, 5 blocks of reach. */
    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.2, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(Punizioni.id("overseer_whip_reach"), EXTRA_REACH,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        // the spur: refreshed by every crack (faint swirls, like a beacon's)
        target.addEffect(new MobEffectInstance(MobEffects.SPEED, SPUR_TICKS, 0, true, true), attacker);
        target.addEffect(new MobEffectInstance(MobEffects.HASTE, SPUR_TICKS, 1, true, true), attacker);
        Body body = ModData.body(target).lashed(level.getGameTime());
        ModData.setBody(target, body);
        level.playSound(null, target.getX(), target.getY() + 1.2, target.getZ(), ModSounds.LASH, SoundSource.PLAYERS, 1.2F,
                1.05F + level.getRandom().nextFloat() * 0.2F);
        float yaw = target.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 back = target.position().add(Mth.sin(yaw) * 0.3, target.getBbHeight() * 0.68, -Mth.cos(yaw) * 0.3);
        Gore.burst(level, back, 5, 0.12, 0.08);
        Gore.hurtScreen(target, Net.VictimFx.LASH, 0.45F);
        if (target instanceof ServerPlayer peasant) {
            peasant.sendOverlayMessage(Component.translatable("punizioni.msg.spurred", attacker.getName()).withStyle(ChatFormatting.GOLD));
        }
    }
}
