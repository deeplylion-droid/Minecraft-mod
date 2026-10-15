package com.disabilities.server;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Picks;
import com.disabilities.condition.Taken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /disabilita: everybody can list conditions and read what they do; operators can reopen the
 * chooser, assign, randomize, remove, and trigger episodes (handy for testing and for the video).
 */
public final class DisabilityCommand {
    private DisabilityCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> CONDITIONS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Condition.values()).map(x -> x.command), b);
    private static final List<String> EVENTS = List.of("sonno", "tic", "starnuto", "inciampo", "blocco", "dimenticanza", "ferita",
            "sveglia", "fiato");
    private static final SuggestionProvider<CommandSourceStack> EVENT_NAMES = (c, b) -> SharedSuggestionProvider.suggest(EVENTS, b);

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("disabilita")
                .executes(c -> mine(c.getSource()))
                .then(Commands.literal("lista").executes(c -> list(c.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("condizione", StringArgumentType.word()).suggests(CONDITIONS)
                                .executes(c -> info(c.getSource(), condition(c, "condizione")))))
                .then(Commands.literal("scegli").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> reopen(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> reopen(c.getSource(), EntityArgument.getPlayers(c, "giocatori")))))
                .then(Commands.literal("assegna").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .then(Commands.argument("prima", StringArgumentType.word()).suggests(CONDITIONS)
                                        .then(Commands.argument("seconda", StringArgumentType.word()).suggests(CONDITIONS)
                                                .executes(c -> assign(c.getSource(), EntityArgument.getPlayers(c, "giocatori"),
                                                        condition(c, "prima"), condition(c, "seconda")))))))
                .then(Commands.literal("casuale").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> random(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> random(c.getSource(), EntityArgument.getPlayers(c, "giocatori")))))
                .then(Commands.literal("togli").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> remove(c.getSource(), EntityArgument.getPlayers(c, "giocatori")))))
                .then(Commands.literal("evento").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("tipo", StringArgumentType.word()).suggests(EVENT_NAMES)
                                .executes(c -> event(c.getSource(), StringArgumentType.getString(c, "tipo"),
                                        List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("giocatori", EntityArgument.players())
                                        .executes(c -> event(c.getSource(), StringArgumentType.getString(c, "tipo"),
                                                EntityArgument.getPlayers(c, "giocatori")))))));
    }

    private static Condition condition(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, name);
        Condition condition = Condition.byId(id);
        if (condition == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.translatable("disabilities.command.unknown", id)).create();
        }
        return condition;
    }

    private static MutableComponent describeAll(int mask) {
        MutableComponent out = Component.empty();
        List<Condition> list = Picks.list(mask);
        if (list.isEmpty()) {
            return Component.translatable("disabilities.command.none").withStyle(ChatFormatting.DARK_GRAY);
        }
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            out.append(Chooser.describe(list.get(i)));
        }
        return out;
    }

    private static int mine(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Picks picks = ModData.picks(player);
        source.sendSuccess(() -> Component.translatable("disabilities.command.yours", describeAll(picks.mask())), false);
        for (Condition c : picks.list()) {
            source.sendSuccess(() -> Component.literal(" - ").append(c.displayName().withStyle(s -> s.withColor(c.color)))
                    .append(Component.literal(": ")).append(c.forYou().withStyle(ChatFormatting.GRAY)), false);
        }
        return picks.list().size();
    }

    private static int list(CommandSourceStack source) {
        Taken taken = Chooser.taken(source.getServer());
        List<Map.Entry<UUID, Taken.Entry>> entries = new ArrayList<>(taken.entries().entrySet());
        entries.sort((a, b) -> a.getValue().name().compareToIgnoreCase(b.getValue().name()));
        source.sendSuccess(() -> Component.translatable("disabilities.command.list_header", entries.size()).withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<UUID, Taken.Entry> e : entries) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(e.getKey());
            Component name = online != null ? online.getDisplayName()
                    : Component.literal(e.getValue().name()).withStyle(ChatFormatting.DARK_GRAY);
            source.sendSuccess(() -> Component.empty().append(name).append(Component.literal(": ")).append(describeAll(e.getValue().mask())), false);
        }
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (Chooser.isChoosing(player)) {
                source.sendSuccess(() -> Component.translatable("disabilities.command.choosing", player.getDisplayName())
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        int free = 0;
        int used = 0;
        for (Map.Entry<UUID, Taken.Entry> e : entries) {
            used |= e.getValue().mask();
        }
        for (Condition c : Condition.values()) {
            if ((used & c.bit()) == 0) {
                free++;
            }
        }
        int freeCount = free;
        source.sendSuccess(() -> Component.translatable("disabilities.command.free", freeCount).withStyle(ChatFormatting.GRAY), false);
        return entries.size();
    }

    private static int info(CommandSourceStack source, Condition c) {
        source.sendSuccess(() -> Chooser.describe(c), false);
        source.sendSuccess(() -> Component.translatable("disabilities.command.for_you", c.forYou()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("disabilities.command.for_others", c.forOthers()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int reopen(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Chooser.clear(player, true);
        }
        source.sendSuccess(() -> Component.translatable("disabilities.command.reopened", players.size()), true);
        return players.size();
    }

    private static int assign(CommandSourceStack source, Collection<ServerPlayer> players, Condition a, Condition b) throws CommandSyntaxException {
        if (!a.compatibleWith(b)) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.translatable("disabilities.command.incompatible", a.displayName(), b.displayName())).create();
        }
        for (ServerPlayer player : players) {
            Chooser.assign(player, a.bit() | b.bit(), true);
        }
        return players.size();
    }

    private static int random(CommandSourceStack source, Collection<ServerPlayer> players) {
        int done = 0;
        for (ServerPlayer player : players) {
            int taken = Chooser.takenFor(player);
            List<Integer> pairs = new ArrayList<>();
            for (int pair : Condition.validPairs()) {
                if ((pair & taken) == 0) {
                    pairs.add(pair);
                }
            }
            if (pairs.isEmpty()) {
                source.sendFailure(Component.translatable("disabilities.command.no_free", player.getDisplayName()));
                continue;
            }
            Chooser.assign(player, pairs.get(player.getRandom().nextInt(pairs.size())), true);
            done++;
        }
        return done;
    }

    private static int remove(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Chooser.clear(player, false);
        }
        source.sendSuccess(() -> Component.translatable("disabilities.command.removed", players.size()), true);
        return players.size();
    }

    private static int event(CommandSourceStack source, String type, Collection<ServerPlayer> players) throws CommandSyntaxException {
        if (!EVENTS.contains(type)) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.translatable("disabilities.command.unknown_event", type, String.join(", ", EVENTS))).create();
        }
        for (ServerPlayer player : players) {
            switch (type) {
                case "sonno" -> Ailments.fallAsleep(player, 140, false);
                case "sveglia" -> Ailments.wake(player, null);
                case "tic" -> Ailments.tic(player, -1);
                case "starnuto" -> Ailments.sneeze(player);
                case "inciampo" -> Ailments.stumble(player);
                case "blocco" -> Ailments.freeze(player, 60);
                case "dimenticanza" -> Ailments.forgetInventory(player);
                case "ferita" -> player.hurtServer(player.level(), player.level().damageSources().generic(), 2.0F);
                case "fiato" -> player.setAirSupply(0);
                default -> {
                }
            }
        }
        return players.size();
    }
}
