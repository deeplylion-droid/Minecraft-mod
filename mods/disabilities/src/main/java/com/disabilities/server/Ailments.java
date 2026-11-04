package com.disabilities.server;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Picks;
import com.disabilities.condition.Status;
import com.disabilities.net.EpisodeKind;
import com.disabilities.net.Net;
import com.disabilities.sound.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Everything the server does over time: sleep attacks, tics, freezes, stumbles, sneezes, bleeding,
 * sunburn, breath and stamina. Sounds and particles are sent to everyone nearby, so the others see
 * and hear what happens to you.
 */
public final class Ailments {
    private Ailments() {
    }

    /** Obesity: ticks of sprinting before you run out of breath. */
    private static final float STAMINA = 80.0F;
    /** Tourette: harmless words shouted by vocal tics (translation keys disabilities.tic.word.N). */
    public static final int TIC_WORDS = 24;
    /** Tourette: the sounds of the vocal tics, in the same order as {@link ModSounds#VOCAL_TICS}. */
    private static final String[] TIC_SOUNDS = {"hm", "whistle", "hey", "click", "sniff", "grunt"};

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    /** Server-only bookkeeping for one player. */
    static final class Tracker {
        long nextSleep;
        long nextTic;
        long nextFreeze;
        long nextDrop;
        long nextShuffle;
        long nextStumble;
        long lastSneeze;
        long lastMessage;
        boolean yawned;
        float stamina = STAMINA;
        boolean breathless;
        int episodes;
        double lastX;
        double lastY;
        double lastZ;
        boolean hasLast;
        boolean moving;
        double dy;
        int caneTimer;
        int sunTicks;
        float allergen;
        // 1.1.0
        long nextSeizure;
        long auraAt;
        int lastBolt;
        boolean wasSeizing;
        long nextAlien;
        int giantStep;
        long lastBonk;
        float bpm = 72.0F;
        int sentBpm;
        int cpr;
        long lastCpr;
        boolean wasArrested;
        float anxiety;
        long lastPanic;
        boolean wasPanicking;
        long lastGlasses;
        long nextNod;
        long nextLost;
        long nextCough;
        long nextNosebleed;
        long nextItch;
        boolean rough;
        int stepTimer;
        boolean wasOnGround = true;
        float fallStart;
        boolean swollen;
        long brokenLegShown;
        long brokenArmShown;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Ailments::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Chooser.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRACKERS.remove(handler.player.getUUID()));

        // nobody can hurt you while you are choosing
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || !Chooser.isChoosing(player));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (entity instanceof ServerPlayer player && damage > 0.0F) {
                afterDamage(player, source, damage);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            TRACKERS.remove(newPlayer.getUUID());
            newPlayer.setAttached(ModData.STATUS, Status.NONE);
            MinecraftServer server = newPlayer.level().getServer();
            if (!alive && server.getGameRules().get(ModRules.REROLL_ON_DEATH) && !ModData.picks(newPlayer).isEmpty()) {
                Chooser.clear(newPlayer, true);
                return;
            }
            Body.refresh(newPlayer);
            if (!alive) {
                Chooser.giveAids(newPlayer, true);
            }
        });
        // right-click a friend: wake them up, put them in the recovery position, CPR
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() || !(entity instanceof ServerPlayer target) || !(player instanceof ServerPlayer helper)
                    || hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            long now = level.getGameTime();
            Status status = ModData.status(target);
            if (status.arrested(now)) {
                Heart.compress(target, helper);
                return InteractionResult.SUCCESS;
            }
            if (status.seizing(now)) {
                Epilepsy.secure(target, helper);
                return InteractionResult.SUCCESS;
            }
            if (status.asleep(now)) {
                wake(target, helper);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        // hitting things makes a weak heart race
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer attacker && ModData.has(attacker, Condition.WEAK_HEART)) {
                Heart.jolt(attacker, 4.0F);
            }
            return InteractionResult.PASS;
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.Bump.TYPE, (payload, context) -> Giant.bump(context.player(), payload.kind()));
    }

    /** Resets the timers (new conditions, respawn). */
    public static void forget(ServerPlayer player) {
        TRACKERS.remove(player.getUUID());
    }

    static Tracker tracker(ServerPlayer player) {
        return TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
    }

    static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    static boolean episodesOn(ServerPlayer player) {
        return player.level().getServer().getGameRules().get(ModRules.EPISODES);
    }

    private static void tick(MinecraftServer server) {
        Chooser.tick(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Picks picks = ModData.picks(player);
            if (picks.isEmpty()) {
                continue;
            }
            try {
                tickPlayer(player, picks, tracker(player));
            } catch (RuntimeException e) {
                com.disabilities.Disabilities.LOGGER.error("Disabilities: error while ticking {}", player.getGameProfile().name(), e);
            }
        }
    }

    private static void tickPlayer(ServerPlayer player, Picks picks, Tracker t) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        RandomSource random = player.getRandom();
        boolean episodes = episodesOn(player);

        if (t.hasLast) {
            double dx = player.getX() - t.lastX;
            double dz = player.getZ() - t.lastZ;
            t.dy = player.getY() - t.lastY;
            t.moving = dx * dx + dz * dz > 0.0016;
        }
        t.lastX = player.getX();
        t.lastY = player.getY();
        t.lastZ = player.getZ();
        t.hasLast = true;

        Status status = ModData.status(player);
        boolean asleep = status.asleep(now);
        boolean lying = ModData.lying(player);

        // attributes follow the episodes: refresh when one starts or ends
        int bits = (asleep ? 1 : 0) | (status.frozen(now) ? 2 : 0) | (status.winded(now) ? 4 : 0) | (t.breathless ? 8 : 0)
                | (status.seizing(now) ? 16 : 0) | (status.arrested(now) ? 32 : 0) | (status.panicking(now) ? 64 : 0)
                | (status.brokenArm(now) ? 128 : 0) | (status.brokenLeg(now) ? 256 : 0) | (status.confused(now) ? 512 : 0)
                | (t.rough ? 1024 : 0);
        if (bits != t.episodes) {
            if ((t.episodes & 1) != 0 && !asleep) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GASP, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            t.episodes = bits;
            Body.refresh(player);
        }

        if (picks.has(Condition.BLIND)) {
            tickBlind(player, t);
        }
        if (picks.has(Condition.ALBINISM)) {
            tickAlbinism(player, t, now);
        }
        if (picks.has(Condition.ONE_ARM)) {
            tickOneArm(player);
        }
        if (picks.has(Condition.ONE_LEG)) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            tickCrutches(player, t);
        }
        if (picks.has(Condition.WHEELCHAIR)) {
            tickWheelchair(player, t, now);
        }
        if (picks.has(Condition.OBESITY)) {
            tickObesity(player, t, now, status);
        }
        if (picks.has(Condition.ASTHMA)) {
            tickAsthma(player, t, now);
        }
        if (status.bleeding(now)) {
            tickBleeding(player, now);
        }
        if (picks.has(Condition.EPILEPSY)) {
            Epilepsy.tick(player, t, now, ModData.status(player), episodes);
        }
        if (picks.has(Condition.WEAK_HEART)) {
            Heart.tick(player, t, now, ModData.status(player));
        }
        if (picks.has(Condition.CLAUSTROPHOBIA) && !lying) {
            Claustrophobia.tick(player, t, now, ModData.status(player));
        }
        if (picks.has(Condition.BRITTLE_BONES)) {
            Bones.tick(player, t, now, ModData.status(player));
        }
        if (picks.has(Condition.GIGANTISM) && !lying) {
            Giant.tick(player, t, now);
        }
        if (asleep) {
            if (now % 50 == 0) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SNORE, SoundSource.PLAYERS, 1.0F,
                        0.9F + random.nextFloat() * 0.2F);
            }
            if (player.isInWater() || player.isInLava() || player.isOnFire()) {
                wake(player, null);
            }
            player.setSprinting(false);
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
        }
        t.wasOnGround = player.onGround();
        if (!episodes || lying) {
            return;
        }
        if (picks.has(Condition.ALIEN_HAND)) {
            AlienHand.tick(player, t, now, true);
        }
        if (picks.has(Condition.NARCOLEPSY)) {
            tickNarcolepsy(player, t, now);
        }
        if (picks.has(Condition.TOURETTE)) {
            tickTourette(player, t, now);
        }
        if (picks.has(Condition.PARKINSON)) {
            tickParkinson(player, t, now, status);
        }
        if (picks.has(Condition.AMNESIA)) {
            tickAmnesia(player, t, now);
        }
        if (picks.has(Condition.VERTIGO)) {
            if (t.nextStumble == 0) {
                t.nextStumble = now + between(random, 1200, 3000);
            }
            if (now >= t.nextStumble) {
                if (player.onGround() && t.moving && !status.frozen(now)) {
                    stumble(player);
                    t.nextStumble = now + between(random, 1200, 3000);
                } else {
                    t.nextStumble = now + 60;
                }
            }
        }
        if (picks.has(Condition.ALLERGY) && now % 20 == 0) {
            tickAllergy(player, t, now);
        }
        if (picks.has(Condition.HEMOPHILIA)) {
            if (t.nextNosebleed == 0) {
                t.nextNosebleed = now + between(random, 6000, 9600);
            }
            if (now >= t.nextNosebleed) {
                t.nextNosebleed = now + between(random, 6000, 9600);
                nosebleed(player);
            }
        }
    }

    // ---------------------------------------------------------------- senses

    private static void tickBlind(ServerPlayer player, Tracker t) {
        boolean cane = player.getMainHandItem().is(com.disabilities.item.ModItems.WHITE_CANE)
                || player.getOffhandItem().is(com.disabilities.item.ModItems.WHITE_CANE);
        if (!cane || !t.moving || !player.onGround()) {
            t.caneTimer = 0;
            return;
        }
        if (++t.caneTimer >= 11) {
            t.caneTimer = 0;
            Vec3 tip = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8));
            player.level().playSound(null, tip.x, tip.y, tip.z, ModSounds.CANE_TAP, SoundSource.PLAYERS, 0.55F,
                    0.95F + player.getRandom().nextFloat() * 0.15F);
        }
    }

    private static boolean inSun(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos head = BlockPos.containing(player.getEyePosition());
        return level.isBrightOutside() && !level.isRainingAt(head) && level.canSeeSky(head) && !player.isInWater();
    }

    private static void tickAlbinism(ServerPlayer player, Tracker t, long now) {
        if (!inSun(player) || !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            t.sunTicks = 0;
            return;
        }
        t.sunTicks++;
        ServerLevel level = player.level();
        if (t.sunTicks % 6 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + player.getBbHeight() * 0.8, player.getZ(), 2,
                    0.2, 0.15, 0.2, 0.01);
        }
        if (t.sunTicks % 8 == 0) {
            level.sendParticles(new DustParticleOptions(0xE8603C, 0.8F), player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 2,
                    0.22, 0.3, 0.22, 0.0);
        }
        if (t.sunTicks >= 60 && t.sunTicks % 40 == 0) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
            // the longer you stay in the sun, the worse it burns
            player.hurtServer(level, level.damageSources().source(ModDamage.SUNBURN), t.sunTicks >= 400 ? 2.0F : 1.0F);
            send(player, new Net.Episode(EpisodeKind.SUNBURN, 0));
            if (now - t.lastMessage > 600) {
                t.lastMessage = now;
                player.sendOverlayMessage(Component.translatable("disabilities.msg.sunburn").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // ---------------------------------------------------------------- body

    /** Two-handed items: no bows, crossbows, tridents or spears with one arm. */
    public static boolean twoHanded(ItemStack stack) {
        return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT) || stack.is(ItemTags.SPEARS);
    }

    private static void tickOneArm(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            ItemStack stack = offhand.copy();
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            player.sendOverlayMessage(Component.translatable("disabilities.msg.one_arm").withStyle(ChatFormatting.YELLOW));
        }
        if (player.isUsingItem() && twoHanded(player.getUseItem())) {
            player.stopUsingItem();
        }
    }

    private static void tickObesity(ServerPlayer player, Tracker t, long now, Status status) {
        ServerLevel level = player.level();
        if (!player.onGround()) {
            t.fallStart = Math.max(t.fallStart, (float) player.fallDistance);
        } else {
            if (!t.wasOnGround && t.fallStart >= 1.0F) {
                // a heavy landing everybody hears and sees
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.THUD, SoundSource.PLAYERS, 1.0F,
                        0.85F + player.getRandom().nextFloat() * 0.2F);
                BlockState below = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ()));
                if (!below.isAir()) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below), player.getX(), player.getY() + 0.05,
                            player.getZ(), 20, 0.6, 0.05, 0.6, 0.15);
                }
            }
            t.fallStart = 0.0F;
        }
        if (status.winded(now)) {
            player.setSprinting(false);
            if (now % 22 == 0) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.PANT, SoundSource.PLAYERS, 0.9F,
                        0.95F + player.getRandom().nextFloat() * 0.1F);
            }
            if (now % 8 == 0) {
                level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + player.getBbHeight() * 0.85, player.getZ(), 2,
                        0.25, 0.1, 0.25, 0.0);
            }
            return;
        }
        if (player.isSprinting()) {
            t.stamina -= 1.0F;
        } else {
            t.stamina = Math.min(STAMINA, t.stamina + 0.5F);
        }
        if (t.stamina <= 0.0F) {
            t.stamina = STAMINA * 0.5F;
            player.setSprinting(false);
            setStatus(player, status.withWinded(now + 140));
            player.sendOverlayMessage(Component.translatable("disabilities.msg.winded").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static boolean isBreathless(ServerPlayer player) {
        Tracker t = TRACKERS.get(player.getUUID());
        return t != null && t.breathless;
    }

    private static void tickAsthma(ServerPlayer player, Tracker t, long now) {
        ServerLevel level = player.level();
        int max = player.getMaxAirSupply();
        int drain = 0;
        if (player.isSprinting()) {
            drain += 3;
        }
        if (player.onClimbable() && t.dy > 0.05) {
            drain += 2;
        }
        if (player.isSwimming()) {
            drain += 1;
        }
        if (drain > 0) {
            player.setAirSupply(Math.max(0, player.getAirSupply() - drain));
        }
        if (!t.breathless && player.getAirSupply() <= max * 0.15F) {
            t.breathless = true;
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GASP, SoundSource.PLAYERS, 1.0F, 1.1F);
            player.sendOverlayMessage(Component.translatable("disabilities.msg.breathless").withStyle(ChatFormatting.RED));
        } else if (t.breathless && player.getAirSupply() >= max * 0.5F) {
            t.breathless = false;
        }
        if (now >= t.nextCough && now % 20 == 0) {
            boolean smoke = irritants(player);
            if (smoke || player.getRandom().nextFloat() < 0.02F) {
                cough(player);
                t.nextCough = now + (smoke ? between(player.getRandom(), 80, 200) : between(player.getRandom(), 1200, 2400));
            }
        }
        if (t.breathless) {
            player.setSprinting(false);
            if (now % 30 == 0) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.WHEEZE, SoundSource.PLAYERS, 0.9F,
                        0.95F + player.getRandom().nextFloat() * 0.1F);
            }
        }
    }

    /** Smoke, fire and dust around: a coughing fit now and then. */
    private static boolean irritants(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        RandomSource random = player.getRandom();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 40; i++) {
            pos.set(center.getX() + random.nextInt(9) - 4, center.getY() + random.nextInt(5) - 2, center.getZ() + random.nextInt(9) - 4);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
                return true;
            }
        }
        return player.isOnFire();
    }

    public static void cough(ServerPlayer player) {
        ServerLevel level = player.level();
        player.setAirSupply(Math.max(0, player.getAirSupply() - 60));
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.COUGH, SoundSource.PLAYERS, 1.0F,
                0.9F + player.getRandom().nextFloat() * 0.2F);
        Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.4));
        level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y - 0.1, mouth.z, 3, 0.05, 0.05, 0.05, 0.02);
        motion(player, com.disabilities.net.MotionKind.COUGH, 0);
        send(player, new Net.Episode(EpisodeKind.COUGH, 0));
    }

    /** True while the asthma rules hold the breath back (no recovery while you run or climb). */
    public static boolean exerting(Player player) {
        return player.isSprinting() || player.isSwimming() || player.onClimbable() && player.getDeltaMovement().y > 0.05;
    }

    /** Inhaler: all the breath back at once. */
    public static void catchBreath(ServerPlayer player) {
        player.setAirSupply(player.getMaxAirSupply());
        Tracker t = tracker(player);
        t.breathless = false;
        t.stamina = STAMINA;
        Status status = ModData.status(player);
        if (status.winded(player.level().getGameTime())) {
            setStatus(player, status.withWinded(0));
        }
    }

    /** Called when a player jumps: asthma and obesity pay for it. */
    public static void onJump(ServerPlayer player) {
        Picks picks = ModData.picks(player);
        if (picks.has(Condition.ASTHMA)) {
            player.setAirSupply(Math.max(0, player.getAirSupply() - 20));
        }
        if (picks.has(Condition.OBESITY)) {
            Tracker t = tracker(player);
            t.stamina -= 12.0F;
        }
        if (picks.has(Condition.WEAK_HEART)) {
            Heart.jolt(player, 7.0F);
        }
    }

    // ---------------------------------------------------------------- health

    private static void afterDamage(ServerPlayer player, DamageSource source, float damage) {
        long now = player.level().getGameTime();
        Picks picks = ModData.picks(player);
        if (picks.has(Condition.HEMOPHILIA) && !source.is(ModDamage.BLEEDING) && !source.is(ModDamage.SUNBURN) && damage >= 0.5F) {
            Status status = ModData.status(player);
            long base = Math.max(now, status.bleedUntil());
            long until = Math.min(now + 1200, base + 160 + (long) (damage * 20));
            if (!status.bleeding(now)) {
                player.sendOverlayMessage(Component.translatable("disabilities.msg.bleeding").withStyle(ChatFormatting.DARK_RED));
            }
            setStatus(player, status.withBleed(until));
        }
        if (picks.has(Condition.NARCOLEPSY) && episodesOn(player) && !ModData.lying(player) && safe(player)
                && player.getRandom().nextFloat() < 0.18F) {
            fallAsleep(player, 50, true);
        }
        Tracker t = tracker(player);
        if (picks.has(Condition.WEAK_HEART)) {
            Heart.jolt(player, 6.0F + damage * 1.5F);
        }
        if (picks.has(Condition.EPILEPSY) && episodesOn(player)) {
            Epilepsy.afterDamage(player, t, source, damage);
        }
        if (picks.has(Condition.BRITTLE_BONES)) {
            Bones.afterDamage(player, source, damage);
        }
        if (picks.has(Condition.TOURETTE) && episodesOn(player) && damage >= 2.0F && player.getRandom().nextFloat() < 0.5F) {
            // stress: a burst of tics
            t.nextTic = now + 4;
        }
        if (picks.has(Condition.MYOPIA) && damage >= 3.0F && now - t.lastGlasses > 400 && player.getRandom().nextFloat() < 0.35F) {
            glassesOff(player);
        }
    }

    /** Myopia: a hard hit knocks the glasses off for half a minute. */
    public static void glassesOff(ServerPlayer player) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        if (status.glassesOff(now)) {
            return;
        }
        tracker(player).lastGlasses = now;
        setStatus(player, status.withGlassesOff(now + 600));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
        bubble(player, Component.translatable("disabilities.bubble.glasses").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), 40);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.glasses_off").withStyle(ChatFormatting.AQUA));
    }

    private static void tickBleeding(ServerPlayer player, long now) {
        ServerLevel level = player.level();
        if (now % 3 == 0) {
            RandomSource random = player.getRandom();
            double y = player.getY() + player.getBbHeight() * (0.3 + random.nextDouble() * 0.5);
            DustParticleOptions blood = new DustParticleOptions(0x8A0303, 1.1F);
            // the others see the blood on the body; the bleeding player only sees drops at their feet
            for (ServerPlayer viewer : level.players()) {
                if (viewer == player) {
                    level.sendParticles(viewer, blood, false, false, player.getX(), player.getY() + 0.15, player.getZ(), 1, 0.25, 0.05, 0.25, 0.0);
                } else if (viewer.distanceToSqr(player) < 48 * 48) {
                    level.sendParticles(viewer, blood, false, false, player.getX(), player.getY() + 0.08, player.getZ(), 2, 0.3, 0.02, 0.3, 0.0);
                    level.sendParticles(viewer, blood, false, false, player.getX(), y, player.getZ(), 2, 0.22, 0.1, 0.22, 0.0);
                    level.sendParticles(viewer, ParticleTypes.DRIPPING_DRIPSTONE_LAVA, false, false, player.getX(), y, player.getZ(), 1, 0.2, 0.1, 0.2,
                            0.0);
                }
            }
        }
        if (now % 40 == 0) {
            player.hurtServer(level, level.damageSources().source(ModDamage.BLEEDING), 1.0F);
        }
    }

    public static boolean canBandage(Player patient) {
        return patient.getHealth() < patient.getMaxHealth() || ModData.status(patient).bleeding(patient.level().getGameTime());
    }

    public static void bandage(ServerPlayer patient, ServerPlayer nurse) {
        Status status = ModData.status(patient);
        long now = patient.level().getGameTime();
        if (status.bleeding(now)) {
            setStatus(patient, status.withBleed(0));
            patient.sendOverlayMessage(Component.translatable("disabilities.msg.bandaged").withStyle(ChatFormatting.GREEN));
            if (nurse != patient) {
                nurse.sendOverlayMessage(Component.translatable("disabilities.msg.bandaged_other", patient.getDisplayName())
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        patient.heal(2.0F);
        patient.level().playSound(null, patient.getX(), patient.getY() + 1, patient.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
        patient.level().sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight(), patient.getZ(), 2,
                0.3, 0.2, 0.3, 0.0);
    }

    /** Hemophilia: while you bleed nothing heals you, only a bandage. */
    public static boolean blocksHealing(Player player) {
        return ModData.has(player, Condition.HEMOPHILIA) && ModData.status(player).bleeding(player.level().getGameTime());
    }

    // ---------------------------------------------------------------- neuro

    static boolean safe(ServerPlayer player) {
        return player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger() && !player.isFallFlying()
                && !player.getAbilities().flying && !player.isOnFire() && !player.isSleeping() && player.isAlive();
    }

    private static void tickNarcolepsy(ServerPlayer player, Tracker t, long now) {
        RandomSource random = player.getRandom();
        if (t.nextSleep == 0) {
            t.nextSleep = now + between(random, 3000, 6000);
        }
        if (t.nextNod == 0) {
            t.nextNod = now + between(random, 600, 1400);
        }
        if (now >= t.nextNod && now < t.nextSleep - 200) {
            // a moment with the eyes closed: the head drops, then jerks back up
            t.nextNod = now + between(random, 600, 1400);
            motion(player, com.disabilities.net.MotionKind.NOD, 0);
            send(player, new Net.Episode(EpisodeKind.NOD, 0));
            bubble(player, Component.literal("zz").withStyle(ChatFormatting.AQUA), 30);
        }
        if (!t.yawned && now >= t.nextSleep - 60) {
            t.yawned = true;
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.YAWN, SoundSource.PLAYERS, 1.0F, 1.0F);
            motion(player, com.disabilities.net.MotionKind.YAWN, 0);
            player.sendOverlayMessage(Component.translatable("disabilities.msg.sleepy").withStyle(ChatFormatting.AQUA));
        }
        if (now >= t.nextSleep) {
            if (safe(player)) {
                fallAsleep(player, between(random, 100, 160), false);
                t.nextSleep = now + between(random, 3000, 6000);
                t.yawned = false;
            } else {
                t.nextSleep = now + 40;
            }
        }
    }

    public static boolean fallAsleep(ServerPlayer player, int ticks, boolean cataplexy) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        if (status.asleep(now)) {
            return false;
        }
        setStatus(player, status.withSleep(now + ticks));
        player.setSprinting(false);
        player.stopUsingItem();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendOverlayMessage(Component.translatable(cataplexy ? "disabilities.msg.cataplexy" : "disabilities.msg.asleep")
                .withStyle(ChatFormatting.AQUA));
        Body.refresh(player);
        return true;
    }

    public static void wake(ServerPlayer player, ServerPlayer waker) {
        Status status = ModData.status(player);
        setStatus(player, status.withSleep(0));
        Body.refresh(player);
        if (waker != null) {
            player.sendOverlayMessage(Component.translatable("disabilities.msg.woken_by", waker.getDisplayName()).withStyle(ChatFormatting.AQUA));
            waker.sendOverlayMessage(Component.translatable("disabilities.msg.woke", player.getDisplayName()).withStyle(ChatFormatting.AQUA));
        }
    }

    private static void tickTourette(ServerPlayer player, Tracker t, long now) {
        RandomSource random = player.getRandom();
        if (t.nextTic == 0) {
            t.nextTic = now + between(random, 100, 300);
        }
        if (now < t.nextTic) {
            return;
        }
        t.nextTic = now + (random.nextFloat() < 0.25F ? between(random, 8, 30) : between(random, 160, 480));
        tic(player, -1);
    }

    /** A tic: {@code kind} -1 for a random one, 0-6 motor, 7 vocal sound, 8 word. */
    public static void tic(ServerPlayer player, int kind) {
        RandomSource random = player.getRandom();
        ServerLevel level = player.level();
        if (kind < 0) {
            float r = random.nextFloat();
            kind = r < 0.55F ? -2 : r < 0.85F ? 7 : 8;
        }
        if (kind == -2) {
            float r = random.nextFloat();
            EpisodeKind motor = r < 0.25F ? EpisodeKind.TIC_HEAD : r < 0.40F ? EpisodeKind.TIC_JUMP : r < 0.60F ? EpisodeKind.TIC_SWING
                    : r < 0.70F ? EpisodeKind.TIC_CROUCH : r < 0.80F ? EpisodeKind.TIC_SPIN : r < 0.95F ? EpisodeKind.TIC_BLINK : EpisodeKind.TIC_HIT;
            send(player, new Net.Episode(motor, random.nextInt(1000)));
        } else if (kind < 7) {
            EpisodeKind motor = EpisodeKind.values()[EpisodeKind.TIC_HEAD.ordinal() + kind];
            send(player, new Net.Episode(motor, random.nextInt(1000)));
        } else if (kind == 7) {
            int i = random.nextInt(ModSounds.VOCAL_TICS.length);
            SoundEvent sound = ModSounds.VOCAL_TICS[i];
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, 1.2F,
                    0.9F + random.nextFloat() * 0.2F);
            bubble(player, Component.translatable("disabilities.tic.sound." + TIC_SOUNDS[i]).withStyle(ChatFormatting.RED), 30);
        } else {
            Component word = Component.translatable("disabilities.tic.word." + random.nextInt(TIC_WORDS));
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.TIC_HEY, SoundSource.PLAYERS, 1.2F,
                    0.8F + random.nextFloat() * 0.4F);
            Component line = Component.translatable("chat.type.text", player.getDisplayName(), word.copy().withStyle(ChatFormatting.RED));
            for (ServerPlayer other : level.players()) {
                if (other.distanceToSqr(player) < 48 * 48) {
                    other.sendSystemMessage(line);
                }
            }
            bubble(player, word.copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD), 50);
        }
    }

    private static void tickParkinson(ServerPlayer player, Tracker t, long now, Status status) {
        RandomSource random = player.getRandom();
        if (t.nextFreeze == 0) {
            t.nextFreeze = now + between(random, 700, 1600);
        }
        if (!status.frozen(now) && now >= t.nextFreeze) {
            if (player.onGround() && t.moving) {
                freeze(player, between(random, 40, 80));
                t.nextFreeze = now + between(random, 700, 1600);
            } else {
                t.nextFreeze = now + 40;
            }
        }
        if (t.nextDrop == 0) {
            t.nextDrop = now + between(random, 1800, 4200);
        }
        if (now >= t.nextDrop) {
            if (!player.getMainHandItem().isEmpty()) {
                dropHeld(player, "disabilities.msg.slipped");
                t.nextDrop = now + between(random, 1800, 4200);
            } else {
                t.nextDrop = now + 200;
            }
        }
    }

    public static void freeze(ServerPlayer player, int ticks) {
        long now = player.level().getGameTime();
        setStatus(player, ModData.status(player).withFreeze(now + ticks));
        player.setSprinting(false);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.frozen").withStyle(ChatFormatting.YELLOW));
        Body.refresh(player);
    }

    private static void dropHeld(ServerPlayer player, String message) {
        if (player.getMainHandItem().isEmpty()) {
            return;
        }
        player.drop(false);
        player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.YELLOW));
    }

    /** Amnesia: the main inventory is shuffled. */
    public static void forgetInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> items = new ArrayList<>();
        int filled = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            items.add(stack);
            if (!stack.isEmpty()) {
                filled++;
            }
        }
        if (filled >= 2) {
            Collections.shuffle(items, new Random(player.getRandom().nextLong()));
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                inventory.setItem(i, items.get(i));
            }
            inventory.setChanged();
        }
        long now = player.level().getGameTime();
        setStatus(player, ModData.status(player).withConfused(now + 100));
        send(player, new Net.Episode(EpisodeKind.FORGOT, 0));
        player.sendOverlayMessage(Component.translatable("disabilities.msg.forgot").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Vertigo: you trip sideways. */
    public static void stumble(ServerPlayer player) {
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) {
            look = new Vec3(0, 0, 1);
        }
        look = look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x).scale(random.nextBoolean() ? 1 : -1);
        Vec3 push = side.scale(0.45).add(look.scale(0.15)).add(0, 0.18, 0);
        player.setDeltaMovement(player.getDeltaMovement().add(push));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 1.0F, 1.2F);
        send(player, new Net.Episode(EpisodeKind.STUMBLE, random.nextBoolean() ? 1 : -1));
        player.sendOverlayMessage(Component.translatable("disabilities.msg.stumble").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** How much pollen or hay a block gives off (0 = none). */
    private static float allergen(BlockState state) {
        if (state.is(BlockTags.FLOWERS) || state.is(Blocks.HAY_BLOCK)) {
            return 1.0F;
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.BUSH)) {
            return 0.6F;
        }
        return state.is(BlockTags.LEAVES) ? 0.35F : 0.0F;
    }

    private static void tickAllergy(ServerPlayer player, Tracker t, long now) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        BlockPos center = player.blockPosition();
        float found = 0.0F;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 48; i++) {
            pos.set(center.getX() + random.nextInt(11) - 5, center.getY() + random.nextInt(6) - 2, center.getZ() + random.nextInt(11) - 5);
            found += allergen(level.getBlockState(pos));
        }
        int animals = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(6)).size();
        float exposure = Mth.clamp(found / 8.0F + animals * 0.25F, 0.0F, 1.0F);
        t.allergen = t.allergen * 0.7F + exposure * 0.3F;
        if (t.allergen > 0.12F && now - t.lastSneeze > 80 && random.nextFloat() < t.allergen * 0.14F) {
            t.lastSneeze = now;
            sneeze(player);
        }
        // red, swollen eyes and nose while it lasts; the itch makes you rub them
        boolean swollen = t.swollen ? t.allergen > 0.12F : t.allergen > 0.3F;
        if (swollen != t.swollen) {
            t.swollen = swollen;
            setStatus(player, ModData.status(player).withFlag(Status.SWOLLEN, swollen));
        }
        if (swollen && now >= t.nextItch) {
            t.nextItch = now + between(random, 160, 400);
            motion(player, com.disabilities.net.MotionKind.ITCH, 0);
        }
    }

    public static void sneeze(ServerPlayer player) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SNEEZE, SoundSource.PLAYERS, 1.3F,
                0.9F + random.nextFloat() * 0.2F);
        Vec3 look = player.getLookAngle();
        Vec3 nose = player.getEyePosition().add(look.scale(0.5));
        for (int i = 0; i < 10; i++) {
            Vec3 v = look.scale(0.15 + random.nextDouble() * 0.2).add(random.nextGaussian() * 0.03, random.nextGaussian() * 0.03 - 0.02,
                    random.nextGaussian() * 0.03);
            level.sendParticles(ParticleTypes.SNEEZE, nose.x, nose.y, nose.z, 0, v.x, v.y, v.z, 1.0);
        }
        Vec3 back = look.multiply(1, 0, 1).scale(-0.25).add(0, 0.05, 0);
        player.setDeltaMovement(player.getDeltaMovement().add(back));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        send(player, new Net.Episode(EpisodeKind.SNEEZE, 0));
        if (!player.getMainHandItem().isEmpty() && random.nextFloat() < 0.2F) {
            dropHeld(player, "disabilities.msg.sneeze_drop");
        }
    }

    // ---------------------------------------------------------------- 1.1.0: more to see and to hear

    /** Crutches: the rubber tips knock on the ground at every step. */
    private static void tickCrutches(ServerPlayer player, Tracker t) {
        if (t.moving && player.onGround() && !player.isPassenger()) {
            if (++t.stepTimer >= 9) {
                t.stepTimer = 0;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CRUTCH, SoundSource.PLAYERS, 0.7F,
                        0.9F + player.getRandom().nextFloat() * 0.2F);
            }
        } else {
            t.stepTimer = 5;
        }
    }

    /** Ground a wheelchair hardly rolls on. */
    private static boolean rough(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.MUD) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.CLAY) || state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL);
    }

    /** Wheelchair: sand, gravel, snow and mud slow you down a lot; the wheels squeak. */
    private static void tickWheelchair(ServerPlayer player, Tracker t, long now) {
        ServerLevel level = player.level();
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        boolean onRough = player.onGround() && (rough(level.getBlockState(feet)) || rough(level.getBlockState(feet.above())));
        if (onRough != t.rough) {
            t.rough = onRough;
            if (onRough) {
                player.sendOverlayMessage(Component.translatable("disabilities.msg.rough").withStyle(ChatFormatting.BLUE));
            }
        }
        if (t.moving && player.onGround() && now % 34 == 0) {
            level.playSound(null, player.getX(), player.getY() + 0.4, player.getZ(), ModSounds.SQUEAK, SoundSource.PLAYERS, 0.45F,
                    0.9F + player.getRandom().nextFloat() * 0.25F);
        }
    }

    public static boolean onRoughGround(ServerPlayer player) {
        Tracker t = TRACKERS.get(player.getUUID());
        return t != null && t.rough;
    }

    /** Amnesia: now and then you stop, lost ("where am I?"), and look around. */
    private static void tickAmnesia(ServerPlayer player, Tracker t, long now) {
        RandomSource random = player.getRandom();
        if (t.nextShuffle == 0) {
            t.nextShuffle = now + between(random, 6000, 10800);
        }
        if (now >= t.nextShuffle) {
            t.nextShuffle = now + between(random, 6000, 10800);
            forgetInventory(player);
            return;
        }
        if (t.nextLost == 0) {
            t.nextLost = now + between(random, 2400, 4800);
        }
        if (now >= t.nextLost) {
            t.nextLost = now + between(random, 2400, 4800);
            lost(player);
        }
    }

    public static void lost(ServerPlayer player) {
        long now = player.level().getGameTime();
        setStatus(player, ModData.status(player).withFreeze(now + 80).withConfused(now + 90));
        Body.refresh(player);
        motion(player, com.disabilities.net.MotionKind.LOOK_AROUND, 0);
        send(player, new Net.Episode(EpisodeKind.LOST, 0));
        bubble(player, Component.translatable("disabilities.bubble.lost").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), 80);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.lost").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Hemophilia: a nosebleed out of nowhere. */
    public static void nosebleed(ServerPlayer player) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        setStatus(player, status.withBleed(Math.max(status.bleedUntil(), now + 200)));
        bubble(player, Component.translatable("disabilities.bubble.nosebleed").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 60);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.nosebleed").withStyle(ChatFormatting.DARK_RED));
        Vec3 nose = player.getEyePosition().add(player.getLookAngle().scale(0.3));
        player.level().sendParticles(new DustParticleOptions(0x8A0303, 1.0F), nose.x, nose.y - 0.15, nose.z, 8, 0.05, 0.1, 0.05, 0.0);
    }

    // ---------------------------------------------------------------- helpers

    public static void setStatus(ServerPlayer player, Status status) {
        player.setAttached(ModData.STATUS, status);
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** A body movement on this player, for them and everyone who can see them. */
    public static void motion(ServerPlayer player, com.disabilities.net.MotionKind kind, int arg) {
        Net.Motion payload = new Net.Motion(player.getId(), kind, arg);
        send(player, payload);
        for (ServerPlayer other : PlayerLookup.tracking(player)) {
            if (other != player) {
                send(other, payload);
            }
        }
    }

    /** A sound at the player's head that everybody nearby hears. */
    static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** A line in the chat of everyone within {@code range} blocks (the player too). */
    static void tellNearby(ServerPlayer player, Component line, double range) {
        for (ServerPlayer other : player.level().players()) {
            if (other.distanceToSqr(player) < range * range) {
                other.sendSystemMessage(line);
            }
        }
    }

    /** A speech bubble over the player's head, for them and everyone who can see them. */
    public static void bubble(ServerPlayer player, Component text, int ticks) {
        Net.Bubble payload = new Net.Bubble(player.getId(), text, ticks);
        send(player, payload);
        for (ServerPlayer other : PlayerLookup.tracking(player)) {
            if (other != player) {
                send(other, payload);
            }
        }
    }
}
