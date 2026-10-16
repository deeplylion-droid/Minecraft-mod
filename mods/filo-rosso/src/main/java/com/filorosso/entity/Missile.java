package com.filorosso.entity;

import com.filorosso.data.Couple;
import com.filorosso.server.Links;
import com.filorosso.server.ModDamage;
import com.filorosso.server.Tether;
import com.filorosso.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Recisione's missiles: black glitching boxes that fall from the sky. A dark red ring on the ground
 * shows where they will land; they burst there, and they cut every thread they fly through.
 */
public class Missile extends ThrowableProjectile {
    public static final double RADIUS = 3.0;
    private static final float DAMAGE = 7.0F;
    private static final int CUT_TICKS = 20 * 8;

    /** Where it is going to land (server only, for the warning ring). */
    private Vec3 landing;
    private final Set<Couple> cutAlready = new HashSet<>();

    public Missile(EntityType<? extends Missile> type, Level level) {
        super(type, level);
    }

    public Missile(Level level, LivingEntity owner) {
        this(ModEntities.MISSILE, level);
        this.setOwner(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    protected float getAirDrag() {
        return 1.0F;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof Recisione) && !(entity instanceof Missile) && !(entity instanceof Servant);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            // a trail of black smoke and dark red dust
            Level level = this.level();
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.02, 0.0);
            }
            level.addParticle(new DustParticleOptions(0x6a0a10, 1.6F), this.getRandomX(0.8), this.getRandomY(), this.getRandomZ(0.8), 0.0, 0.0, 0.0);
            return;
        }
        if (this.tickCount > 120) {
            this.burst(level, this.position());
            return;
        }
        if (this.landing == null) {
            Vec3 from = this.position();
            HitResult hit = level.clip(new ClipContext(from, from.add(this.getDeltaMovement().normalize().scale(64.0)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY, this));
            this.landing = hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
            if (this.landing == null) {
                this.landing = Vec3.ZERO;
            }
        }
        if (this.landing != Vec3.ZERO && this.tickCount % 2 == 0) {
            // the warning: a dark red ring where it will land
            int n = 14;
            double spin = this.tickCount * 0.15;
            for (int i = 0; i < n; i++) {
                double a = spin + i * Math.PI * 2.0 / n;
                level.sendParticles(new DustParticleOptions(0x9a1018, 1.3F), this.landing.x + Math.cos(a) * RADIUS * 0.8, this.landing.y + 0.15,
                        this.landing.z + Math.sin(a) * RADIUS * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        // threads it flies through are severed
        Vec3 here = this.position();
        for (Links.Pair pair : Links.active(level.getServer())) {
            Couple couple = pair.couple();
            if (couple == null || this.cutAlready.contains(couple) || pair.a().level() != level || pair.a().distanceToSqr(this) > 24 * 24) {
                continue;
            }
            if (Tether.distanceToThread(pair.a(), pair.b(), here) < 1.1) {
                this.cutAlready.add(couple);
                Tether.cut(level.getServer(), couple, CUT_TICKS);
                level.sendParticles(ParticleTypes.SQUID_INK, here.x, here.y, here.z, 16, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            this.burst(level, hit.getLocation());
        }
    }

    private void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 2, 0.4, 0.2, 0.4, 0.0);
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.4, at.z, 40, 0.8, 0.4, 0.8, 0.18);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.4, at.z, 16, 0.8, 0.3, 0.8, 0.06);
        level.sendParticles(new DustParticleOptions(0x9a1018, 2.0F), at.x, at.y + 0.5, at.z, 20, 1.2, 0.4, 1.2, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.MISSILE_HIT, SoundSource.HOSTILE, 3.0F, 0.85F + this.random.nextFloat() * 0.3F);
        DamageSource source = level.damageSources().source(ModDamage.VOID, this, this.getOwner());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS))) {
            if (e instanceof Recisione || e instanceof Servant || !e.isAlive()) {
                continue;
            }
            double d = e.getBoundingBox().distanceToSqr(at);
            if (d > RADIUS * RADIUS) {
                continue;
            }
            float k = (float) (1.0 - Math.sqrt(d) / RADIUS * 0.6);
            e.hurtServer(level, source, DAMAGE * k);
            Vec3 push = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
            push = (push.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : push.normalize().scale(0.7 * k)).add(0.0, 0.45 * k, 0.0);
            if (e instanceof ServerPlayer p) {
                Recisione.fling(p, p.getDeltaMovement().add(push));
            } else {
                e.push(push);
            }
        }
        // and the threads close to it
        for (Links.Pair pair : Links.active(level.getServer())) {
            Couple couple = pair.couple();
            if (couple != null && !this.cutAlready.contains(couple) && pair.a().level() == level
                    && Tether.distanceToThread(pair.a(), pair.b(), at) < RADIUS * 0.8) {
                this.cutAlready.add(couple);
                Tether.cut(level.getServer(), couple, CUT_TICKS);
            }
        }
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }
}
