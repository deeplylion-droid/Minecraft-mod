package com.infezione.client.fx;

import com.infezione.client.ClientInfections;
import com.infezione.client.hud.GenomeBar;
import com.infezione.client.hud.InfectionHud;
import com.infezione.config.InfezioneConfig;
import com.infezione.infection.Stages;
import com.infezione.infection.Symptom;
import com.infezione.registry.ModParticles;
import com.infezione.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What the episodes and the stages look and sound like around an infected player (particles, flies,
 * sweat, blood), and what only the infected player feels: their heartbeat, the jolts of the camera, sounds
 * that are not there.
 */
public final class SymptomFx {
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextBeat = 40;
    private static int nextHallucination = 900;
    /** This client's clock when the heart last beat (the edges of the screen pulse with it). */
    private static long lastBeat = -100;

    private SymptomFx() {
    }

    public static long lastBeat() {
        return lastBeat;
    }

    /** An episode starts on a player. */
    public static void start(Entity entity, Symptom symptom) {
        Minecraft mc = Minecraft.getInstance();
        boolean self = entity == mc.player;
        if (self) {
            GenomeBar.glitch(symptom == Symptom.TRANSFORM ? 60 : 8);
        }
        switch (symptom) {
            case THROB -> {
                if (self) {
                    beat(mc, 1.0F);
                    InfectionHud.flash(0x8A0000, 0.35F, 14);
                    ScreenShake.add(0.8F, 10);
                }
            }
            case TWITCH -> {
                if (self) {
                    ScreenShake.add(3.5F, 7);
                }
            }
            case CONVULSION -> {
                if (self) {
                    ScreenShake.add(5.0F, symptom.ticks);
                    InfectionHud.flash(0x5A0000, 0.25F, 20);
                }
            }
            case TRANSFORM -> {
                burst(entity, 40);
                if (self) {
                    ScreenShake.add(7.0F, symptom.ticks);
                }
            }
            default -> {
            }
        }
    }

    /** Someone was cured in front of us. */
    public static void cured(Entity entity) {
        for (int i = 0; i < 18; i++) {
            entity.level().addParticle(ParticleTypes.HAPPY_VILLAGER, entity.getRandomX(0.8), entity.getRandomY() + 0.2,
                    entity.getRandomZ(0.8), 0.0, 0.05, 0.0);
        }
    }

    /** Every client tick (not while paused). */
    public static void tick(Minecraft mc) {
        ScreenShake.tick();
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (Player player : mc.level.players()) {
            int stage = ClientInfections.stage(player);
            if (stage == Stages.HEALTHY || player.isInvisible() || player.isSpectator()) {
                continue;
            }
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            ClientInfections.Episode episode = ClientInfections.episode(player);
            if (episode != null) {
                episodeTick(player, episode, firstPerson);
            }
            ambient(mc, (AbstractClientPlayer) player, stage, firstPerson);
        }
        selfTick(mc);
    }

    /** The moments of the episodes: each cough brings up a puff, or blood. */
    private static void episodeTick(Player player, ClientInfections.Episode episode, boolean firstPerson) {
        int t = (int) (ClientInfections.clock() - episode.start());
        switch (episode.symptom()) {
            case COUGH -> {
                if (t == 1 || t == 5 || t == 10) {
                    fromMouth(player, ModParticles.COUGH.get(), firstPerson ? 2 : 4, 0.09, firstPerson);
                }
            }
            case BLOOD_COUGH -> {
                if (t == 1 || t == 6 || t == 11 || t == 16) {
                    fromMouth(player, ModParticles.BLOOD.get(), firstPerson ? 3 : 7, 0.13, firstPerson);
                    fromMouth(player, ModParticles.COUGH_BLOOD.get(), firstPerson ? 1 : 3, 0.07, firstPerson);
                }
            }
            case CONVULSION -> {
                if (t % 6 == 2) {
                    fromMouth(player, ModParticles.BLOOD.get(), 2, 0.04, firstPerson);
                }
            }
            case TRANSFORM -> {
                if (t % 8 == 4 && t < 44) {
                    burst(player, 10);
                }
            }
            default -> {
            }
        }
    }

    /** The signs of a stage, all the time: fever sweat, flies, blood dripping from the mouth. */
    private static void ambient(Minecraft mc, AbstractClientPlayer player, int stage, boolean firstPerson) {
        if (firstPerson) {
            return;
        }
        Vec3 head = player.getEyePosition();
        if ((stage == 2 || stage == 3) && RANDOM.nextInt(stage == 2 ? 30 : 20) == 0) {
            player.level().addParticle(ParticleTypes.FALLING_WATER, head.x + (RANDOM.nextDouble() - 0.5) * 0.5,
                    head.y + 0.1 + RANDOM.nextDouble() * 0.2, head.z + (RANDOM.nextDouble() - 0.5) * 0.5, 0.0, 0.0, 0.0);
        }
        if (stage >= 4 && RANDOM.nextInt(stage == 4 ? 30 : 45) == 0) {
            fromMouth(player, ModParticles.BLOOD.get(), 1, 0.0, false);
        }
        if (stage >= 4 && RANDOM.nextInt(stage == 4 ? 12 : 6) == 0) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double radius = 0.35 + RANDOM.nextDouble() * 0.4;
            player.level().addParticle(ModParticles.FLY.get(), head.x + Math.cos(angle) * radius,
                    head.y - 0.6 + RANDOM.nextDouble() * 0.9, head.z + Math.sin(angle) * radius, 0.0, 0.0, 0.0);
        }
        if (stage >= 4 && RANDOM.nextInt(120) == 0) {
            player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), ModSounds.FLIES.get(),
                    SoundSource.PLAYERS, 0.35F, 0.9F + RANDOM.nextFloat() * 0.2F, false);
        }
    }

    /** The infected player's heartbeat, and the sounds that are not there. */
    private static void selfTick(Minecraft mc) {
        int stage = ClientInfections.SELF.stage;
        if (stage == Stages.HEALTHY || stage == Stages.ZOMBIE || mc.player.isSpectator()) {
            // the zombie's heart does not beat
            nextBeat = 40;
            return;
        }
        if (--nextBeat <= 0) {
            beat(mc, switch (stage) {
                case 1 -> 0.3F;
                case 2 -> 0.45F;
                case 3 -> 0.6F;
                default -> 0.85F;
            });
            nextBeat = switch (stage) {
                case 1 -> 110 + RANDOM.nextInt(60);
                case 2 -> 70 + RANDOM.nextInt(40);
                case 3 -> 26;
                default -> 17;
            };
        }
        if (stage >= 3 && InfezioneConfig.CLIENT.hallucinations.get() && --nextHallucination <= 0) {
            nextHallucination = 600 + RANDOM.nextInt(800);
            hallucinate(mc);
        }
    }

    /** One heartbeat (the edges of the screen pulse with it). */
    private static void beat(Minecraft mc, float volume) {
        lastBeat = ClientInfections.clock();
        if (InfezioneConfig.CLIENT.heartbeat.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.0F, volume));
        }
    }

    /** A zombie groaning somewhere behind you, or its steps. Only you hear it. */
    private static void hallucinate(Minecraft mc) {
        Player player = mc.player;
        float yaw = player.getYRot() + 180.0F + (RANDOM.nextFloat() - 0.5F) * 120.0F;
        double distance = 5.0 + RANDOM.nextDouble() * 7.0;
        double x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
        double z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
        if (RANDOM.nextInt(3) == 0) {
            for (int i = 0; i < 4; i++) {
                double d = distance - i * 0.8;
                mc.level.playLocalSound(player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * d, player.getY(),
                        player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * d, SoundEvents.ZOMBIE_STEP, SoundSource.HOSTILE,
                        0.6F, 1.0F, false);
            }
        } else {
            mc.level.playLocalSound(x, player.getEyeY(), z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE,
                    1.0F, 0.8F + RANDOM.nextFloat() * 0.3F, false);
        }
    }

    private static void fromMouth(Player player, ParticleOptions particle, int count, double speed, boolean firstPerson) {
        Vec3 look = player.getViewVector(1.0F);
        // in first person a bit further and lower, or right in front of the eyes they look huge
        Vec3 mouth = player.getEyePosition().add(look.scale(firstPerson ? 0.9 : 0.3)).add(0.0, firstPerson ? -0.5 : -0.17, 0.0);
        for (int i = 0; i < count; i++) {
            player.level().addParticle(particle, mouth.x, mouth.y, mouth.z,
                    look.x * speed + (RANDOM.nextDouble() - 0.5) * 0.03,
                    look.y * speed + (RANDOM.nextDouble() - 0.5) * 0.03 + 0.01,
                    look.z * speed + (RANDOM.nextDouble() - 0.5) * 0.03);
        }
    }

    /** Smoke and blood all around a body (the transformation). */
    private static void burst(Entity entity, int count) {
        for (int i = 0; i < count; i++) {
            entity.level().addParticle(i % 4 == 0 ? ParticleTypes.LARGE_SMOKE : ModParticles.BLOOD.get(),
                    entity.getRandomX(0.9), entity.getRandomY(), entity.getRandomZ(0.9),
                    (RANDOM.nextDouble() - 0.5) * 0.25, RANDOM.nextDouble() * 0.2, (RANDOM.nextDouble() - 0.5) * 0.25);
        }
    }
}
