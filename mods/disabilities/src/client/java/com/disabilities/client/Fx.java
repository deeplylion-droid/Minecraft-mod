package com.disabilities.client;

import com.disabilities.condition.Condition;
import com.disabilities.net.EpisodeKind;
import com.disabilities.sound.ModSounds;
import com.disabilities.util.Noise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * What the local player's own conditions do to their screen and controls, tick by tick: the blind
 * fog and the cane, camera lurches, tremor, vertigo, tears, tics.
 */
public final class Fx {
    private Fx() {
    }

    // ---------------------------------------------------------------- blindness

    /** How far a blind player sees, in blocks. */
    public static final float BLIND_RADIUS = 2.6F;
    private static int sonarAge = -1;
    private static int tapAge = -1;
    /** Entities that just made a sound: a blind player sees their outline for a moment. */
    private static final Map<Integer, Integer> ECHOES = new HashMap<>();

    // ---------------------------------------------------------------- camera and screen

    private static float kickRoll;
    private static float kickRollO;
    private static float kickFov;
    private static float kickFovO;
    private static int tears;
    private static int blink;
    private static int forgotFlash;
    private static int burnFlash;
    private static int crouchTicks;
    private static float spinLeft;
    private static float fear;
    private static float fearO;
    private static int ticks;
    private static boolean wasDeaf;

    // Parkinson tremor offsets currently added to the aim
    private static float tremorYaw;
    private static float tremorPitch;

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            ECHOES.clear();
            return;
        }
        ticks++;
        if (sonarAge >= 0 && ++sonarAge > 90) {
            sonarAge = -1;
        }
        if (tapAge >= 0 && ++tapAge > 20) {
            tapAge = -1;
        }
        Iterator<Map.Entry<Integer, Integer>> it = ECHOES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            if (e.getValue() <= 1) {
                it.remove();
            } else {
                e.setValue(e.getValue() - 1);
            }
        }
        kickRollO = kickRoll;
        kickRoll *= 0.8F;
        kickFovO = kickFov;
        kickFov *= 0.8F;
        if (tears > 0) {
            tears--;
        }
        if (blink > 0) {
            blink--;
        }
        if (forgotFlash > 0) {
            forgotFlash--;
        }
        if (burnFlash > 0) {
            burnFlash--;
        }
        if (crouchTicks > 0) {
            crouchTicks--;
        }
        if (Math.abs(spinLeft) > 0.5F) {
            float step = Math.copySign(Math.min(Math.abs(spinLeft), 36.0F), spinLeft);
            player.turn(step / 0.15F, 0.0);
            spinLeft -= step;
        } else {
            spinLeft = 0.0F;
        }
        fearO = fear;
        fear += (vertigoTarget(player) - fear) * 0.08F;

        boolean deaf = Me.has(Condition.DEAF);
        if (deaf && !wasDeaf) {
            mc.getSoundManager().stop();
        }
        wasDeaf = deaf;
    }

    // ---------------------------------------------------------------- blindness

    /** Cane right-click: the fog opens for a few seconds. */
    public static void sonar() {
        if (Me.has(Condition.BLIND)) {
            sonarAge = 0;
        }
    }

    public static float blindRadius(float partial) {
        float radius = BLIND_RADIUS;
        if (sonarAge >= 0) {
            float age = sonarAge + partial;
            float open = age < 6 ? age / 6.0F : age < 40 ? 1.0F : Math.max(0.0F, 1.0F - (age - 40) / 50.0F);
            radius += 22.0F * open * open;
        }
        if (tapAge >= 0) {
            float age = tapAge + partial;
            float pulse = age < 3 ? age / 3.0F : Math.max(0.0F, 1.0F - (age - 3) / 17.0F);
            radius += 2.5F * pulse;
        }
        return radius;
    }

    public static boolean echoing(Entity entity) {
        return ECHOES.containsKey(entity.getId());
    }

    /** Every sound the client plays: the blind see where it came from. */
    public static void onSound(SoundInstance sound) {
        LocalPlayer player = Me.player();
        if (player == null || sound.isRelative() || !Me.has(Condition.BLIND)) {
            return;
        }
        double x = sound.getX();
        double y = sound.getY();
        double z = sound.getZ();
        double dist = player.distanceToSqr(x, y, z);
        if (dist > 28 * 28) {
            return;
        }
        if (dist < 9 && sound.getIdentifier().equals(ModSounds.CANE_TAP.location())) {
            tapAge = 0;
            return;
        }
        AABB box = new AABB(x - 1.6, y - 1.6, z - 1.6, x + 1.6, y + 1.6, z + 1.6);
        for (Entity e : player.level().getEntities(player, box, e -> e instanceof LivingEntity)) {
            ECHOES.put(e.getId(), 36);
        }
    }

    // ---------------------------------------------------------------- episodes from the server

    public static void onEpisode(EpisodeKind kind, int arg) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || kind == null) {
            return;
        }
        java.util.Random random = new java.util.Random(arg * 31L + ticks);
        switch (kind) {
            case SNEEZE -> {
                player.turn(0.0, (14.0F + random.nextFloat() * 10.0F) / 0.15F);
                kickRoll += (random.nextBoolean() ? 1 : -1) * 6.0F;
                kickFov -= 0.12F;
                tears = 90;
            }
            case TIC_HEAD -> {
                float yaw = (random.nextBoolean() ? 1 : -1) * (14.0F + random.nextFloat() * 22.0F);
                float pitch = (random.nextFloat() - 0.6F) * 30.0F;
                player.turn(yaw / 0.15F, pitch / 0.15F);
                kickRoll += Math.signum(yaw) * 4.0F;
            }
            case TIC_JUMP -> {
                if (player.onGround()) {
                    player.jumpFromGround();
                }
            }
            case TIC_SWING -> punch(player);
            case TIC_CROUCH -> crouchTicks = 6;
            case TIC_SPIN -> spinLeft = (random.nextBoolean() ? 1 : -1) * (120.0F + random.nextFloat() * 120.0F);
            case TIC_BLINK -> blink = 5;
            case TIC_HIT -> {
                if (mc.hitResult instanceof EntityHitResult hit && mc.gameMode != null) {
                    mc.gameMode.attack(player, hit.getEntity());
                }
                punch(player);
            }
            case STUMBLE -> {
                kickRoll += arg * 14.0F;
                kickFov += 0.08F;
                player.turn(arg * 12.0F / 0.15F, 8.0F / 0.15F);
            }
            case FORGOT -> forgotFlash = 50;
            case SUNBURN -> {
                burnFlash = 8;
                kickRoll += (random.nextBoolean() ? 1 : -1) * 2.0F;
            }
        }
    }

    /** A swing of the arm that everybody sees (like a click in the air). */
    private static void punch(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
        player.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
    }

    // ---------------------------------------------------------------- per frame

    public static float time(float partial) {
        return ticks + partial;
    }

    /** Camera roll in degrees: vertigo sway and the lurches. */
    public static float roll(float partial) {
        float t = time(partial);
        float roll = Mth.lerp(partial, kickRollO, kickRoll);
        if (Me.has(Condition.VERTIGO)) {
            float f = Mth.lerp(partial, fearO, fear);
            roll += Mth.sin(t * 0.045F) * 2.2F + Mth.sin(t * 0.11F + 1.0F) * 1.0F;
            roll += f * (Mth.sin(t * 0.09F) * 14.0F + Mth.sin(t * 0.23F) * 5.0F);
        }
        return roll;
    }

    /** FOV multiplier: the vertigo "dolly zoom" and the lurches. */
    public static float fov(float partial) {
        float f = 1.0F + Mth.lerp(partial, kickFovO, kickFov);
        if (Me.has(Condition.VERTIGO)) {
            float fr = Mth.lerp(partial, fearO, fear);
            f *= 1.0F + fr * 0.22F * Mth.sin(time(partial) * 0.06F);
        }
        return f;
    }

    public static float fear(float partial) {
        return Mth.lerp(partial, fearO, fear);
    }

    public static float tears(float partial) {
        return tears <= 0 ? 0.0F : Math.min(1.0F, (tears - partial) / 30.0F);
    }

    public static boolean blinking() {
        return blink > 0;
    }

    public static float forgotFlash(float partial) {
        return forgotFlash <= 0 ? 0.0F : (forgotFlash - partial) / 50.0F;
    }

    public static float burnFlash(float partial) {
        return burnFlash <= 0 ? 0.0F : (burnFlash - partial) / 8.0F;
    }

    public static boolean forcedCrouch() {
        return crouchTicks > 0;
    }

    /** Parkinson: the aim shakes around where you point it (called every frame). */
    public static void tremor(Minecraft mc, float partial) {
        LocalPlayer player = mc.player;
        if (player == null || !Me.has(Condition.PARKINSON) || mc.gui.screen() != null) {
            tremorYaw = 0.0F;
            tremorPitch = 0.0F;
            return;
        }
        double seconds = time(partial) / 20.0;
        long seed = player.getUUID().getLeastSignificantBits();
        float burst = Math.max(0.0F, Noise.smooth(seed + 5, seconds * 0.35)) * 2.2F;
        float amp = 1.0F + burst;
        float yaw = Noise.tremor(seed, seconds * 5.5) * 1.25F * amp;
        float pitch = Noise.tremor(seed + 99, seconds * 5.5) * 0.95F * amp;
        float dy = yaw - tremorYaw;
        float dp = pitch - tremorPitch;
        tremorYaw = yaw;
        tremorPitch = pitch;
        player.turn(dy / 0.15F, dp / 0.15F);
    }

    /** Vertigo: how scared of the drop you are (0-1), from the empty space around your feet. */
    private static float vertigoTarget(LocalPlayer player) {
        if (!Me.has(Condition.VERTIGO) || player.getAbilities().flying) {
            return 0.0F;
        }
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        int worst = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) {
                    continue;
                }
                int depth = 0;
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(feet.getX() + dx, feet.getY() - 1, feet.getZ() + dz);
                while (depth < 12 && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()) {
                    depth++;
                    pos.move(0, -1, 0);
                }
                worst = Math.max(worst, depth);
            }
        }
        if (player.onClimbable()) {
            int depth = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(feet.getX(), feet.getY() - 1, feet.getZ());
            while (depth < 12 && !level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) {
                depth++;
                pos.move(0, -1, 0);
            }
            worst = Math.max(worst, depth);
        }
        if (worst < 4) {
            return 0.0F;
        }
        float f = Mth.clamp((worst - 3) / 6.0F, 0.0F, 1.0F);
        float down = Mth.clamp(player.getXRot() / 60.0F, 0.0F, 1.0F);
        return Math.min(1.0F, f * (0.6F + 0.6F * down));
    }

    public static void reset() {
        sonarAge = -1;
        tapAge = -1;
        ECHOES.clear();
        kickRoll = kickRollO = kickFov = kickFovO = 0.0F;
        tears = blink = forgotFlash = burnFlash = crouchTicks = 0;
        spinLeft = 0.0F;
        fear = fearO = 0.0F;
        tremorYaw = tremorPitch = 0.0F;
    }
}
