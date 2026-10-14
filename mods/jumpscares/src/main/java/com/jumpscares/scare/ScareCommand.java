package com.jumpscares.scare;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /jumpscare &lt;giocatori&gt; [clown|dama|ragno|cadavere|demone|casuale] [secondi]
 * <p>The feedback goes only to whoever typed the command, so a victim who is an operator does not see it coming.
 */
public final class ScareCommand {
    private static final String RANDOM = "casuale";
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            name -> Component.translatableEscape("commands.jumpscares.unknown", name));

    private ScareCommand() {
    }

    private static List<String> names() {
        List<String> names = new ArrayList<>();
        for (ScareKind kind : ScareKind.values()) {
            names.add(kind.command);
        }
        names.add(RANDOM);
        return names;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("jumpscare")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("giocatori", EntityArgument.players())
                                .executes(ctx -> run(ctx, RANDOM, 0))
                                .then(Commands.argument("mostro", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(names(), builder))
                                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "mostro"), 0))
                                        .then(Commands.argument("secondi", IntegerArgumentType.integer(0, 3600))
                                                .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "mostro"),
                                                        IntegerArgumentType.getInteger(ctx, "secondi"))))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String type, int seconds) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "giocatori");
        boolean random = type.equalsIgnoreCase(RANDOM) || type.equalsIgnoreCase("random");
        ScareKind fixed = random ? null : ScareKind.byName(type);
        if (!random && fixed == null) {
            throw UNKNOWN.create(type);
        }
        CommandSourceStack source = ctx.getSource();
        int shown = 0;
        for (ServerPlayer target : targets) {
            ScareKind kind = fixed != null ? fixed : ScareKind.random(target.getRandom());
            if (seconds > 0) {
                Scares.later(target, seconds * 20, player -> Scares.scare(player, kind));
                shown++;
            } else if (Scares.scare(target, kind)) {
                shown++;
            }
        }
        Component who = targets.size() == 1 ? targets.iterator().next().getDisplayName() : Component.literal(String.valueOf(targets.size()));
        if (shown == 0) {
            source.sendFailure(Component.translatable("commands.jumpscares.no_mod", who));
            return 0;
        }
        Component what = fixed != null ? fixed.displayName() : Component.translatable("jumpscares.kind.random");
        if (seconds > 0) {
            source.sendSuccess(() -> Component.translatable("commands.jumpscares.later", what, who, seconds), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.jumpscares.now", what, who), false);
        }
        return shown;
    }
}
