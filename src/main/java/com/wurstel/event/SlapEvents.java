package com.wurstel.event;

import com.wurstel.WurstelMod;
import com.wurstel.config.WurstelConfig;
import com.wurstel.item.WurstelBeltItem;
import java.lang.ref.WeakReference;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side gameplay of a würstel slap: a melee hit with an empty main hand while wearing the
 * belt deals more damage and pushes the target further. Works on mobs and, with PvP on, players.
 */
@Mod.EventBusSubscriber(modid = WurstelMod.MODID)
public final class SlapEvents {
    /** Target of the slap being processed, so the knockback that follows can be boosted. */
    private static WeakReference<LivingEntity> slapTarget = new WeakReference<>(null);
    private static long slapTime;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker) {
            return;
        }
        if (!source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)) {
            return;
        }
        if (!WurstelBeltItem.slapsWithWurstel(attacker)) {
            return;
        }
        float multiplier = WurstelConfig.SERVER.damageMultiplier.get().floatValue();
        event.setAmount(event.getAmount() * multiplier);

        LivingEntity target = event.getEntity();
        slapTarget = new WeakReference<>(target);
        slapTime = target.level().getGameTime();
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (slapTarget.get() != target || target.level().getGameTime() != slapTime) {
            return;
        }
        slapTarget = new WeakReference<>(null);
        float extra = WurstelConfig.SERVER.extraKnockback.get().floatValue();
        event.setStrength(event.getStrength() + extra);
    }

    private SlapEvents() {
    }
}
