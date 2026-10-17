package com.filorosso.server;

import com.filorosso.data.BondLevel;
import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.entity.Recisione;
import com.filorosso.util.Text;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /filo: everybody can see their couple and the leaderboard; operators can tie, untie, shuffle,
 * set fates, start events, start a new live, add affinity and call Recisione.
 */
public final class FiloCommand {
    private FiloCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> TYPES = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(BondType.ALL).map(t -> t.command), b);
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (Events.Kind k : Events.Kind.ALL) {
            names.add(k.command);
        }
        names.add("fine");
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<CommandSourceStack> CHALLENGES = (c, b) -> SharedSuggestionProvider.suggest(Challenges.all(), b);

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> register(dispatcher));
    }

    private static CommandSyntaxException error(String key, Object... args) {
        return new SimpleCommandExceptionType(Component.translatable(key, args)).create();
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        java.util.function.Predicate<CommandSourceStack> op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        dispatcher.register(Commands.literal("filo")
                .executes(c -> mine(c.getSource()))
                .then(Commands.literal("lista").executes(c -> list(c.getSource())))
                .then(Commands.literal("classifica").executes(c -> list(c.getSource())))
                .then(Commands.literal("lega").requires(op)
                        .then(Commands.argument("primo", EntityArgument.player())
                                .then(Commands.argument("secondo", EntityArgument.player())
                                        .executes(c -> tie(c.getSource(), EntityArgument.getPlayer(c, "primo"), EntityArgument.getPlayer(c, "secondo"))))))
                .then(Commands.literal("slega").requires(op)
                        .then(Commands.argument("giocatore", EntityArgument.player())
                                .executes(c -> untie(c.getSource(), EntityArgument.getPlayer(c, "giocatore")))))
                .then(Commands.literal("casuale").requires(op).executes(c -> shuffle(c.getSource())))
                .then(Commands.literal("affinita").requires(op)
                        .then(Commands.argument("giocatore", EntityArgument.player())
                                .then(Commands.argument("quanta", IntegerArgumentType.integer(1, 100000))
                                        .executes(c -> affinity(c.getSource(), EntityArgument.getPlayer(c, "giocatore"),
                                                IntegerArgumentType.getInteger(c, "quanta"))))))
                .then(Commands.literal("livello").requires(op)
                        .then(Commands.argument("giocatore", EntityArgument.player())
                                .then(Commands.argument("livello", IntegerArgumentType.integer(1, 5))
                                        .executes(c -> level(c.getSource(), EntityArgument.getPlayer(c, "giocatore"),
                                                IntegerArgumentType.getInteger(c, "livello"))))))
                .then(Commands.literal("destino").requires(op)
                        .executes(c -> reroll(c.getSource()))
                        .then(Commands.argument("tipo", StringArgumentType.word()).suggests(TYPES)
                                .then(Commands.argument("giocatore", EntityArgument.player())
                                        .executes(c -> fate(c.getSource(), StringArgumentType.getString(c, "tipo"), EntityArgument.getPlayer(c, "giocatore"))))))
                .then(Commands.literal("evento").requires(op)
                        .then(Commands.argument("nome", StringArgumentType.word()).suggests(EVENTS)
                                .executes(c -> event(c.getSource(), StringArgumentType.getString(c, "nome")))))
                .then(Commands.literal("nuovalive").requires(op).executes(c -> newSession(c.getSource())))
                .then(Commands.literal("imprese").requires(op)
                        .executes(c -> {
                            Challenges.newSession(c.getSource().getServer());
                            return 1;
                        })
                        .then(Commands.argument("prima", StringArgumentType.word()).suggests(CHALLENGES)
                                .then(Commands.argument("seconda", StringArgumentType.word()).suggests(CHALLENGES)
                                        .then(Commands.argument("terza", StringArgumentType.word()).suggests(CHALLENGES)
                                                .executes(c -> {
                                                    Challenges.set(c.getSource().getServer(), List.of(StringArgumentType.getString(c, "prima"),
                                                            StringArgumentType.getString(c, "seconda"), StringArgumentType.getString(c, "terza")));
                                                    return 1;
                                                })))))
                .then(Commands.literal("punti").requires(op)
                        .then(Commands.argument("giocatore", EntityArgument.player())
                                .then(Commands.argument("punti", IntegerArgumentType.integer(-1000, 1000))
                                        .executes(c -> points(c.getSource(), EntityArgument.getPlayer(c, "giocatore"),
                                                IntegerArgumentType.getInteger(c, "punti"))))))
                .then(Commands.literal("taglia").requires(op)
                        .then(Commands.argument("giocatore", EntityArgument.player())
                                .executes(c -> cut(c.getSource(), EntityArgument.getPlayer(c, "giocatore")))))
                .then(Commands.literal("boss").requires(op).executes(c -> boss(c.getSource()))
                        // for testing and for videos: make it attack now, or skip to a later form
                        .then(Commands.literal("attacco")
                                .then(Commands.argument("attacco", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Recisione.ACTION_NAMES).filter(n -> !n.isEmpty()), b))
                                        .executes(c -> bossAttack(c.getSource(), StringArgumentType.getString(c, "attacco")))))
                        .then(Commands.literal("fase")
                                .then(Commands.argument("fase", IntegerArgumentType.integer(2, 3))
                                        .executes(c -> bossPhase(c.getSource(), IntegerArgumentType.getInteger(c, "fase"))))))
                .then(Commands.literal("servo").requires(op).executes(c -> servant(c.getSource()))));
    }

    private static Couple coupleOf(ServerPlayer p) throws CommandSyntaxException {
        Couple c = Bonds.coupleOf(p);
        if (c == null) {
            throw error("filorosso.command.single", p.getGameProfile().name());
        }
        return c;
    }

    private static int mine(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Couple c = Bonds.coupleOf(player);
        if (c == null) {
            source.sendSuccess(() -> Component.translatable("filorosso.command.you_single").withStyle(ChatFormatting.GRAY), false);
            Bonds.sendCandidates(player, true);
            return 0;
        }
        BondLevel level = c.level();
        source.sendSuccess(() -> Component.empty().append(Text.names(c.nameA, c.nameB, c.color)).append(Component.literal("  "))
                .append(Component.translatable("filorosso.command.info", level.number(), level.displayName(), c.xp, c.bondType().displayName(), c.points)
                        .withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<Couple> couples = new ArrayList<>(CoupleBook.get(server).couples);
        couples.sort((a, b) -> Integer.compare(b.points, a.points));
        if (couples.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("filorosso.command.no_couples").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        int i = 1;
        for (Couple c : couples) {
            int n = i++;
            source.sendSuccess(() -> Component.literal(n + ". ").withStyle(ChatFormatting.GOLD).append(Text.names(c.nameA, c.nameB, c.color))
                    .append(Component.literal("  ")).append(Component.translatable("filorosso.command.row", c.points, c.level().number(),
                            c.bondType().displayName()).withStyle(ChatFormatting.GRAY)), false);
        }
        return couples.size();
    }

    private static int tie(CommandSourceStack source, ServerPlayer a, ServerPlayer b) throws CommandSyntaxException {
        if (a == b) {
            throw error("filorosso.command.same");
        }
        MinecraftServer server = source.getServer();
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            Couple c = Bonds.coupleOf(p);
            if (c != null) {
                Bonds.unbind(server, c, true);
            }
        }
        Bonds.bond(a, b);
        return 1;
    }

    private static int untie(CommandSourceStack source, ServerPlayer p) throws CommandSyntaxException {
        Bonds.unbind(source.getServer(), coupleOf(p), true);
        return 1;
    }

    private static int shuffle(CommandSourceStack source) {
        int made = Bonds.pairRandomly(source.getServer());
        source.sendSuccess(() -> Component.translatable("filorosso.command.shuffled", made), true);
        return made;
    }

    private static int affinity(CommandSourceStack source, ServerPlayer p, int amount) throws CommandSyntaxException {
        Affinity.add(source.getServer(), coupleOf(p), amount, false);
        return 1;
    }

    private static int level(CommandSourceStack source, ServerPlayer p, int level) throws CommandSyntaxException {
        Couple c = coupleOf(p);
        int target = BondLevel.ALL[level - 1].xp;
        if (target > c.xp) {
            Affinity.add(source.getServer(), c, target - c.xp, false);
        } else {
            c.xp = target;
            Bonds.sync(source.getServer(), c);
        }
        return 1;
    }

    private static int reroll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        for (Couple c : CoupleBook.get(server).couples) {
            Destiny.reroll(server, c);
            for (ServerPlayer p : new ServerPlayer[]{Bonds.player(server, c.a), Bonds.player(server, c.b)}) {
                if (p != null) {
                    Destiny.announce(p, c);
                }
            }
        }
        return 1;
    }

    private static int fate(CommandSourceStack source, String name, ServerPlayer p) throws CommandSyntaxException {
        BondType type = BondType.byCommand(name);
        if (type == null) {
            throw error("filorosso.command.unknown_type", name);
        }
        Destiny.set(source.getServer(), coupleOf(p), type);
        return 1;
    }

    private static int event(CommandSourceStack source, String name) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        if (name.equals("fine")) {
            Events.end(server);
            return 1;
        }
        Events.Kind kind = Events.Kind.byCommand(name);
        if (kind == null) {
            throw error("filorosso.command.unknown_event", name);
        }
        Events.end(server);
        if (!Events.start(server, kind)) {
            throw error("filorosso.command.event_failed");
        }
        return 1;
    }

    private static int newSession(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Destiny.newSession(server);
        for (Couple c : CoupleBook.get(server).couples) {
            for (ServerPlayer p : new ServerPlayer[]{Bonds.player(server, c.a), Bonds.player(server, c.b)}) {
                if (p != null) {
                    Destiny.announce(p, c);
                }
            }
        }
        return 1;
    }

    private static int points(CommandSourceStack source, ServerPlayer p, int points) throws CommandSyntaxException {
        Couple c = coupleOf(p);
        c.points += points;
        CoupleBook.get(source.getServer()).setDirty();
        Scores.refresh(source.getServer());
        Challenges.sendInfo(source.getServer(), c);
        return 1;
    }

    private static int cut(CommandSourceStack source, ServerPlayer p) throws CommandSyntaxException {
        Tether.cut(source.getServer(), coupleOf(p), 20 * 15);
        return 1;
    }

    private static int boss(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        if (!Boss.summon(p.level().getServer(), (net.minecraft.server.level.ServerLevel) p.level(), p.position().add(p.getLookAngle().scale(12.0)))) {
            throw error("filorosso.command.boss_failed");
        }
        return 1;
    }

    /** The Recisione closest to whoever runs the command. */
    private static Recisione nearestBoss(CommandSourceStack source) throws CommandSyntaxException {
        Recisione best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.server.level.ServerLevel level : source.getServer().getAllLevels()) {
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof Recisione r && r.isAlive()) {
                    double d = level == source.getLevel() ? r.distanceToSqr(source.getPosition()) : Double.MAX_VALUE / 2;
                    if (d < bestD) {
                        bestD = d;
                        best = r;
                    }
                }
            }
        }
        if (best == null) {
            throw error("filorosso.command.no_boss");
        }
        return best;
    }

    private static int bossAttack(CommandSourceStack source, String name) throws CommandSyntaxException {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        // "colpo" was the name of the first boss's arm swing: it still works for the strings' lash
        int action = Arrays.asList(Recisione.ACTION_NAMES).indexOf(key.equals("colpo") ? "frusta" : key);
        if (action <= 0) {
            throw new SimpleCommandExceptionType(Component.translatable("filorosso.command.unknown_attack", name)).create();
        }
        nearestBoss(source).force(action);
        return 1;
    }

    private static int bossPhase(CommandSourceStack source, int phase) throws CommandSyntaxException {
        Recisione boss = nearestBoss(source);
        boss.setHealth(Math.min(boss.getHealth(), boss.getMaxHealth() * (phase == 3 ? 0.3F : 0.6F)));
        return 1;
    }

    private static int servant(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        Servants.spawnNear((net.minecraft.server.level.ServerLevel) p.level(), p, true);
        return 1;
    }
}
