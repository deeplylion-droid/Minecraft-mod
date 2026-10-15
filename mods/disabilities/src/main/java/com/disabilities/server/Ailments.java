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
    private static final class Tracker {
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
        // right-click a sleeping friend to wake them up
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() && entity instanceof ServerPlayer target && player instanceof ServerPlayer waker
                    && ModData.asleep(target)) {
                wake(target, waker);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    /** Resets the timers (new conditions, respawn). */
    public static void forget(ServerPlayer player) {
        TRACKERS.remove(player.getUUID());
    }

    private static Tracker tracker(ServerPlayer player) {
        return TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
    }

    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static boolean episodesOn(ServerPlayer player) {
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

        // attributes follow the episodes: refresh when one starts or ends
        int bits = (asleep ? 1 : 0) | (status.frozen(now) ? 2 : 0) | (status.winded(now) ? 4 : 0) | (t.breathless ? 8 : 0);
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
        if (picks.has(Condition.ONE_LEG) && player.isSprinting()) {
            player.setSprinting(false);
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
        if (!episodes) {
            return;
        }
        if (picks.has(Condition.NARCOLEPSY) && !asleep) {
            tickNarcolepsy(player, t, now);
        }
        if (picks.has(Condition.TOURETTE) && !asleep) {
            tickTourette(player, t, now);
        }
        if (picks.has(Condition.PARKINSON) && !asleep) {
            tickParkinson(player, t, now, status);
        }
        if (picks.has(Condition.AMNESIA)) {
            if (t.nextShuffle == 0) {
                t.nextShuffle = now + between(random, 6000, 10800);
            }
            if (now >= t.nextShuffle) {
                t.nextShuffle = now + between(random, 6000, 10800);
                forgetInventory(player);
            }
        }
        if (picks.has(Condition.VERTIGO) && !asleep) {
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
        if (picks.has(Condition.ALLERGY) && !asleep && now % 20 == 0) {
            tickAllergy(player, t, now);
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
        if (t.sunTicks >= 60 && t.sunTicks % 40 == 0) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
            player.hurtServer(level, level.damageSources().source(ModDamage.SUNBURN), 1.0F);
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
        if (t.breathless) {
            player.setSprinting(false);
            if (now % 30 == 0) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.WHEEZE, SoundSource.PLAYERS, 0.9F,
                        0.95F + player.getRandom().nextFloat() * 0.1F);
            }
        }
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
        if (picks.has(Condition.NARCOLEPSY) && episodesOn(player) && !ModData.asleep(player) && safe(player)
                && player.getRandom().nextFloat() < 0.12F) {
            fallAsleep(player, 50, true);
        }
    }

    private static void tickBleeding(ServerPlayer player, long now) {
        ServerLevel level = player.level();
        if (now % 3 == 0) {
            RandomSource random = player.getRandom();
            double y = player.getY() + player.getBbHeight() * (0.3 + random.nextDouble() * 0.5);
            level.sendParticles(new DustParticleOptions(0x8A0303, 1.1F), player.getX(), y, player.getZ(), 2, 0.22, 0.1, 0.22, 0.0);
            level.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, player.getX(), y, player.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
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

    private static boolean safe(ServerPlayer player) {
        return player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger() && !player.isFallFlying()
                && !player.getAbilities().flying && !player.isOnFire() && !player.isSleeping() && player.isAlive();
    }

    private static void tickNarcolepsy(ServerPlayer player, Tracker t, long now) {
        RandomSource random = player.getRandom();
        if (t.nextSleep == 0) {
            t.nextSleep = now + between(random, 3600, 7200);
        }
        if (!t.yawned && now >= t.nextSleep - 60) {
            t.yawned = true;
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.YAWN, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.translatable("disabilities.msg.sleepy").withStyle(ChatFormatting.AQUA));
        }
        if (now >= t.nextSleep) {
            if (safe(player)) {
                fallAsleep(player, between(random, 100, 160), false);
                t.nextSleep = now + between(random, 3600, 7200);
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
            t.nextFreeze = now + between(random, 900, 2000);
        }
        if (!status.frozen(now) && now >= t.nextFreeze) {
            if (player.onGround() && t.moving) {
                freeze(player, between(random, 40, 80));
                t.nextFreeze = now + between(random, 900, 2000);
            } else {
                t.nextFreeze = now + 40;
            }
        }
        if (t.nextDrop == 0) {
            t.nextDrop = now + between(random, 2400, 6000);
        }
        if (now >= t.nextDrop) {
            if (!player.getMainHandItem().isEmpty()) {
                dropHeld(player, "disabilities.msg.slipped");
                t.nextDrop = now + between(random, 2400, 6000);
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

    private static boolean allergen(BlockState state) {
        return state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.BUSH) || state.is(Blocks.HAY_BLOCK);
    }

    private static void tickAllergy(ServerPlayer player, Tracker t, long now) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        BlockPos center = player.blockPosition();
        int found = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 48; i++) {
            pos.set(center.getX() + random.nextInt(11) - 5, center.getY() + random.nextInt(6) - 2, center.getZ() + random.nextInt(11) - 5);
            if (allergen(level.getBlockState(pos))) {
                found++;
            }
        }
        int animals = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(6)).size();
        float exposure = Mth.clamp(found / 10.0F + animals * 0.25F, 0.0F, 1.0F);
        t.allergen = t.allergen * 0.7F + exposure * 0.3F;
        if (t.allergen > 0.12F && now - t.lastSneeze > 80 && random.nextFloat() < t.allergen * 0.14F) {
            t.lastSneeze = now;
            sneeze(player);
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

    // ---------------------------------------------------------------- helpers

    public static void setStatus(ServerPlayer player, Status status) {
        player.setAttached(ModData.STATUS, status);
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
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
