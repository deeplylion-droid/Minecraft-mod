package com.disabilities.server;

import com.disabilities.condition.ModData;
import com.disabilities.condition.Status;
import com.disabilities.net.MotionKind;
import com.disabilities.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Claustrophobia: in tunnels, caves, small rooms and dark tight places the fear grows (under the
 * open sky it fades, faster with a friend close by). Anxious: the screen closes in, the heart races,
 * the hands shake (slower digging). When it is full: a panic attack, 12 seconds without the use of
 * your hands, breathing fast, arms over the head. Everybody sees and hears it.
 */
public final class Claustrophobia {
    private Claustrophobia() {
    }

    static final float ANXIOUS = 0.4F;
    static final int PANIC_TICKS = 240;

    static void tick(ServerPlayer player, Ailments.Tracker t, long now, Status status) {
        ServerLevel level = player.level();
        boolean panicking = status.panicking(now);
        if (t.wasPanicking && !panicking) {
            t.wasPanicking = false;
            t.lastPanic = now;
            t.anxiety = 0.7F;
            Body.refresh(player);
        }
        if (panicking) {
            t.wasPanicking = true;
            player.setSprinting(false);
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            if (now % 60 == 0) {
                Ailments.sound(player, ModSounds.HYPERVENTILATE, 1.0F, 1.0F);
            }
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + player.getBbHeight() * 0.9, player.getZ(), 2,
                        0.25, 0.05, 0.25, 0.0);
            }
            return;
        }
        if (now % 10 != 0) {
            return;
        }
        float enclosure = enclosure(player);
        float rate = enclosure > 0.35F ? (enclosure - 0.35F) * 0.06F : -0.05F;
        if (rate > 0.0F && friendNear(player)) {
            rate *= 0.5F;
        }
        t.anxiety = Mth.clamp(t.anxiety + rate, 0.0F, 1.0F);
        boolean anxious = t.anxiety >= ANXIOUS;
        if (anxious != status.anxious()) {
            if (anxious) {
                player.sendOverlayMessage(Component.translatable("disabilities.msg.anxious").withStyle(ChatFormatting.GRAY));
            }
            Ailments.setStatus(player, status.withFlag(Status.ANXIOUS, anxious));
            Body.refresh(player);
        }
        if (anxious && now % 30 == 0) {
            level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + player.getBbHeight() * 0.9, player.getZ(), 1,
                    0.25, 0.05, 0.25, 0.0);
        }
        if (anxious && now % 200 == 0 && player.getRandom().nextFloat() < 0.4F) {
            Ailments.motion(player, MotionKind.LOOK_AROUND, 0);
        }
        if (t.anxiety >= 1.0F && now - t.lastPanic > 600) {
            panic(player);
        }
    }

    public static void panic(ServerPlayer player) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        if (status.panicking(now) || ModData.lying(player)) {
            return;
        }
        Ailments.Tracker t = Ailments.tracker(player);
        t.wasPanicking = true;
        Ailments.setStatus(player, status.withPanic(now + PANIC_TICKS).withFlag(Status.ANXIOUS, true));
        player.setSprinting(false);
        player.stopUsingItem();
        Body.refresh(player);
        Ailments.sound(player, ModSounds.HYPERVENTILATE, 1.0F, 1.0F);
        Ailments.bubble(player, Component.translatable("disabilities.bubble.panic").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), PANIC_TICKS);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.panic").withStyle(ChatFormatting.RED));
    }

    /** 0 (under the open sky) to 1 (in a hole): solid blocks around the head, low ceiling, darkness. */
    static float enclosure(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos head = BlockPos.containing(player.getEyePosition());
        if (level.canSeeSky(head)) {
            return 0.0F;
        }
        int solid = 0;
        int total = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    pos.set(head.getX() + dx, head.getY() + dy, head.getZ() + dz);
                    total++;
                    if (level.getBlockState(pos).isSolid()) {
                        solid++;
                    }
                }
            }
        }
        float f = (float) solid / total;
        int ceiling = 0;
        pos.set(head);
        while (ceiling < 3) {
            pos.move(0, 1, 0);
            if (level.getBlockState(pos).isSolid()) {
                break;
            }
            ceiling++;
        }
        if (ceiling == 0) {
            f += 0.1F;
        }
        if (level.getMaxLocalRawBrightness(head) < 6) {
            f += 0.1F;
        }
        return Mth.clamp(f, 0.0F, 1.0F);
    }

    private static boolean friendNear(ServerPlayer player) {
        for (Player other : player.level().players()) {
            if (other != player && !other.isSpectator() && other.distanceToSqr(player) < 25.0) {
                return true;
            }
        }
        return false;
    }

    public static float anxiety(ServerPlayer player) {
        return Ailments.tracker(player).anxiety;
    }
}
