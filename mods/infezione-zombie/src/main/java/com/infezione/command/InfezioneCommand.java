package com.infezione.command;

import com.infezione.config.InfezioneConfig;
import com.infezione.infection.Change;
import com.infezione.infection.Infection;
import com.infezione.infection.Infections;
import com.infezione.infection.Stages;
import com.infezione.infection.Symptom;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /infezione (operators): infect, set the stage, cure, stop and restart the clock, look, and change how
 * long a stage lasts.
 *
 * <pre>
 * /infezione infetta &lt;giocatori&gt;
 * /infezione stadio &lt;giocatori&gt; &lt;1-5&gt;
 * /infezione cura &lt;giocatori&gt;
 * /infezione pausa &lt;giocatori&gt;
 * /infezione riprendi &lt;giocatori&gt;
 * /infezione info [giocatori]
 * /infezione durata &lt;minuti&gt;
 * /infezione sintomo &lt;giocatori&gt; &lt;tosse|sangue|spasmo|vertigini|convulsioni|svenimento|morso|lamento|trasformazione&gt;
 * </pre>
 */
public final class InfezioneCommand {
    private InfezioneCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("infezione")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("infetta")
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> infect(c, EntityArgument.getPlayers(c, "giocatori")))))
                .then(Commands.literal("stadio")
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .then(Commands.argument("stadio", IntegerArgumentType.integer(1, Stages.ZOMBIE))
                                        .executes(c -> stage(c, EntityArgument.getPlayers(c, "giocatori"),
                                                IntegerArgumentType.getInteger(c, "stadio"))))))
                .then(Commands.literal("cura")
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> cure(c, EntityArgument.getPlayers(c, "giocatori")))))
                .then(Commands.literal("pausa")
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> pause(c, EntityArgument.getPlayers(c, "giocatori"), true))))
                .then(Commands.literal("riprendi")
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> pause(c, EntityArgument.getPlayers(c, "giocatori"), false))))
                .then(Commands.literal("info")
                        .executes(c -> info(c, c.getSource().getServer().getPlayerList().getPlayers()))
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(c -> info(c, EntityArgument.getPlayers(c, "giocatori")))))
                .then(Commands.literal("sintomo")
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .then(Commands.argument("sintomo", StringArgumentType.word())
                                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Symptom.values()).map(s -> s.command), builder))
                                        .executes(c -> symptom(c, EntityArgument.getPlayers(c, "giocatori"),
                                                StringArgumentType.getString(c, "sintomo"))))))
                .then(Commands.literal("durata")
                        .executes(InfezioneCommand::showDuration)
                        .then(Commands.argument("minuti", DoubleArgumentType.doubleArg(0.05, 1440.0))
                                .executes(c -> duration(c, DoubleArgumentType.getDouble(c, "minuti"))))));
    }

    private static int infect(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int done = 0;
        for (ServerPlayer player : players) {
            int stage = Infection.of(player).stage();
            if (stage > Stages.HEALTHY) {
                c.getSource().sendFailure(Component.translatable("infezione.command.already", player.getDisplayName(), stage));
                continue;
            }
            Infections.setStage(player, 1, Change.INFECTED);
            done++;
        }
        report(c, done, "infezione.command.infected", players);
        return done;
    }

    private static int stage(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int stage) {
        for (ServerPlayer player : players) {
            Infections.setStage(player, stage, Infection.of(player).stage() == Stages.HEALTHY ? Change.INFECTED : Change.SET);
        }
        int count = players.size();
        c.getSource().sendSuccess(() -> count == 1
                ? Component.translatable("infezione.command.stage.one", players.iterator().next().getDisplayName(), stage)
                : Component.translatable("infezione.command.stage.many", count, stage), true);
        return count;
    }

    private static int cure(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int done = 0;
        for (ServerPlayer player : players) {
            if (Infection.of(player).stage() > Stages.HEALTHY) {
                Infections.setStage(player, Stages.HEALTHY, Change.CURED);
                done++;
            }
        }
        report(c, done, "infezione.command.cured", players);
        return done;
    }

    private static int pause(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, boolean paused) {
        for (ServerPlayer player : players) {
            Infections.setPaused(player, paused);
        }
        report(c, players.size(), paused ? "infezione.command.paused" : "infezione.command.resumed", players);
        return players.size();
    }

    private static int info(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int infected = 0;
        for (ServerPlayer player : players) {
            Infection infection = Infection.of(player);
            int stage = infection.stage();
            if (stage == Stages.HEALTHY) {
                continue;
            }
            infected++;
            Component name = Component.translatable("infezione.stage." + stage);
            Component line;
            if (stage == Stages.ZOMBIE) {
                line = Component.translatable("infezione.command.info.zombie", player.getDisplayName());
            } else if (infection.paused()) {
                line = Component.translatable("infezione.command.info.paused", player.getDisplayName(), stage, name);
            } else {
                int seconds = Math.max(0, Infections.stageTicks() - infection.ticks()) / 20;
                line = Component.translatable("infezione.command.info.line", player.getDisplayName(), stage, name,
                        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
            }
            c.getSource().sendSuccess(() -> line, false);
        }
        if (infected == 0) {
            c.getSource().sendSuccess(() -> Component.translatable("infezione.command.info.none"), false);
        }
        return infected;
    }

    private static int symptom(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, String name) {
        Symptom symptom = Symptom.byCommand(name);
        if (symptom == null) {
            c.getSource().sendFailure(Component.translatable("infezione.command.symptom.unknown", name));
            return 0;
        }
        for (ServerPlayer player : players) {
            Infections.episode(player, symptom);
        }
        c.getSource().sendSuccess(() -> Component.translatable("infezione.command.symptom", name, players.size()), true);
        return players.size();
    }

    private static int showDuration(CommandContext<CommandSourceStack> c) {
        double minutes = InfezioneConfig.SERVER.minutesPerStage.get();
        c.getSource().sendSuccess(() -> Component.translatable("infezione.command.duration.now", format(minutes)), false);
        return (int) Math.round(minutes);
    }

    private static int duration(CommandContext<CommandSourceStack> c, double minutes) {
        int before = Infections.stageTicks();
        InfezioneConfig.SERVER.minutesPerStage.set(minutes);
        InfezioneConfig.SERVER.minutesPerStage.save();
        for (ServerPlayer player : c.getSource().getServer().getPlayerList().getPlayers()) {
            Infections.rescale(player, before, Infections.stageTicks());
        }
        c.getSource().sendSuccess(() -> Component.translatable("infezione.command.duration.set", format(minutes)), true);
        return (int) Math.round(minutes);
    }

    private static String format(double minutes) {
        return minutes == Math.rint(minutes) ? String.valueOf((long) minutes) : String.format(Locale.ROOT, "%.2f", minutes);
    }

    private static void report(CommandContext<CommandSourceStack> c, int done, String key, Collection<ServerPlayer> players) {
        if (done == 0) {
            return;
        }
        List<ServerPlayer> list = List.copyOf(players);
        c.getSource().sendSuccess(() -> done == 1 && list.size() == 1
                ? Component.translatable(key + ".one", list.get(0).getDisplayName())
                : Component.translatable(key + ".many", done), true);
    }
}
