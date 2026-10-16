package com.filorosso.server;

import com.filorosso.data.BondLevel;
import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.data.Palette;
import com.filorosso.net.Net;
import com.filorosso.sound.ModSounds;
import com.filorosso.util.Text;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The bond grows by doing things together: staying close, fighting side by side, catching each
 * other's falls, feeding, sleeping, reviving. Each level changes the thread and unlocks an ability.
 */
public final class Affinity {
    private Affinity() {
    }

    public static void init() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
            if (killer instanceof ServerPlayer player && killed instanceof LivingEntity) {
                onKill(player, (LivingEntity) killed);
            }
        });
    }

    /** Adds affinity (three times as much during the romantic event). */
    public static void add(MinecraftServer server, Couple couple, int amount, boolean quiet) {
        if (amount <= 0) {
            return;
        }
        if (Events.active(Events.Kind.ROMANCE)) {
            amount *= 3;
        }
        BondLevel before = couple.level();
        couple.xp += amount;
        BondLevel after = couple.level();
        Bonds.sync(server, couple);
        if (after != before) {
            levelUp(server, couple, after);
        }
    }

    private static void levelUp(MinecraftServer server, Couple couple, BondLevel level) {
        couple.points += 1;
        Component name = level.displayName().copy().withStyle(s -> s.withColor(level.color));
        ServerPlayer a = Bonds.player(server, couple.a);
        ServerPlayer b = Bonds.player(server, couple.b);
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            if (p == null) {
                continue;
            }
            Bonds.title(p, Component.translatable("filorosso.title.level", level.number(), name),
                    Component.translatable("filorosso.level.unlock." + level.number()), 10, 80, 20);
            Bonds.sound(p, ModSounds.LEVEL_UP, 1.0F);
            Bonds.hearts(p, 30);
        }
        if (a != null && b != null) {
            Net.ThreadFx fx = new Net.ThreadFx(Net.ThreadFx.LEVEL_UP, a.getId(), b.getId());
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                if (viewer.level() == a.level() && viewer.distanceToSqr(a) < 96 * 96) {
                    Bonds.send(viewer, fx);
                }
            }
        }
        server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.heart(couple.color)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.level_up", Text.names(couple.nameA, couple.nameB, couple.color), level.number(), name)
                        .withStyle(s -> s.withColor(Palette.rgb(couple.color)))), false);
        Challenges.add(server, couple, Challenges.LEVEL_UP, 1);
        Scores.refresh(server);
    }

    /** Every 15 seconds spent close together. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 300 != 0) {
            return;
        }
        double near = Events.length(server) + 1.0;
        for (Couple c : CoupleBook.get(server).couples) {
            ServerPlayer a = Bonds.player(server, c.a);
            ServerPlayer b = Bonds.player(server, c.b);
            if (Links.usable(a) && Links.usable(b) && a.level() == b.level() && a.distanceTo(b) <= near && a.isAlive() && b.isAlive()) {
                add(server, c, 1, true);
            }
        }
    }

    private static void onKill(ServerPlayer killer, LivingEntity killed) {
        Couple couple = Bonds.coupleOf(killer);
        if (couple == null) {
            return;
        }
        ServerPlayer partner = Bonds.player(killer.level().getServer(), couple.other(killer.getUUID()));
        if (partner == null || partner.level() != killer.level() || partner.distanceTo(killer) > 12.0) {
            return;
        }
        boolean together = Combat.hitBy(killed, partner.getUUID(), killer.level().getServer().overworld().getGameTime(), 100);
        add(killer.level().getServer(), couple, together ? 4 : 2, true);
        if (killed instanceof Enemy) {
            Challenges.add(killer.level().getServer(), couple, Challenges.KILLS, 1);
        }
    }

    /** Feeding the partner by hand. */
    public static void onFeed(ServerPlayer feeder, ServerPlayer partner) {
        Couple couple = Bonds.coupleOf(feeder);
        if (couple == null || !couple.has(partner.getUUID())) {
            return;
        }
        add(feeder.level().getServer(), couple, 4, true);
        Challenges.add(feeder.level().getServer(), couple, Challenges.FEED, 1);
        partner.sendSystemMessage(Component.translatable("filorosso.msg.fed", feeder.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }
}
