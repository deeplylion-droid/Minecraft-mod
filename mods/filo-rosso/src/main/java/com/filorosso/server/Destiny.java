package com.filorosso.server;

import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.sound.ModSounds;
import com.filorosso.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Every live (every time the server starts) fate draws a new kind of bond for each couple:
 * elastic, fire, magnetic, swap, jealous, shared hearts... (see {@link BondType}).
 */
public final class Destiny {
    private Destiny() {
    }

    /** The swap bond: positions are exchanged this often. */
    public static final int SWAP_TICKS = 20 * 300;

    /** A new session: a new fate for everybody, new challenges. */
    public static void newSession(MinecraftServer server) {
        CoupleBook book = CoupleBook.get(server);
        book.session++;
        for (Couple c : book.couples) {
            reroll(server, c);
        }
        book.setDirty();
        Challenges.newSession(server);
    }

    public static void reroll(MinecraftServer server, Couple c) {
        CoupleBook book = CoupleBook.get(server);
        c.type = BondType.draw(server.overworld().getRandom(), c.bondType()).ordinal();
        c.typeSession = book.session;
        Bonds.sync(server, c);
    }

    public static void set(MinecraftServer server, Couple c, BondType type) {
        c.type = type.ordinal();
        c.typeSession = CoupleBook.get(server).session;
        Bonds.sync(server, c);
        for (ServerPlayer p : new ServerPlayer[]{Bonds.player(server, c.a), Bonds.player(server, c.b)}) {
            if (p != null) {
                announce(p, c);
            }
        }
    }

    public static void onJoin(ServerPlayer player, Couple couple) {
        MinecraftServer server = player.level().getServer();
        if (couple.typeSession != CoupleBook.get(server).session) {
            reroll(server, couple);
        }
        announce(player, couple);
    }

    /** "Today's fate: ..." */
    public static void announce(ServerPlayer player, Couple couple) {
        BondType type = couple.bondType();
        player.sendSystemMessage(Component.empty().append(Text.icon(Text.STAR, type.color)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.fate", type.displayName().copy().withStyle(s -> s.withColor(type.color).withBold(true))))
                .append(Component.literal(" ")).append(type.description().copy().withStyle(ChatFormatting.GRAY)));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Couple c : CoupleBook.get(server).couples) {
            if (c.bondType() != BondType.SWAP) {
                continue;
            }
            ServerPlayer a = Bonds.player(server, c.a);
            ServerPlayer b = Bonds.player(server, c.b);
            if (!Links.usable(a) || !Links.usable(b) || !a.isAlive() || !b.isAlive()) {
                continue;
            }
            long phase = Math.floorMod(now - c.since, SWAP_TICKS);
            long left = SWAP_TICKS - phase;
            if (left == 60 || left == 40 || left == 20) {
                for (ServerPlayer p : new ServerPlayer[]{a, b}) {
                    p.sendSystemMessage(Component.translatable("filorosso.msg.swap_countdown", left / 20).withStyle(ChatFormatting.GREEN), true);
                    Bonds.sound(p, ModSounds.SWAP, 0.8F + (60 - left) / 100.0F);
                }
            } else if (phase == 0 && now != c.since) {
                swap(a, b);
            }
        }
    }

    /** The two exchange places (and where they look). */
    public static void swap(ServerPlayer a, ServerPlayer b) {
        ServerLevel la = (ServerLevel) a.level();
        ServerLevel lb = (ServerLevel) b.level();
        Vec3 pa = a.position();
        Vec3 pb = b.position();
        float ya = a.getYRot();
        float xa = a.getXRot();
        float yb = b.getYRot();
        float xb = b.getXRot();
        a.teleportTo(lb, pb.x, pb.y, pb.z, Set.of(), yb, xb, true);
        b.teleportTo(la, pa.x, pa.y, pa.z, Set.of(), ya, xa, true);
        a.resetFallDistance();
        b.resetFallDistance();
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            Bonds.sound(p, ModSounds.SWAP, 1.4F);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.5);
            p.sendSystemMessage(Component.translatable("filorosso.msg.swapped").withStyle(ChatFormatting.GREEN), true);
        }
    }
}
