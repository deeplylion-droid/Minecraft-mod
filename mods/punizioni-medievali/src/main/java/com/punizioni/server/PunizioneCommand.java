package com.punizioni.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.punizioni.data.Body;
import com.punizioni.data.Limb;
import com.punizioni.data.ModData;
import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * /punizione (operators): put someone in the nearest machine of a kind and start it, move the nearest
 * machine on to its next step, let go, heal, or give a body its marks directly (for videos and tests).
 */
public final class PunizioneCommand {
    private PunizioneCommand() {
    }

    private static final String[] BONES = {"braccio_destro", "braccio_sinistro", "gamba_destra", "gamba_sinistra", "tutte"};

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        Predicate<CommandSourceStack> op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        var root = Commands.literal("punizione").requires(op);
        for (MachineKind kind : MachineKind.ALL) {
            root.then(Commands.literal(kind.command)
                    .then(Commands.argument("vittime", EntityArgument.entities())
                            .executes(c -> start(c.getSource(), kind, EntityArgument.getEntities(c, "vittime")))));
        }
        root.then(Commands.literal("avanza")
                        .executes(c -> advance(c.getSource(), null))
                        .then(Commands.argument("macchina", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(MachineKind.ALL).map(k -> k.command), b))
                                .executes(c -> advance(c.getSource(), MachineKind.byCommand(StringArgumentType.getString(c, "macchina"))))))
                .then(Commands.literal("libera")
                        .executes(c -> releaseNearest(c.getSource()))
                        .then(Commands.argument("vittime", EntityArgument.entities())
                                .executes(c -> release(c.getSource(), EntityArgument.getEntities(c, "vittime")))))
                .then(Commands.literal("guarisci")
                        .then(Commands.argument("vittime", EntityArgument.entities())
                                .executes(c -> heal(c.getSource(), EntityArgument.getEntities(c, "vittime")))))
                .then(Commands.literal("amputa")
                        .then(Commands.argument("vittima", EntityArgument.entity())
                                .then(Commands.argument("arto", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Limb.ALL).map(l -> l.command), b))
                                        .executes(c -> amputate(c.getSource(), EntityArgument.getEntity(c, "vittima"),
                                                StringArgumentType.getString(c, "arto"))))))
                .then(Commands.literal("spezza")
                        .then(Commands.argument("vittima", EntityArgument.entity())
                                .then(Commands.argument("osso", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BONES, b))
                                        .executes(c -> breakBone(c.getSource(), EntityArgument.getEntity(c, "vittima"),
                                                StringArgumentType.getString(c, "osso"))))))
                .then(Commands.literal("segni")
                        .then(Commands.argument("vittima", EntityArgument.entity())
                                .then(Commands.argument("frustate", IntegerArgumentType.integer(0, Body.MAX_LASHES))
                                        .then(Commands.argument("morsi", IntegerArgumentType.integer(0, Body.MAX_BITES))
                                                .then(Commands.argument("bruciature", IntegerArgumentType.integer(0, 100))
                                                        .executes(c -> marks(c.getSource(), EntityArgument.getEntity(c, "vittima"),
                                                                IntegerArgumentType.getInteger(c, "frustate"), IntegerArgumentType.getInteger(c, "morsi"),
                                                                IntegerArgumentType.getInteger(c, "bruciature"))))))))
                .then(Commands.literal("allunga")
                        .then(Commands.argument("vittima", EntityArgument.entity())
                                .executes(c -> stretch(c.getSource(), EntityArgument.getEntity(c, "vittima")))));
        dispatcher.register(root);
    }

    private static CommandSyntaxException error(String key, Object... args) {
        return new SimpleCommandExceptionType(Component.translatable(key, args)).create();
    }

    private static @Nullable Machine nearest(ServerLevel level, Vec3 at, @Nullable MachineKind kind, double range) {
        List<Machine> machines = level.getEntitiesOfClass(Machine.class, new AABB(at, at).inflate(range),
                m -> kind == null || m.kind() == kind);
        return machines.stream().min(Comparator.comparingDouble(m -> m.position().distanceToSqr(at))).orElse(null);
    }

    private static int start(CommandSourceStack source, MachineKind kind, Collection<? extends Entity> victims) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        int n = 0;
        for (Entity e : victims) {
            if (!(e instanceof LivingEntity victim) || !(e.level() instanceof ServerLevel level)) {
                continue;
            }
            Machine m = nearest(level, victim.position(), kind, 64.0);
            if (m == null) {
                throw error("punizioni.command.no_machine", Component.translatable(kind.item().getDescriptionId()));
            }
            if (m.commandStart(level, victim, player)) {
                n++;
            }
        }
        int done = n;
        source.sendSuccess(() -> Component.translatable("punizioni.command.started", done), true);
        return n;
    }

    private static int advance(CommandSourceStack source, @Nullable MachineKind kind) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Machine m = nearest(source.getLevel(), source.getPosition(), kind, 48.0);
        if (m == null) {
            throw error("punizioni.command.no_machine_near");
        }
        m.commandAdvance(source.getLevel(), player);
        return 1;
    }

    private static int releaseNearest(CommandSourceStack source) throws CommandSyntaxException {
        Machine m = nearest(source.getLevel(), source.getPosition(), null, 48.0);
        if (m == null) {
            throw error("punizioni.command.no_machine_near");
        }
        m.commandRelease();
        source.sendSuccess(() -> Component.translatable("punizioni.command.released", 1), true);
        return 1;
    }

    private static int release(CommandSourceStack source, Collection<? extends Entity> victims) {
        int n = 0;
        for (Entity e : victims) {
            if (e.getVehicle() instanceof Machine m) {
                m.commandRelease();
                n++;
            }
        }
        int done = n;
        source.sendSuccess(() -> Component.translatable("punizioni.command.released", done), true);
        return n;
    }

    private static int heal(CommandSourceStack source, Collection<? extends Entity> victims) {
        for (Entity e : victims) {
            ModData.setBody(e, Body.NONE);
        }
        source.sendSuccess(() -> Component.translatable("punizioni.command.healed", victims.size()), true);
        return victims.size();
    }

    private static int amputate(CommandSourceStack source, Entity victim, String name) throws CommandSyntaxException {
        Limb limb = Limb.byCommand(name);
        if (limb == null) {
            throw error("punizioni.command.unknown_limb", name);
        }
        ModData.setBody(victim, ModData.body(victim).without(limb));
        source.sendSuccess(() -> Component.translatable("punizioni.command.amputated", victim.getDisplayName(), Component.translatable(limb.key())),
                true);
        return 1;
    }

    private static int breakBone(CommandSourceStack source, Entity victim, String name) throws CommandSyntaxException {
        int bone = switch (name) {
            case "braccio_destro" -> Body.RIGHT_ARM;
            case "braccio_sinistro" -> Body.LEFT_ARM;
            case "gamba_destra" -> Body.RIGHT_LEG;
            case "gamba_sinistra" -> Body.LEFT_LEG;
            case "tutte" -> Body.ALL_BONES;
            default -> throw error("punizioni.command.unknown_bone", name);
        };
        ModData.setBody(victim, ModData.body(victim).withBroken(bone));
        source.sendSuccess(() -> Component.translatable("punizioni.command.broken", victim.getDisplayName()), true);
        return 1;
    }

    private static int marks(CommandSourceStack source, Entity victim, int lashes, int bites, int burns) {
        Body b = ModData.body(victim);
        long now = source.getLevel().getGameTime();
        ModData.setBody(victim, new Body(b.missing(), b.broken(), lashes, bites, burns / 100.0F, lashes > 0 ? now : 0L, b.stretchUntil(), b.stretch(),
                b.flags()));
        source.sendSuccess(() -> Component.translatable("punizioni.command.marked", victim.getDisplayName()), true);
        return 1;
    }

    private static int stretch(CommandSourceStack source, Entity victim) {
        ModData.setBody(victim, ModData.body(victim).stretched(source.getLevel().getGameTime() + Body.STRETCH_TICKS, 1.0F));
        source.sendSuccess(() -> Component.translatable("punizioni.command.stretched", victim.getDisplayName()), true);
        return 1;
    }
}
