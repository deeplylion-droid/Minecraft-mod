package com.chiappe.client;

import com.chiappe.Butt;
import com.chiappe.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What everyone sees when a player farts: the cheeks squeeze and blow apart ({@link ButtState}), a jet
 * of gas comes out from between them and a ring of it rolls out as far as the fart reaches. At the top
 * level it is a blast.
 */
public final class FartFx {
    private FartFx() {
    }

    /** Game time when the local player can fart again, and how long the wait was. */
    static long readyAt;
    static int wait = 1;

    static void reset() {
        readyAt = 0L;
        wait = 1;
    }

    static void play(Minecraft mc, int entityId, int level) {
        ClientLevel world = mc.level;
        if (world == null || !(world.getEntity(entityId) instanceof Player p)) {
            return;
        }
        Cheeks.get(entityId).fart();
        if (p == mc.player) {
            wait = Butt.cooldownTicks(level);
            readyAt = world.getGameTime() + wait;
        }

        RandomSource random = world.getRandom();
        double size = Butt.diameter(level);
        double r = size / 2.0;
        double radius = Butt.fartRadius(level);
        float scale = p.getScale();
        Vec3 behind = Vec3.directionFromRotation(0.0F, p.yBodyRot).scale(-1.0);
        Vec3 side = new Vec3(-behind.z, 0.0, behind.x);
        Vec3 feet = p.position();
        // low in the cleft, at the back
        Vec3 exit = feet.add(behind.scale((Butt.BACK + Butt.exitBack(r)) * scale)).add(0.0, Butt.exitHeight(r) * scale, 0.0);
        boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
        Shake.fart(mc, p, level, radius);

        // the jet
        int jet = 12 + (int) (5.0 * size);
        double speed = 0.35 + 0.09 * r;
        double spread = 0.12 + 0.05 * r;
        for (int i = 0; i < jet; i++) {
            double k = speed * (0.6 + random.nextDouble() * 0.7);
            Vec3 v = behind.scale(k)
                    .add(side.scale((random.nextDouble() - 0.5) * 2.0 * spread))
                    .add(0.0, (random.nextDouble() - 0.6) * spread, 0.0);
            Vec3 at = exit.add(side.scale((random.nextDouble() - 0.5) * 0.3 * r)).add(0.0, (random.nextDouble() - 0.5) * 0.25 * r, 0.0);
            gas(mc, at, v, (float) (0.8 + 0.4 * r) * (0.7F + random.nextFloat() * 0.6F));
        }

        // the ring rolling over the ground, as far as it hurts
        int ring = 16 + (int) (4.0 * radius);
        double ringSpeed = radius * 0.125;
        double from = firstPerson ? 1.6 : 0.8;
        for (int i = 0; i < ring; i++) {
            double a = Mth.TWO_PI * (i + random.nextDouble() * 0.5) / ring;
            Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            Vec3 at = feet.add(dir.scale(from)).add(0.0, 0.15 + random.nextDouble() * (0.3 + 0.08 * radius), 0.0);
            gas(mc, at, dir.scale(ringSpeed * (0.9 + random.nextDouble() * 0.2)).add(0.0, 0.01, 0.0),
                    (float) (0.7 + 0.15 * radius) * (0.8F + random.nextFloat() * 0.4F));
        }

        if (level >= Butt.MAX_LEVEL) {
            // the atomic one: blasts, and a mushroom of gas rising out of the cleft
            world.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, exit.x, exit.y, exit.z, 0.0, 0.0, 0.0);
            world.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, feet.x, feet.y + 0.5, feet.z, 0.0, 0.0, 0.0);
            for (int i = 0; i < 8; i++) {
                double a = Mth.TWO_PI * i / 8.0;
                world.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, feet.x + Math.cos(a) * radius * 0.6, feet.y + 0.8,
                        feet.z + Math.sin(a) * radius * 0.6, 0.0, 0.0, 0.0);
            }
            double top = (Butt.centreHeight(r) + Butt.TALL * r) * scale;
            Vec3 stem = feet.add(behind.scale((Butt.BACK + Butt.back(r)) * scale));
            for (int i = 0; i < 36; i++) {
                double y = random.nextDouble() * top;
                Vec3 at = stem.add((random.nextDouble() - 0.5) * 2.0, y, (random.nextDouble() - 0.5) * 2.0);
                gas(mc, at, new Vec3((random.nextDouble() - 0.5) * 0.1, 0.45 + random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.1),
                        3.0F + random.nextFloat() * 2.0F);
            }
            for (int i = 0; i < 40; i++) {
                double a = Mth.TWO_PI * i / 40.0;
                Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
                Vec3 at = stem.add(0.0, top + 5.0 + random.nextDouble() * 2.0, 0.0).add(dir.scale(1.5));
                gas(mc, at, dir.scale(0.45 + random.nextDouble() * 0.25).add(0.0, 0.15 + random.nextDouble() * 0.2, 0.0),
                        4.0F + random.nextFloat() * 2.5F);
            }
        }
    }

    private static void gas(Minecraft mc, Vec3 at, Vec3 v, float size) {
        Particle particle = mc.particleEngine.createParticle(ModParticles.GAS, at.x, at.y, at.z, v.x, v.y, v.z);
        if (particle instanceof GasParticle gas) {
            gas.grow(size);
        }
    }

    /** 0 when the fart is ready, up to 1 right after one. */
    static float cooldown(Minecraft mc) {
        if (mc.level == null) {
            return 0.0F;
        }
        long left = readyAt - mc.level.getGameTime();
        return left <= 0L ? 0.0F : Math.min(1.0F, (float) left / wait);
    }
}
