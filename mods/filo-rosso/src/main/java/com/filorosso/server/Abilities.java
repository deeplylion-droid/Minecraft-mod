package com.filorosso.server;

import com.filorosso.data.BondLevel;
import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.ModData;
import com.filorosso.net.Net;
import com.filorosso.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** What a stronger bond lets you do, each with its own key. */
public final class Abilities {
    private Abilities() {
    }

    public enum Ability {
        /** Yank the partner towards you. */
        PULL(BondLevel.RIBBON, 20 * 15),
        /** Give two of your hearts to the partner. */
        GIFT(BondLevel.BRAID, 20 * 10),
        /** Fly to the partner, wherever they are. */
        REACH(BondLevel.GOLD, 20 * 300);

        public static final Ability[] ALL = values();
        public final BondLevel level;
        public final int cooldown;

        Ability(BondLevel level, int cooldown) {
            this.level = level;
            this.cooldown = cooldown;
        }
    }

    private static final Map<UUID, long[]> READY = new HashMap<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Net.UseAbility.TYPE, (p, c) -> {
            if (p.ability() >= 0 && p.ability() < Ability.ALL.length) {
                use(c.player(), Ability.ALL[p.ability()]);
            }
        });
    }

    private static long[] ready(UUID id) {
        return READY.computeIfAbsent(id, k -> new long[Ability.ALL.length]);
    }

    public static void sendCooldowns(ServerPlayer player) {
        long[] r = ready(player.getUUID());
        List<Long> list = new java.util.ArrayList<>();
        for (long v : r) {
            list.add(v);
        }
        Bonds.send(player, new Net.Cooldowns(list));
    }

    private static void deny(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    public static void use(ServerPlayer player, Ability ability) {
        MinecraftServer server = player.level().getServer();
        long now = server.overworld().getGameTime();
        Couple couple = Bonds.coupleOf(player);
        if (couple == null) {
            deny(player, "filorosso.ability.single");
            return;
        }
        if (!couple.level().atLeast(ability.level)) {
            deny(player, "filorosso.ability.locked", ability.level.number());
            return;
        }
        if (ModData.downed(player) || !player.isAlive()) {
            return;
        }
        long[] r = ready(player.getUUID());
        if (now < r[ability.ordinal()]) {
            deny(player, "filorosso.ability.cooldown", (r[ability.ordinal()] - now + 19) / 20);
            return;
        }
        ServerPlayer partner = Bonds.player(server, couple.other(player.getUUID()));
        if (partner == null || !partner.isAlive()) {
            deny(player, "filorosso.ability.no_partner");
            return;
        }
        boolean done = switch (ability) {
            case PULL -> pull(player, partner);
            case GIFT -> gift(player, partner);
            case REACH -> reach(player, partner);
        };
        if (done) {
            int cooldown = ability.cooldown;
            if (ability == Ability.PULL && couple.bondType() == BondType.MAGNET) {
                cooldown /= 2;
            }
            r[ability.ordinal()] = now + cooldown;
            sendCooldowns(player);
        }
    }

    private static boolean pull(ServerPlayer player, ServerPlayer partner) {
        if (partner.level() != player.level() || partner.distanceTo(player) > 40.0) {
            deny(player, "filorosso.ability.too_far");
            return false;
        }
        Vec3 dir = player.position().subtract(partner.position());
        double d = dir.length();
        if (d < 1.5) {
            return false;
        }
        Vec3 v = dir.normalize().scale(Math.min(1.6, 0.5 + d * 0.12)).add(0.0, 0.42, 0.0);
        partner.setDeltaMovement(v);
        partner.connection.send(new ClientboundSetEntityMotionPacket(partner));
        partner.resetFallDistance();
        Bonds.sound(player, ModSounds.PULL, 1.0F);
        Bonds.sound(partner, ModSounds.PULL, 1.1F);
        Net.ThreadFx fx = new Net.ThreadFx(Net.ThreadFx.PULL, player.getId(), partner.getId());
        for (ServerPlayer viewer : player.level().getServer().getPlayerList().getPlayers()) {
            if (viewer.level() == player.level() && viewer.distanceToSqr(player) < 96 * 96) {
                Bonds.send(viewer, fx);
            }
        }
        return true;
    }

    private static boolean gift(ServerPlayer player, ServerPlayer partner) {
        if (partner.level() != player.level() || partner.distanceTo(player) > 12.0) {
            deny(player, "filorosso.ability.too_far");
            return false;
        }
        if (player.getHealth() <= 8.0F) {
            deny(player, "filorosso.ability.gift_weak");
            return false;
        }
        if (partner.getHealth() >= partner.getMaxHealth()) {
            deny(player, "filorosso.ability.gift_full", partner.getGameProfile().name());
            return false;
        }
        player.setHealth(player.getHealth() - 4.0F);
        partner.heal(4.0F);
        ServerLevel level = (ServerLevel) player.level();
        Vec3 from = player.position().add(0, 1.2, 0);
        Vec3 to = partner.position().add(0, 1.2, 0);
        for (int i = 0; i <= 8; i++) {
            Vec3 p = from.lerp(to, i / 8.0);
            level.sendParticles(ParticleTypes.HEART, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        Bonds.sound(player, ModSounds.GIFT, 1.0F);
        Bonds.sound(partner, ModSounds.GIFT, 1.2F);
        partner.sendSystemMessage(Component.translatable("filorosso.msg.gift", player.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    private static boolean reach(ServerPlayer player, ServerPlayer partner) {
        Vec3 to = partner.position();
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.6, 0.4, 0.1);
        player.teleportTo((ServerLevel) partner.level(), to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        Bonds.sound(player, ModSounds.TELEPORT, 1.0F);
        Bonds.hearts(player, 16);
        partner.sendSystemMessage(Component.translatable("filorosso.msg.reached", player.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }
}
