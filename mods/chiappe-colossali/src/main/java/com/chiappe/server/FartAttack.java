package com.chiappe.server;

import com.chiappe.Butt;
import com.chiappe.ModData;
import com.chiappe.ModSounds;
import com.chiappe.net.Net;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The fart: everything around the player is hit, harder with every level (see {@link Butt}), pushed
 * away and left dizzy. In the air it pushes you up. The Ender Dragon takes it on the head, in full:
 * at level 25 that is 1000 damage, and it has 200.
 */
public final class FartAttack {
    private FartAttack() {
    }

    /** Game time from which each player can fart again. */
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Net.Fart.TYPE, (payload, context) -> fart(context.player()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT.remove(handler.player.getUUID()));
    }

    /** Full damage up to 60% of the radius, then down to half at the edge. */
    static float falloff(double distance, double radius) {
        double t = (distance / radius - 0.6) / 0.4;
        return (float) (1.0 - 0.5 * Math.max(0.0, Math.min(1.0, t)));
    }

    public static void fart(ServerPlayer p) {
        if (p.isSpectator() || !p.isAlive()) {
            return;
        }
        ServerLevel level = p.level();
        long now = level.getGameTime();
        Long next = NEXT.get(p.getUUID());
        if (next != null && now < next) {
            return;
        }
        int lv = ModData.level(p);
        NEXT.put(p.getUUID(), now + Butt.cooldownTicks(lv));
        double size = Butt.diameter(lv);
        double radius = Butt.fartRadius(lv);
        float damage = Butt.fartDamage(lv);
        Vec3 centre = p.position().add(0.0, p.getBbHeight() * 0.45, 0.0);
        Vec3 behind = Vec3.directionFromRotation(0.0F, p.yBodyRot).scale(-1.0);
        DamageSource source = level.damageSources().source(ModDamage.FART, p);
        AABB box = new AABB(centre, centre).inflate(radius);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == p || !e.isAlive() || e instanceof EnderDragon) {
                continue;
            }
            if (e instanceof OwnableEntity pet && p.equals(pet.getOwner())) {
                continue;
            }
            double distance = Math.sqrt(e.getBoundingBox().distanceToSqr(centre));
            if (distance > radius) {
                continue;
            }
            float k = falloff(distance, radius);
            if (!e.hurtServer(level, source, damage * k)) {
                continue;
            }
            Vec3 away = e.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? behind : away.normalize();
            Vec3 push = away.scale((0.5 + 0.05 * size) * k).add(0.0, 0.3 + 0.03 * size * k, 0.0);
            if (e instanceof ServerPlayer other) {
                fling(other, other.getDeltaMovement().add(push));
                other.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60 + 4 * lv, 0));
            } else {
                e.push(push);
            }
        }

        // the dragon: if any part of it is in the cloud it takes the whole fart on its head
        for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, box.inflate(24.0))) {
            boolean inside = Math.sqrt(dragon.getBoundingBox().distanceToSqr(centre)) <= radius;
            for (EnderDragonPart part : dragon.getSubEntities()) {
                inside |= Math.sqrt(part.getBoundingBox().distanceToSqr(centre)) <= radius;
            }
            if (inside && dragon.isAlive()) {
                dragon.hurt(level, dragon.head, source, damage);
            }
        }

        // in the air it works like a jet
        if (!p.onGround() && !p.isInWater() && !p.getAbilities().flying) {
            Vec3 m = p.getDeltaMovement();
            fling(p, new Vec3(m.x, Math.max(m.y, 0.55 + 0.012 * lv), m.z));
            p.resetFallDistance();
        }
        p.causeFoodExhaustion(0.3F + 0.04F * lv);

        float volume = (float) (1.0 + 0.4 * size);
        float pitch = lv >= Butt.MAX_LEVEL ? 1.0F : 1.3F - 0.022F * lv + (p.getRandom().nextFloat() - 0.5F) * 0.16F;
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.fart(lv), SoundSource.PLAYERS, volume, pitch);

        // everyone who can see it (the player too) sees the cheeks move and the gas come out
        Net.FartFx fx = new Net.FartFx(p.getId(), lv);
        ServerPlayNetworking.send(p, fx);
        for (ServerPlayer viewer : PlayerLookup.tracking(p)) {
            if (viewer != p && ServerPlayNetworking.canSend(viewer, Net.FartFx.TYPE)) {
                ServerPlayNetworking.send(viewer, fx);
            }
        }
    }

    static void fling(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.syncVelocity = true;
    }
}
