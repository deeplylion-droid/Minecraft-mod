package com.belowbedrock.haunt;

import com.belowbedrock.world.Faded;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /faded} (operators only), to film or test things on demand:
 * {@code /faded event <name> [players]}, {@code /faded him <mode> [players]}, {@code /faded fall [players]}.
 */
public final class FadedCommand {
    private FadedCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher));
    }

    private interface Action {
        boolean run(ServerPlayer player, String argument);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faded").requires(source -> source.hasPermission(2))
                .then(Commands.literal("event")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Director.eventNames(), builder))
                                .executes(ctx -> run(ctx, self(ctx), "event", Director::triggerEvent))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> run(ctx, EntityArgument.getPlayers(ctx, "players"), "event", Director::triggerEvent)))))
                .then(Commands.literal("him")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Director.himNames(), builder))
                                .executes(ctx -> run(ctx, self(ctx), "mode", Director::summonHim))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> run(ctx, EntityArgument.getPlayers(ctx, "players"), "mode", Director::summonHim)))))
                .then(Commands.literal("fall")
                        .executes(ctx -> run(ctx, self(ctx), null, FadedCommand::fall))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> run(ctx, EntityArgument.getPlayers(ctx, "players"), null, FadedCommand::fall)))));
    }

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return List.of(ctx.getSource().getPlayerOrException());
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String argumentName, Action action) {
        String argument = argumentName == null ? "" : StringArgumentType.getString(ctx, argumentName).toLowerCase(Locale.ROOT);
        List<String> valid = "event".equals(argumentName) ? Director.eventNames() : "mode".equals(argumentName) ? Director.himNames() : List.of("");
        if (!valid.contains(argument)) {
            ctx.getSource().sendFailure(Component.translatable("commands.belowbedrock.unknown", argument, String.join(", ", valid)));
            return 0;
        }
        int done = 0;
        for (ServerPlayer player : players) {
            if (action.run(player, argument)) {
                done++;
            }
        }
        if (done == 0) {
            ctx.getSource().sendFailure(Component.translatable("commands.belowbedrock.nothing"));
            return 0;
        }
        int count = done;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.belowbedrock.done", count), true);
        return done;
    }

    /** Drops the player just below the bottom of the world (overworld or faded world): the fall starts by itself. */
    private static boolean fall(ServerPlayer player, String unused) {
        if (!Faded.hasDeepVoid(player.level())) {
            return false;
        }
        player.teleportTo(player.serverLevel(), player.getX(), player.level().getMinBuildHeight() - 4, player.getZ(),
                player.getYRot(), player.getXRot());
        return true;
    }
}
