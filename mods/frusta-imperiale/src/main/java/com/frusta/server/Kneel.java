package com.frusta.server;

import com.frusta.network.FrustaNetwork;
import com.frusta.network.KneelPacket;
import com.frusta.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;

/**
 * Sottomissione: whoever is whipped falls to their knees for a moment. They cannot walk or jump,
 * players get lower (smaller hitbox, lower eyes) and everyone sees them kneel. Afterwards they cannot
 * be knelt again for a second, so nobody is kept down forever.
 *
 * <p>The server keeps the truth; clients get a {@link KneelPacket} and keep their own copy
 * ({@link #CLIENT}) for drawing, for the local player's controls and for the hitbox.</p>
 */
public final class Kneel {
    private static final UUID SLOW = UUID.fromString("5a7d9c84-3f0b-4b7e-9d0a-6c1f2e8b4a31");
    /** Ticks after standing up during which the same entity cannot be knelt again. */
    private static final int IMMUNITY = 20;
    /** A kneeling player: about two thirds as tall. */
    private static final EntityDimensions KNEELING = EntityDimensions.scalable(0.6F, 1.3F);
    private static final float KNEELING_EYES = 1.12F;

    /** Server: entity id to the server tick when it stands up. */
    private static final Map<Integer, Long> SERVER = new HashMap<>();
    private static final Map<Integer, Long> IMMUNE = new HashMap<>();
    /** Client: entity id to the client tick when it stands up (filled from packets). */
    public static final Map<Integer, Long> CLIENT = new HashMap<>();
    /** Ticks counted by the client, for {@link #CLIENT}. */
    public static long clientTicks;

    private Kneel() {
    }

    public static boolean isKneeling(Entity entity) {
        if (entity.level().isClientSide) {
            Long until = CLIENT.get(entity.getId());
            return until != null && until > clientTicks;
        }
        return SERVER.containsKey(entity.getId());
    }

    /** Server: {@code target} falls to its knees for {@code ticks} ticks, whipped by {@code by}. */
    public static void start(LivingEntity target, int ticks, ServerPlayer by) {
        if (ticks <= 0 || !target.isAlive() || target instanceof EnderDragon || target instanceof WitherBoss
                || target.isSpectator()) {
            return;
        }
        MinecraftServer server = by.server;
        long now = server.getTickCount();
        int id = target.getId();
        boolean already = SERVER.containsKey(id);
        if (!already && IMMUNE.getOrDefault(id, 0L) > now) {
            return;
        }
        SERVER.put(id, now + ticks);
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW) == null) {
            speed.addTransientModifier(new AttributeModifier(SLOW, "Sottomissione", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        target.refreshDimensions();
        FrustaNetwork.sendToTrackingAndSelf(target, new KneelPacket(id, ticks));
        if (!already) {
            ServerLevel level = by.serverLevel();
            level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.KNEEL.get(), SoundSource.PLAYERS,
                    1.4F, 0.9F + target.getRandom().nextFloat() * 0.2F);
            if (target instanceof ServerPlayer victim) {
                victim.displayClientMessage(Component.translatable("message.frusta.kneel.victim", by.getDisplayName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
            }
            by.displayClientMessage(Component.translatable("message.frusta.kneel.whipper", target.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Server, every tick: who stands up again. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<Map.Entry<Integer, Long>> it = SERVER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (entry.getValue() > now) {
                continue;
            }
            it.remove();
            IMMUNE.put(entry.getKey(), now + IMMUNITY);
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(entry.getKey());
                if (entity instanceof LivingEntity living) {
                    standUp(living);
                    break;
                }
            }
        }
        IMMUNE.values().removeIf(until -> until <= now);
    }

    private static void standUp(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
        }
        entity.refreshDimensions();
        FrustaNetwork.sendToTrackingAndSelf(entity, new KneelPacket(entity.getId(), 0));
    }

    /** Server: a player who just started seeing {@code entity} learns that it is kneeling. */
    public static void onStartTracking(Entity entity, ServerPlayer viewer) {
        Long until = SERVER.get(entity.getId());
        if (until != null) {
            long left = until - viewer.server.getTickCount();
            if (left > 0) {
                FrustaNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> viewer),
                        new KneelPacket(entity.getId(), (int) left));
            }
        }
    }

    /** Server: forget an entity that left (and make sure it can walk if it comes back). */
    public static void forget(LivingEntity entity) {
        if (SERVER.remove(entity.getId()) != null) {
            AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(SLOW);
            }
        }
    }

    /** Kneeling players are smaller and see from lower down. Both sides, so they agree on the hitbox. */
    public static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && event.getPose() == Pose.STANDING && entity.level() != null && isKneeling(entity)) {
            event.setNewSize(KNEELING);
            event.setNewEyeHeight(KNEELING_EYES);
        }
    }
}
