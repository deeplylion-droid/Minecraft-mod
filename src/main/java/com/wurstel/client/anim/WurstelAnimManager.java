package com.wurstel.client.anim;

import com.wurstel.config.WurstelConfig;
import com.wurstel.item.WurstelBeltItem;
import com.wurstel.registry.ModSounds;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client side: keeps a {@link WurstelAnimState} for every entity wearing the belt, turns their
 * bare-hand swings into würstel slaps and plays the whoosh/slap sounds and particles.
 */
public final class WurstelAnimManager {
    private static final Map<LivingEntity, Tracked> TRACKED = new WeakHashMap<>();
    private static final WurstelAnimState REST = new WurstelAnimState();
    private static final Random RANDOM = new Random();

    private static final double ENTITY_REACH = 3.5;
    private static final double BLOCK_REACH = 4.5;

    private static final class Tracked {
        final WurstelAnimState anim = new WurstelAnimState();
        int impactIn = -1; // ticks until the würstel reaches the target, -1 = nothing pending
        @Nullable Vec3 impactPos;
        @Nullable Entity impactEntity;
        @Nullable BlockState impactBlock;
    }

    private WurstelAnimManager() {
    }

    /** Animation of this entity's würstel (a resting one if it is not tracked yet). */
    public static WurstelAnimState state(LivingEntity entity) {
        Tracked tracked = TRACKED.get(entity);
        return tracked != null ? tracked.anim : REST;
    }

    public static void clear() {
        TRACKED.clear();
    }

    /** Called at the end of every client tick. */
    public static void tick(ClientLevel level) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!WurstelBeltItem.isWearing(living)) {
                if (!TRACKED.isEmpty()) {
                    TRACKED.remove(living);
                }
                continue;
            }
            Tracked tracked = TRACKED.computeIfAbsent(living, e -> new Tracked());

            boolean mainHandSwing = living.swingingArm == null || living.swingingArm == InteractionHand.MAIN_HAND;
            boolean slaps = mainHandSwing && living.getMainHandItem().isEmpty();
            // swing() sets swingTime to -1 and the entity's tick moves it to 0: a new swing just started.
            if (slaps && living.swinging && living.swingTime == 0) {
                tracked.anim.startSlap(RANDOM);
                onSlapStart(level, living, tracked);
            }

            tracked.anim.tick(living.getYHeadRot(), living.getXRot(), (float) (living.getY() - living.yo),
                    living.walkAnimation.position(), living.walkAnimation.speed());

            if (tracked.impactIn >= 0 && tracked.impactIn-- == 0) {
                onImpact(level, living, tracked);
            }

            if (slaps) {
                // No arm punch: the würstel does the work. attackAnim is purely visual and is
                // recomputed every tick, so clearing it here hides the punch in first and third person.
                living.attackAnim = 0.0F;
                living.oAttackAnim = 0.0F;
            }
        }
    }

    private static void onSlapStart(ClientLevel level, LivingEntity living, Tracked tracked) {
        tracked.impactPos = null;
        tracked.impactEntity = null;
        tracked.impactBlock = null;

        HitResult hit = findTarget(living);
        if (hit instanceof EntityHitResult entityHit) {
            tracked.impactEntity = entityHit.getEntity();
            tracked.impactPos = entityHit.getLocation();
        } else if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            tracked.impactBlock = level.getBlockState(blockHit.getBlockPos());
            tracked.impactPos = blockHit.getLocation();
        }
        tracked.impactIn = WurstelAnimState.IMPACT_TICKS;

        if (WurstelConfig.CLIENT.sounds.get()) {
            level.playLocalSound(living.getX(), living.getY() + living.getBbHeight() * 0.45, living.getZ(),
                    ModSounds.WHOOSH.get(), living.getSoundSource(), 0.5F, 0.85F + RANDOM.nextFloat() * 0.3F, false);
        }
    }

    private static void onImpact(ClientLevel level, LivingEntity living, Tracked tracked) {
        boolean sounds = WurstelConfig.CLIENT.sounds.get();
        boolean particles = WurstelConfig.CLIENT.particles.get();

        if (tracked.impactEntity != null) {
            Vec3 p = tracked.impactEntity.getBoundingBox().getCenter();
            if (sounds) {
                level.playLocalSound(p.x, p.y, p.z, ModSounds.SLAP.get(), living.getSoundSource(),
                        1.0F, 0.85F + RANDOM.nextFloat() * 0.3F, false);
            }
            if (particles && living.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof WurstelBeltItem belt) {
                // Bits in the colour of the würstel: meat, diamond or netherite.
                ItemStack bitsItem = new ItemStack(belt.getWurstelMaterial().particleItem());
                ItemParticleOption bits = new ItemParticleOption(ParticleTypes.ITEM, bitsItem);
                for (int i = 0; i < 7; i++) {
                    level.addParticle(bits, p.x, p.y, p.z,
                            (RANDOM.nextDouble() - 0.5) * 0.3, RANDOM.nextDouble() * 0.25, (RANDOM.nextDouble() - 0.5) * 0.3);
                }
                level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 0.0, 0.0, 0.0);
            }
        } else if (tracked.impactBlock != null && tracked.impactPos != null) {
            Vec3 p = tracked.impactPos;
            if (sounds) {
                level.playLocalSound(p.x, p.y, p.z, ModSounds.SLAP.get(), living.getSoundSource(),
                        0.55F, 1.05F + RANDOM.nextFloat() * 0.25F, false);
            }
            BlockState block = tracked.impactBlock;
            if (particles && !block.isAir() && block.getRenderShape() != RenderShape.INVISIBLE) {
                BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, block);
                for (int i = 0; i < 5; i++) {
                    level.addParticle(dust,
                            p.x + (RANDOM.nextDouble() - 0.5) * 0.3,
                            p.y + (RANDOM.nextDouble() - 0.5) * 0.3,
                            p.z + (RANDOM.nextDouble() - 0.5) * 0.3,
                            0.0, 0.0, 0.0);
                }
            }
        }
        tracked.impactEntity = null;
        tracked.impactBlock = null;
        tracked.impactPos = null;
    }

    /** What the würstel is about to hit: your crosshair target, or a quick ray cast for others. */
    private static HitResult findTarget(LivingEntity living) {
        Minecraft mc = Minecraft.getInstance();
        if (living == mc.player && mc.hitResult != null) {
            return mc.hitResult;
        }
        Vec3 eye = living.getEyePosition();
        HitResult block = living.pick(BLOCK_REACH, 1.0F, false);
        double reach = ENTITY_REACH;
        if (block.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, block.getLocation().distanceTo(eye));
        }
        Vec3 look = living.getViewVector(1.0F).scale(reach);
        AABB area = living.getBoundingBox().expandTowards(look).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(living, eye, eye.add(look), area,
                e -> e != living && !e.isSpectator() && e.isPickable(), reach * reach);
        return entityHit != null ? entityHit : block;
    }
}
