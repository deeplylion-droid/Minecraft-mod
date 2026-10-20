package com.disabilities.server;

import com.disabilities.Disabilities;
import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Picks;
import com.disabilities.condition.Status;
import com.disabilities.condition.Taken;
import com.disabilities.item.ModItems;
import com.disabilities.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The mandatory choice: a player who has not picked yet gets the chooser screen as soon as they
 * join, and cannot be hurt until they pick. The server checks every choice (valid pair, not taken
 * by someone else when the "unique" rule is on), then tells everybody.
 */
public final class Chooser {
    private Chooser() {
    }

    public static final FontDescription BADGE_FONT = new FontDescription.Resource(Disabilities.id("badges"));

    /** Players who joined but whose client has not announced the mod yet: kicked if it never does. */
    private static final Map<UUID, Integer> WAITING = new HashMap<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Net.Choose.TYPE, (payload, context) -> choose(context.player(), payload.mask()));
    }

    public static void onJoin(ServerPlayer player) {
        WAITING.put(player.getUUID(), 0);
    }

    /** Called every server tick: finishes the join of players whose client is ready. */
    public static void tick(MinecraftServer server) {
        if (WAITING.isEmpty()) {
            return;
        }
        var it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (ServerPlayNetworking.canSend(player, Net.OpenChooser.TYPE)) {
                it.remove();
                ready(player);
            } else if (e.getValue() > 100) {
                it.remove();
                player.connection.disconnect(Component.literal(
                        "Questo mondo usa la mod Disabilità: installala (con Fabric API) per entrare.\n"
                                + "This world uses the Disabilità mod: install it (with Fabric API) to join."));
            } else {
                e.setValue(e.getValue() + 1);
            }
        }
    }

    private static void ready(ServerPlayer player) {
        Picks picks = ModData.picks(player);
        Body.refresh(player);
        if (!picks.isEmpty()) {
            // keep the name in the list up to date (also sends the roster)
            setTaken(player.level().getServer(), player.getUUID(), player.getGameProfile().name(), picks.mask());
        } else {
            broadcastRoster(player.level().getServer());
        }
        if (!picks.settled()) {
            open(player);
        }
    }

    public static boolean isChoosing(ServerPlayer player) {
        return !ModData.picks(player).settled();
    }

    public static void open(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Net.OpenChooser.TYPE)) {
            ServerPlayNetworking.send(player, new Net.OpenChooser(takenFor(player)));
        }
    }

    /** Conditions this player cannot pick because someone else has them (0 if the rule is off). */
    public static int takenFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (!server.getGameRules().get(ModRules.UNIQUE)) {
            return 0;
        }
        return taken(server).maskExcept(player.getUUID());
    }

    public static Taken taken(MinecraftServer server) {
        return server.overworld().getAttachedOrElse(ModData.TAKEN, Taken.EMPTY);
    }

    private static void setTaken(MinecraftServer server, UUID id, String name, int mask) {
        Taken before = taken(server);
        Taken after = before.with(id, name, mask);
        if (!after.equals(before)) {
            server.overworld().setAttached(ModData.TAKEN, after);
        }
        broadcastRoster(server);
    }

    /** Sends everyone's conditions to every client (tab list badges). */
    public static void broadcastRoster(MinecraftServer server) {
        Map<UUID, Integer> masks = new HashMap<>();
        for (Map.Entry<UUID, Taken.Entry> e : taken(server).entries().entrySet()) {
            masks.put(e.getKey(), e.getValue().mask());
        }
        Net.Roster roster = new Net.Roster(masks);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, Net.Roster.TYPE)) {
                ServerPlayNetworking.send(player, roster);
            }
        }
    }

    private static void choose(ServerPlayer player, int mask) {
        if (!isChoosing(player)) {
            return;
        }
        if (!Picks.isValidChoice(mask)) {
            open(player);
            return;
        }
        if ((mask & takenFor(player)) != 0) {
            player.sendSystemMessage(Component.translatable("disabilities.chooser.taken").withStyle(ChatFormatting.RED));
            open(player);
            return;
        }
        assign(player, mask, true);
    }

    /** Gives the player these conditions (and the aids that come with them). */
    public static void assign(ServerPlayer player, int mask, boolean announce) {
        player.setAttached(ModData.PICKS, new Picks(mask, true));
        player.setAttached(ModData.STATUS, Status.NONE);
        MinecraftServer server = player.level().getServer();
        setTaken(server, player.getUUID(), player.getGameProfile().name(), mask);
        Ailments.forget(player);
        Body.refresh(player);
        giveAids(player, false);
        List<Condition> list = Picks.list(mask);
        if (announce && !list.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    names.append(Component.translatable(i == list.size() - 1 ? "disabilities.and" : "disabilities.comma"));
                }
                names.append(describe(list.get(i)));
            }
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("disabilities.announce", player.getDisplayName(), names).withStyle(ChatFormatting.GRAY), false);
        }
        for (Condition c : list) {
            player.sendSystemMessage(Component.empty().append(describe(c)).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(c.forYou().withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Removes the conditions; with {@code reopen} the player must choose again, otherwise they stay without. */
    public static void clear(ServerPlayer player, boolean reopen) {
        player.setAttached(ModData.PICKS, new Picks(0, !reopen));
        player.setAttached(ModData.STATUS, Status.NONE);
        setTaken(player.level().getServer(), player.getUUID(), player.getGameProfile().name(), 0);
        Ailments.forget(player);
        Body.refresh(player);
        if (reopen) {
            open(player);
        }
    }

    /** Icon and coloured name, with what it does as hover text. */
    public static MutableComponent describe(Condition c) {
        MutableComponent hover = c.displayName().withStyle(Style.EMPTY.withColor(c.color).withBold(true))
                .append(Component.literal("\n"))
                .append(c.forYou().withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(c.forOthers().withStyle(ChatFormatting.DARK_GRAY));
        return Component.empty()
                .append(badge(String.valueOf(c.glyph())))
                .append(Component.literal(" "))
                .append(c.displayName().withStyle(Style.EMPTY.withColor(c.color)))
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    /** Text in the icon font (white, so the icons keep their colours). */
    public static MutableComponent badge(String glyphs) {
        return Component.literal(glyphs).withStyle(Style.EMPTY.withFont(BADGE_FONT).withColor(ChatFormatting.WHITE)
                .withBold(false).withItalic(false));
    }

    /** The cane, the inhaler and some bandages, for the conditions that need them (only if missing). */
    public static void giveAids(ServerPlayer player, boolean onlyEssentials) {
        Picks picks = ModData.picks(player);
        List<ItemStack> aids = new ArrayList<>();
        if (picks.has(Condition.BLIND)) {
            aids.add(new ItemStack(ModItems.WHITE_CANE));
        }
        if (picks.has(Condition.ASTHMA)) {
            aids.add(new ItemStack(ModItems.INHALER));
        }
        if (picks.has(Condition.HEMOPHILIA) && !onlyEssentials) {
            aids.add(new ItemStack(ModItems.BANDAGE, 8));
        }
        for (ItemStack stack : aids) {
            if (hasItem(player, stack.getItem())) {
                continue;
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
}
