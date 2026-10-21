package com.infezione.infection;

import com.infezione.config.InfezioneConfig;
import com.infezione.network.InfezioneNetwork;
import com.infezione.network.StatePacket;
import com.infezione.network.SymptomPacket;
import com.infezione.registry.ModSounds;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The infection on the server: the clock that moves a player from stage to stage, the episodes, what
 * each stage takes away, and telling the clients. Everything that changes a player's infection goes
 * through {@link #setStage}.
 */
public final class Infections {
    private static final UUID HEALTH_ID = UUID.fromString("5d1f3b7a-6c1e-4f43-9a51-2b1c0e8f4a01");
    private static final UUID SPEED_ID = UUID.fromString("5d1f3b7a-6c1e-4f43-9a51-2b1c0e8f4a02");
    private static final UUID DAMAGE_ID = UUID.fromString("5d1f3b7a-6c1e-4f43-9a51-2b1c0e8f4a03");

    private Infections() {
    }

    public static int stageTicks() {
        return Stages.stageTicks(InfezioneConfig.SERVER.minutesPerStage.get());
    }

    /** Every tick, for every living player. */
    public static void tick(ServerPlayer player) {
        Infection infection = Infection.of(player);
        int stage = infection.stage();
        if (stage == Stages.HEALTHY || !player.isAlive()) {
            return;
        }
        if (Stages.progresses(stage) && !infection.paused()) {
            int ticks = infection.ticks() + 1;
            if (ticks >= stageTicks()) {
                setStage(player, stage + 1, Change.WORSE);
                return;
            }
            infection.setTicks(ticks);
        }
        boolean effects = InfezioneConfig.SERVER.gameplayEffects.get();
        if (effects && !player.getAbilities().instabuild) {
            player.causeFoodExhaustion(Stages.exhaustion(stage));
        }
        int next = infection.nextEpisode() - 1;
        if (next <= 0) {
            Symptom symptom = Symptom.pick(stage, player.getRandom().nextDouble());
            if (symptom != null && !player.isSpectator()) {
                episode(player, symptom);
            }
            next = Stages.ticksToNextEpisode(stage, player.getRandom().nextDouble());
        }
        infection.setNextEpisode(next);
        if (stage == Stages.ZOMBIE) {
            ZombiePlayer.tick(player);
        }
        if (player.tickCount % 20 == 0) {
            applyAttributes(player, stage);
            InfezioneNetwork.sendTo(player, state(player, Change.NONE));
        }
    }

    /**
     * Puts a player at a stage (0 = healthy, 5 = zombie), from its beginning, and tells everyone. The
     * first episode of the new stage comes after a few seconds.
     */
    public static void setStage(ServerPlayer player, int stage, Change change) {
        Infection infection = Infection.of(player);
        int before = infection.stage();
        stage = Stages.clamp(stage);
        infection.setStage(stage);
        infection.setNextEpisode(stage == Stages.ZOMBIE ? 200 + player.getRandom().nextInt(200) : 80 + player.getRandom().nextInt(120));
        if (stage == Stages.HEALTHY) {
            infection.setPaused(false);
        }
        applyAttributes(player, stage);
        if (before == Stages.ZOMBIE && stage != Stages.ZOMBIE) {
            ZombiePlayer.stop(player);
        }
        if (before != Stages.ZOMBIE && stage == Stages.ZOMBIE && player.isAlive()) {
            infection.setSunSafeUntil(player.level().getGameTime() + Symptom.TRANSFORM.ticks + 40);
            ZombiePlayer.start(player);
        }
        InfezioneNetwork.sendToTrackingAndSelf(player, state(player, change));
        if (stage == Stages.ZOMBIE && before != Stages.ZOMBIE && player.isAlive()) {
            episode(player, Symptom.TRANSFORM);
        }
    }

    public static void setPaused(ServerPlayer player, boolean paused) {
        Infection.of(player).setPaused(paused);
        InfezioneNetwork.sendTo(player, state(player, Change.PAUSE));
    }

    /** The length of a stage changed: the player keeps their place in it, as a share of the whole. */
    public static void rescale(ServerPlayer player, int before, int after) {
        Infection infection = Infection.of(player);
        infection.setTicks((int) ((long) infection.ticks() * after / Math.max(1, before)));
        InfezioneNetwork.sendTo(player, state(player, Change.NONE));
    }

    /** The state of a player for the clients. */
    public static StatePacket state(ServerPlayer player, Change change) {
        Infection infection = Infection.of(player);
        int stage = infection.stage();
        int total = stageTicks();
        int left = Stages.progresses(stage) ? Math.max(0, total - infection.ticks()) : 0;
        return new StatePacket(player.getId(), player.getUUID(), stage, infection.paused(), left, total, change.ordinal());
    }

    /** After logging in, respawning or changing world: the attributes again, and the state to the player. */
    public static void refresh(ServerPlayer player) {
        int stage = Infection.of(player).stage();
        applyAttributes(player, stage);
        if (stage == Stages.ZOMBIE) {
            ZombiePlayer.start(player, false);
        }
        InfezioneNetwork.sendTo(player, state(player, Change.NONE));
    }

    /** An episode: its sound for everyone around, its effects on the player, its animation on every client. */
    public static void episode(ServerPlayer player, Symptom symptom) {
        boolean effects = InfezioneConfig.SERVER.gameplayEffects.get() && !player.getAbilities().instabuild;
        float pitch = 0.9F + player.getRandom().nextFloat() * 0.2F;
        switch (symptom) {
            case COUGH -> sound(player, ModSounds.COUGH.get(), 1.0F, pitch);
            case BLOOD_COUGH -> sound(player, ModSounds.COUGH_BLOOD.get(), 1.1F, pitch);
            case TWITCH -> sound(player, ModSounds.BONE_CRACK.get(), 0.6F, pitch + 0.2F);
            case DIZZY -> {
                sound(player, ModSounds.BREATH.get(), 0.8F, pitch);
                if (effects) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, symptom.ticks + 40, 0, false, false, true));
                }
            }
            case CONVULSION -> {
                sound(player, ModSounds.BONE_CRACK.get(), 1.2F, pitch);
                sound(player, ModSounds.BREATH.get(), 1.0F, pitch - 0.15F);
                if (effects) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, symptom.ticks, 4, false, false, false));
                }
            }
            case BLACKOUT -> sound(player, ModSounds.BREATH.get(), 0.7F, pitch - 0.2F);
            case GROAN -> sound(player, SoundEvents.ZOMBIE_AMBIENT, 1.3F, pitch - 0.1F);
            case TRANSFORM -> sound(player, ModSounds.TRANSFORM.get(), 2.5F, 1.0F);
            default -> {
            }
        }
        InfezioneNetwork.sendToTrackingAndSelf(player, new SymptomPacket(player.getId(), symptom.ordinal()));
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * The antidote, given by {@code by} (who can be the patient): it works up to stage 4. Returns true if
     * it cured them (the syringe is used up).
     */
    public static boolean inject(ServerPlayer patient, ServerPlayer by) {
        int stage = Infection.of(patient).stage();
        boolean self = patient == by;
        if (stage == Stages.HEALTHY) {
            by.displayClientMessage(self ? Component.translatable("infezione.antidote.healthy_self")
                    : Component.translatable("infezione.antidote.healthy", patient.getDisplayName()), true);
            return false;
        }
        if (stage > Stages.LAST_CURABLE) {
            by.displayClientMessage(self ? Component.translatable("infezione.antidote.too_late_self")
                    : Component.translatable("infezione.antidote.too_late", patient.getDisplayName()), true);
            sound(patient, SoundEvents.ZOMBIE_AMBIENT, 1.0F, 0.8F);
            return false;
        }
        sound(patient, ModSounds.INJECT.get(), 1.0F, 1.0F);
        setStage(patient, Stages.HEALTHY, Change.CURED);
        if (!self) {
            by.displayClientMessage(Component.translatable("infezione.antidote.cured", patient.getDisplayName()), true);
            patient.sendSystemMessage(Component.translatable("infezione.antidote.cured_by", by.getDisplayName()));
        }
        return true;
    }

    /** The modifiers of the stage: fewer hearts, slower at stage 4, harder hits for the zombie. */
    static void applyAttributes(ServerPlayer player, int stage) {
        boolean effects = InfezioneConfig.SERVER.gameplayEffects.get();
        modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Infezione: cuori", effects ? -Stages.healthLost(stage) : 0,
                AttributeModifier.Operation.ADDITION);
        modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Infezione: lento", effects ? -Stages.slower(stage) : 0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "Infezione: zombie",
                stage == Stages.ZOMBIE ? InfezioneConfig.SERVER.zombieDamage.get() : 0, AttributeModifier.Operation.ADDITION);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void modifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount,
                                 AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        }
    }
}
