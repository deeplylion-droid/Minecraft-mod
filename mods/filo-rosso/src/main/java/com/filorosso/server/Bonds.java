package com.filorosso.server;

import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.data.Link;
import com.filorosso.data.ModData;
import com.filorosso.data.Palette;
import com.filorosso.data.Status;
import com.filorosso.net.Net;
import com.filorosso.sound.ModSounds;
import com.filorosso.util.Text;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is tied to whom: proposals ("will you be tied to me?"), the bond, breaking up. The couple
 * book is the truth; every change is copied into both partners' {@link Link} (synced to clients).
 */
public final class Bonds {
    private Bonds() {
    }

    /** A proposal waits this long for an answer. */
    private static final int PROPOSAL_TICKS = 20 * 90;

    private record Pending(UUID target, long expires) {
    }

    private static final Map<UUID, Pending> PROPOSALS = new HashMap<>();
    /** Partners who asked to break up, and until when the request holds. */
    private static final Map<UUID, Long> BREAKUPS = new HashMap<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Net.Propose.TYPE, (p, c) -> propose(c.player(), p.target()));
        ServerPlayNetworking.registerGlobalReceiver(Net.Answer.TYPE, (p, c) -> answer(c.player(), p.proposer(), p.yes()));
        ServerPlayNetworking.registerGlobalReceiver(Net.OpenMenu.TYPE, (p, c) -> sendCandidates(c.player(), true));
        ServerPlayNetworking.registerGlobalReceiver(Net.BreakUp.TYPE, (p, c) -> askBreakUp(c.player()));
        ServerPlayNetworking.registerGlobalReceiver(Net.BreakUpAnswer.TYPE, (p, c) -> answerBreakUp(c.player(), p.yes()));
    }

    // ------------------------------------------------------------------ lookups

    public static CoupleBook book(MinecraftServer server) {
        return CoupleBook.get(server);
    }

    public static Couple coupleOf(ServerPlayer player) {
        return book(player.level().getServer()).of(player.getUUID());
    }

    /** The partner, if online. */
    public static ServerPlayer partner(ServerPlayer player) {
        Link link = ModData.link(player);
        if (link.partner().isEmpty()) {
            return null;
        }
        return player.level().getServer().getPlayerList().getPlayer(link.partner().get());
    }

    public static ServerPlayer player(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    // ------------------------------------------------------------------ syncing

    /** Copies the couple into both partners' links (if online). */
    public static void sync(MinecraftServer server, Couple couple) {
        book(server).setDirty();
        sync(server, couple, couple.a);
        sync(server, couple, couple.b);
    }

    private static void sync(MinecraftServer server, Couple couple, UUID id) {
        ServerPlayer player = player(server, id);
        if (player == null) {
            return;
        }
        UUID other = couple.other(id);
        Link link = new Link(Optional.of(other), couple.nameOf(other), couple.color, couple.xp, couple.type, couple.cutUntil, couple.since);
        if (!link.equals(ModData.link(player))) {
            player.setAttached(ModData.LINK, link);
        }
    }

    /** A player without a couple (or whose couple is gone) gets an empty link. */
    public static void refresh(ServerPlayer player) {
        CoupleBook book = book(player.level().getServer());
        Couple couple = book.of(player.getUUID());
        if (couple == null) {
            if (ModData.link(player).bound()) {
                player.setAttached(ModData.LINK, Link.NONE);
            }
            return;
        }
        String name = player.getGameProfile().name();
        if (!name.equals(couple.nameOf(player.getUUID()))) {
            couple.setName(player.getUUID(), name);
            book.setDirty();
        }
        sync(player.level().getServer(), couple);
    }

    // ------------------------------------------------------------------ the list of players

    public static List<Net.Candidate> candidates(ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        CoupleBook book = book(server);
        List<Net.Candidate> list = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == viewer) {
                continue;
            }
            Couple c = book.of(p.getUUID());
            int status;
            String other = "";
            if (c != null) {
                status = Net.Candidate.BOUND;
                other = c.nameOf(c.other(p.getUUID()));
            } else if (proposed(p.getUUID(), viewer.getUUID())) {
                status = Net.Candidate.PROPOSED_TO_YOU;
            } else if (proposed(viewer.getUUID(), p.getUUID())) {
                status = Net.Candidate.YOU_PROPOSED;
            } else {
                status = Net.Candidate.FREE;
            }
            list.add(new Net.Candidate(p.getUUID(), p.getGameProfile().name(), status, other));
        }
        list.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
        return list;
    }

    public static void sendCandidates(ServerPlayer player, boolean open) {
        send(player, new Net.Candidates(candidates(player), open));
    }

    /** Players whose list may be open get it again (someone joined, left, proposed or got tied). */
    public static void refreshLists(MinecraftServer server) {
        Map<UUID, Integer> colors = new HashMap<>();
        for (Couple c : book(server).couples) {
            colors.put(c.a, c.color);
            colors.put(c.b, c.color);
        }
        Net.Roster roster = new Net.Roster(colors);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendCandidates(p, false);
            send(p, roster);
        }
    }

    // ------------------------------------------------------------------ proposals

    private static boolean proposed(UUID from, UUID to) {
        Pending p = PROPOSALS.get(from);
        return p != null && p.target.equals(to);
    }

    private static void propose(ServerPlayer from, UUID targetId) {
        MinecraftServer server = from.level().getServer();
        CoupleBook book = book(server);
        long now = server.overworld().getGameTime();
        ServerPlayer target = player(server, targetId);
        if (target == null || target == from) {
            return;
        }
        if (book.of(from.getUUID()) != null) {
            from.sendSystemMessage(Component.translatable("filorosso.msg.already_you").withStyle(ChatFormatting.RED));
            return;
        }
        Couple theirs = book.of(targetId);
        if (theirs != null) {
            from.sendSystemMessage(Component.translatable("filorosso.msg.already_them", target.getGameProfile().name(),
                    theirs.nameOf(theirs.other(targetId))).withStyle(ChatFormatting.RED));
            sendCandidates(from, false);
            return;
        }
        Pending back = PROPOSALS.get(targetId);
        if (back != null && back.target.equals(from.getUUID()) && back.expires > now) {
            // they chose each other
            bond(target, from);
            return;
        }
        PROPOSALS.put(from.getUUID(), new Pending(targetId, now + PROPOSAL_TICKS));
        from.sendSystemMessage(Component.translatable("filorosso.msg.proposed", target.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE));
        send(target, new Net.Proposal(from.getUUID(), from.getGameProfile().name()));
        target.sendSystemMessage(Component.empty().append(Text.icon(Text.RING, 0xFFD030)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.proposal", from.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE)));
        sound(target, ModSounds.PROPOSAL, 1.0F);
        refreshLists(server);
    }

    private static void answer(ServerPlayer target, UUID proposerId, boolean yes) {
        MinecraftServer server = target.level().getServer();
        long now = server.overworld().getGameTime();
        Pending p = PROPOSALS.get(proposerId);
        ServerPlayer proposer = player(server, proposerId);
        if (p == null || !p.target.equals(target.getUUID()) || p.expires < now || proposer == null) {
            PROPOSALS.remove(proposerId);
            target.sendSystemMessage(Component.translatable("filorosso.msg.expired").withStyle(ChatFormatting.GRAY));
            sendCandidates(target, false);
            return;
        }
        PROPOSALS.remove(proposerId);
        if (!yes) {
            proposer.sendSystemMessage(Component.empty().append(Text.icon(Text.BROKEN, 0xA0A0A0)).append(Component.literal(" "))
                    .append(Component.translatable("filorosso.msg.refused", target.getGameProfile().name()).withStyle(ChatFormatting.GRAY)));
            sound(proposer, ModSounds.REFUSE, 1.0F);
            refreshLists(server);
            return;
        }
        CoupleBook book = book(server);
        if (book.of(proposerId) != null || book.of(target.getUUID()) != null) {
            target.sendSystemMessage(Component.translatable("filorosso.msg.too_late").withStyle(ChatFormatting.GRAY));
            refreshLists(server);
            return;
        }
        bond(proposer, target);
    }

    // ------------------------------------------------------------------ bond and break up

    /** Ties these two (both online and free): the ceremony, the announcement. */
    public static Couple bond(ServerPlayer a, ServerPlayer b) {
        MinecraftServer server = a.level().getServer();
        CoupleBook book = book(server);
        PROPOSALS.remove(a.getUUID());
        PROPOSALS.remove(b.getUUID());
        PROPOSALS.values().removeIf(p -> p.target.equals(a.getUUID()) || p.target.equals(b.getUUID()));
        long now = server.overworld().getGameTime();
        BondType type = BondType.draw(server.overworld().getRandom(), null);
        Couple couple = new Couple(book.nextId++, a.getUUID(), b.getUUID(), a.getGameProfile().name(), b.getGameProfile().name(),
                book.freeColor(), 0, type.ordinal(), book.session, 0, now, Map.of(), List.of(), 0);
        book.couples.add(couple);
        sync(server, couple);

        Component names = Text.names(couple.nameA, couple.nameB, couple.color);
        server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.RING, 0xFFD030)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.bonded", names).withStyle(ChatFormatting.LIGHT_PURPLE)), false);
        for (ServerPlayer p : List.of(a, b)) {
            ServerPlayer other = p == a ? b : a;
            title(p, Component.empty().append(Text.heart(couple.color)).append(Component.literal(" "))
                            .append(Component.translatable("filorosso.title.bonded").withStyle(s -> s.withColor(Palette.rgb(couple.color))))
                            .append(Component.literal(" ")).append(Text.heart(couple.color)),
                    Component.translatable("filorosso.title.bonded.sub", other.getGameProfile().name()), 10, 70, 20);
            sound(p, ModSounds.BOND, 1.0F);
            hearts(p, 24);
            Destiny.announce(p, couple);
        }
        Challenges.onBond(server, couple);
        refreshLists(server);
        return couple;
    }

    /** Ends a couple (break up, or an operator): the thread snaps. */
    public static void unbind(MinecraftServer server, Couple couple, boolean announce) {
        CoupleBook book = book(server);
        book.couples.remove(couple);
        book.setDirty();
        BREAKUPS.remove(couple.a);
        BREAKUPS.remove(couple.b);
        ServerPlayer a = player(server, couple.a);
        ServerPlayer b = player(server, couple.b);
        if (a != null && b != null) {
            Net.ThreadFx fx = new Net.ThreadFx(Net.ThreadFx.SNAP, a.getId(), b.getId());
            for (ServerPlayer viewer : PlayerLookup.tracking(a)) {
                send(viewer, fx);
            }
            send(a, fx);
        }
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            if (p != null) {
                p.setAttached(ModData.LINK, Link.NONE);
                p.setAttached(ModData.STATUS, Status.NONE);
                sound(p, ModSounds.SNAP, 1.0F);
            }
        }
        if (announce) {
            server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.BROKEN, 0xB0B0B0)).append(Component.literal(" "))
                    .append(Component.translatable("filorosso.msg.broke_up", couple.nameA, couple.nameB).withStyle(ChatFormatting.GRAY)), false);
        }
        Scores.refresh(server);
        refreshLists(server);
    }

    private static void askBreakUp(ServerPlayer player) {
        Couple couple = coupleOf(player);
        if (couple == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        long now = server.overworld().getGameTime();
        UUID otherId = couple.other(player.getUUID());
        Long theirs = BREAKUPS.get(otherId);
        if (theirs != null && theirs > now) {
            // both want it
            unbind(server, couple, true);
            return;
        }
        ServerPlayer other = player(server, otherId);
        if (other == null) {
            player.sendSystemMessage(Component.translatable("filorosso.msg.breakup_offline").withStyle(ChatFormatting.GRAY));
            return;
        }
        BREAKUPS.put(player.getUUID(), now + PROPOSAL_TICKS);
        player.sendSystemMessage(Component.translatable("filorosso.msg.breakup_asked", other.getGameProfile().name()).withStyle(ChatFormatting.GRAY));
        send(other, new Net.BreakUpAsk(player.getGameProfile().name()));
    }

    private static void answerBreakUp(ServerPlayer player, boolean yes) {
        Couple couple = coupleOf(player);
        if (couple == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        UUID otherId = couple.other(player.getUUID());
        Long asked = BREAKUPS.remove(otherId);
        ServerPlayer other = player(server, otherId);
        if (asked == null || asked < server.overworld().getGameTime()) {
            return;
        }
        if (yes) {
            unbind(server, couple, true);
        } else if (other != null) {
            other.sendSystemMessage(Component.empty().append(Text.heart(couple.color)).append(Component.literal(" "))
                    .append(Component.translatable("filorosso.msg.breakup_refused", player.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }

    /** Ties every free online player to another, at random (an operator's shortcut). */
    public static int pairRandomly(MinecraftServer server) {
        CoupleBook book = book(server);
        List<ServerPlayer> free = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (book.of(p.getUUID()) == null && !p.isSpectator()) {
                free.add(p);
            }
        }
        Collections.shuffle(free);
        int made = 0;
        for (int i = 0; i + 1 < free.size(); i += 2) {
            bond(free.get(i), free.get(i + 1));
            made++;
        }
        return made;
    }

    // ------------------------------------------------------------------ joining and leaving

    public static void onLeave(ServerPlayer player) {
        PROPOSALS.remove(player.getUUID());
        PROPOSALS.values().removeIf(p -> p.target.equals(player.getUUID()));
        BREAKUPS.remove(player.getUUID());
    }

    /** The player's client is ready: their link, their list if they are still free. */
    public static void onReady(ServerPlayer player) {
        refresh(player);
        MinecraftServer server = player.level().getServer();
        Couple couple = book(server).of(player.getUUID());
        if (couple == null) {
            sendCandidates(player, true);
        } else {
            Destiny.onJoin(player, couple);
        }
        refreshLists(server);
    }

    // ------------------------------------------------------------------ little helpers

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void title(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
    }

    /** A sound only this player hears, right where they are (the bell: everyone hears it once). */
    public static void hear(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
                player.getX(), player.getEyeY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    public static void hearts(ServerPlayer player, int count) {
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(), count, 0.6, 0.5, 0.6, 0.1);
    }
}
