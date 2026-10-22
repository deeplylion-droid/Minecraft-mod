package com.filorosso.server;

import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The "Gemelli" fate: an effect that reaches one partner (a potion, a golden apple, a witch's
 * poison, a beacon) reaches the other too, wherever they are in the same world.
 */
public final class Twins {
    private Twins() {
    }

    /** Copying an effect adds one to the partner, which would copy it back: not while copying. */
    private static boolean copying;

    public static void onEffect(LivingEntity entity, MobEffectInstance effect) {
        if (copying || !(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Couple couple = Bonds.coupleOf(player);
        if (couple == null || couple.bondType() != BondType.TWINS) {
            return;
        }
        ServerPlayer partner = Bonds.partner(player);
        if (partner == null || !partner.isAlive() || partner.level() != level || partner.isSpectator()) {
            return;
        }
        MobEffectInstance has = partner.getEffect(effect.getEffect());
        if (has != null && has.getAmplifier() >= effect.getAmplifier() && (has.isInfiniteDuration() || has.getDuration() >= effect.getDuration() - 20)) {
            return;
        }
        copying = true;
        try {
            partner.addEffect(new MobEffectInstance(effect), player);
        } finally {
            copying = false;
        }
        com.filorosso.util.Fx.forOthers(level, partner, ParticleTypes.WITCH, partner.getX(), partner.getY() + 1.2, partner.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
    }
}
