package com.jumpscares.client.scare;

import com.jumpscares.registry.ModSounds;
import com.jumpscares.scare.ScareKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** The scares this client is playing: its own (as the victim) and the ones it is watching. */
public final class ScareClient {
    private static Scare active;
    private static final List<Scare> WITNESSED = new ArrayList<>();
    private static final ScarePose POSE = new ScarePose();
    private static final RandomSource RANDOM = RandomSource.create();

    /** A running scare; for a witnessed one, {@code victim} is the entity id it jumps at. */
    public static final class Scare {
        public final ScareKind kind;
        public final long seed;
        public final int victim;
        public int age;

        Scare(ScareKind kind, long seed, int victim) {
            this.kind = kind;
            this.seed = seed;
            this.victim = victim;
        }

        public float time(float partialTick) {
            return this.age + partialTick;
        }
    }

    private ScareClient() {
    }

    public static void start(ScareKind kind, long seed) {
        active = new Scare(kind, seed, -1);
        play(ModSounds.WHOOSH, 1.0F);
        play(ModSounds.intro(kind), 1.0F);
    }

    public static void witness(int victim, ScareKind kind, long seed) {
        WITNESSED.removeIf(s -> s.victim == victim);
        WITNESSED.add(new Scare(kind, seed, victim));
    }

    public static Scare active() {
        return active;
    }

    public static List<Scare> witnessed() {
        return WITNESSED;
    }

    public static void reset() {
        active = null;
        WITNESSED.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (active != null) {
            active.age++;
            if (active.age == active.kind.impactTick) {
                impact(mc, active.kind);
            }
            if (active.age >= active.kind.length()) {
                active = null;
            }
        }
        WITNESSED.removeIf(s -> {
            s.age++;
            Entity victim = mc.level.getEntity(s.victim);
            if (victim != null && s.age == s.kind.lungeTick() + 1) {
                // it vanishes in a puff of smoke
                Vec3 at = victim.getEyePosition().add(victim.getViewVector(1.0F).scale(0.6));
                for (int i = 0; i < 20; i++) {
                    mc.level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + RANDOM.nextGaussian() * 0.25, at.y + RANDOM.nextGaussian() * 0.25,
                            at.z + RANDOM.nextGaussian() * 0.25, 0.0, 0.02, 0.0);
                }
            }
            return victim == null || s.age > s.kind.lungeTick() + 2;
        });
    }

    private static void impact(Minecraft mc, ScareKind kind) {
        // two screams at once (a hair apart in pitch) plus the sting: as loud as it gets
        play(ModSounds.scream(kind), 1.0F);
        play(ModSounds.scream(kind), 0.97F);
        play(ModSounds.STING, 1.0F);
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        Vector3f look = camera.getLookVector();
        for (int i = 0; i < 20; i++) {
            double d = 0.8 + RANDOM.nextDouble() * 1.8;
            double x = eye.x + look.x() * d + RANDOM.nextGaussian() * 0.7;
            double y = eye.y + look.y() * d + RANDOM.nextGaussian() * 0.5;
            double z = eye.z + look.z() * d + RANDOM.nextGaussian() * 0.7;
            mc.level.addParticle(particle(kind, i), x, y, z, RANDOM.nextGaussian() * 0.05, RANDOM.nextGaussian() * 0.05,
                    RANDOM.nextGaussian() * 0.05);
        }
    }

    private static ParticleOptions particle(ScareKind kind, int i) {
        return switch (kind) {
            case CLOWN -> {
                int[] colors = {0xE8102A, 0xFFD21A, 0x2050E0, 0x20B040};
                int c = colors[i % colors.length];
                yield new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F), 1.5F);
            }
            case LADY -> i % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.ASH;
            case SPIDER -> new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
            case CORPSE -> i % 3 == 0 ? ParticleTypes.SQUID_INK : new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH));
            case DEMON -> i % 5 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
        };
    }

    private static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 1.0F));
    }

    /** The victim's own scare at this moment, or null. The returned pose is reused: do not keep it. */
    public static ScarePose pose(float partialTick) {
        if (active == null) {
            return null;
        }
        ScareTimeline.compute(active.kind, active.seed, active.time(partialTick), POSE);
        return POSE;
    }

    public static boolean hidesHand() {
        return active != null;
    }

    /** Field of view multiplier (the punch when it hits). */
    public static double fovFactor(float partialTick) {
        ScarePose p = pose(partialTick);
        return p == null ? 1.0 : p.fov;
    }

    /** Shakes the camera; applied where vanilla tilts it when you are hurt. */
    public static void shake(PoseStack poseStack, float partialTick) {
        ScarePose p = pose(partialTick);
        if (p == null || p.shake <= 0.0F) {
            return;
        }
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        float s = p.shake * (0.35F + 0.65F * scale);
        float t = active.time(partialTick);
        long seed = active.seed;
        float ph = (seed & 1023) * 0.01F;
        int frame = (int) (t * 3.0F);
        float jx = ScareTimeline.hash(seed, 5000 + frame * 3), jy = ScareTimeline.hash(seed, 5001 + frame * 3),
                jz = ScareTimeline.hash(seed, 5002 + frame * 3);
        float rz = s * (4.0F * Mth.sin(t * 3.7F + ph) + 2.5F * Mth.sin(t * 9.3F + ph * 2.0F) + 2.0F * jz);
        float rx = s * (2.2F * Mth.sin(t * 4.3F + ph * 3.0F) + 1.2F * jx);
        float ry = s * (2.2F * Mth.sin(t * 3.1F + ph * 5.0F) + 1.2F * jy);
        poseStack.translate(s * 0.05F * jx, s * 0.05F * jy, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        poseStack.mulPose(Axis.YP.rotationDegrees(ry));
    }
}
