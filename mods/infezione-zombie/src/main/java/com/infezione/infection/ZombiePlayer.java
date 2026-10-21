package com.infezione.infection;

import com.infezione.InfezioneMod;
import com.infezione.config.InfezioneConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Stage 5: a zombie player. Zombies leave them alone (unless hit first), the sun burns them unless they
 * wear a helmet, they hit harder, see in the dark, groan, and rotten flesh is good food for them. They
 * come back human by dying (or with the command): the antidote is too late.
 */
public final class ZombiePlayer {
    /** The mobs that take a zombie player for one of them. */
    public static final TagKey<EntityType<?>> FRIENDS = TagKey.create(Registries.ENTITY_TYPE, InfezioneMod.id("zombie_friends"));

    private ZombiePlayer() {
    }

    /** Just turned: the zombies around forget them. */
    static void start(ServerPlayer player) {
        start(player, true);
    }

    static void start(ServerPlayer player, boolean justTurned) {
        if (InfezioneConfig.SERVER.nightVision.get()) {
            nightVision(player);
        }
        if (justTurned && InfezioneConfig.SERVER.zombiesAreFriends.get()) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0),
                    m -> m.getTarget() == player && m.getType().is(FRIENDS))) {
                mob.setTarget(null);
            }
        }
    }

    /** Human again: their night vision goes. */
    static void stop(ServerPlayer player) {
        MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
        if (vision != null && vision.isAmbient() && !vision.showIcon()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    static void tick(ServerPlayer player) {
        if (InfezioneConfig.SERVER.nightVision.get() && player.tickCount % 100 == 0) {
            nightVision(player);
        }
        if (InfezioneConfig.SERVER.burnsInSun.get() && !player.isCreative() && !player.isSpectator()
                && player.level().getGameTime() >= Infection.of(player).sunSafeUntil() && sunBurnTick(player)) {
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!helmet.isEmpty()) {
                if (helmet.isDamageableItem()) {
                    helmet.hurtAndBreak(player.getRandom().nextInt(2), player, p -> p.broadcastBreakEvent(EquipmentSlot.HEAD));
                }
            } else {
                if (!player.isOnFire()) {
                    player.displayClientMessage(Component.translatable("infezione.zombie.sun"), true);
                }
                player.setSecondsOnFire(8);
            }
        }
    }

    /** Long enough that it never blinks; ambient and without an icon, so it is known to be ours. */
    private static void nightVision(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
    }

    /** The same test as a zombie's ({@code Mob.isSunBurnTick}). */
    private static boolean sunBurnTick(ServerPlayer player) {
        if (!player.level().isDay()) {
            return false;
        }
        float light = player.getLightLevelDependentMagicValue();
        BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        boolean wet = player.isInWaterRainOrBubble() || player.isInPowderSnow || player.wasInPowderSnow;
        return light > 0.5F && player.getRandom().nextFloat() * 30.0F < (light - 0.4F) * 2.0F && !wet
                && player.level().canSeeSky(eyes);
    }

    /**
     * Zombies do not go after a zombie player, unless the player hit them first: they go after the nearest
     * human they can see instead (their targeting always picks the nearest player, often the zombie one).
     */
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(event.getNewTarget() instanceof ServerPlayer player) || !mob.getType().is(FRIENDS)
                || !InfezioneConfig.SERVER.zombiesAreFriends.get() || !isZombie(player) || mob.getLastHurtByMob() == player) {
            return;
        }
        LivingEntity human = null;
        if (mob instanceof Mob hunter) {
            TargetingConditions humans = TargetingConditions.forCombat()
                    .range(hunter.getAttributeValue(Attributes.FOLLOW_RANGE))
                    .selector(e -> !(e instanceof ServerPlayer p && isZombie(p)));
            human = hunter.level().getNearestPlayer(humans, hunter);
        }
        event.setNewTarget(human);
    }

    private static boolean isZombie(ServerPlayer player) {
        return Infection.of(player).stage() == Stages.ZOMBIE;
    }

    /** Rotten flesh does not make a zombie sick, and it feeds them well. */
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().is(Items.ROTTEN_FLESH)
                && Infection.of(player).stage() == Stages.ZOMBIE) {
            player.removeEffect(MobEffects.HUNGER);
            player.getFoodData().eat(4, 0.6F);
        }
    }

    /** A zombie player sounds like a zombie when hurt. */
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Infection.of(player).stage() == Stages.ZOMBIE) {
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ZOMBIE_HURT,
                    SoundSource.PLAYERS, 1.0F, 0.8F + player.getRandom().nextFloat() * 0.3F);
        }
    }
}
