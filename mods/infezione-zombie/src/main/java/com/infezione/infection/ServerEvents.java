package com.infezione.infection;

import com.infezione.InfezioneMod;
import com.infezione.command.InfezioneCommand;
import com.infezione.config.InfezioneConfig;
import com.infezione.network.InfezioneNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Game events on the server: the clock of the infection, death, respawn, who sees whom, the zombies. */
@Mod.EventBusSubscriber(modid = InfezioneMod.MODID)
public final class ServerEvents {
    /** Players who died infected, and at which stage (for the message when they respawn). */
    private static final Map<UUID, Integer> DIED_INFECTED = new HashMap<>();

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        InfezioneCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            Infections.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Infections.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Infections.refresh(player);
        }
    }

    /** Whoever comes in sight learns their infection (healthy too: it may be old news on that client). */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            InfezioneNetwork.sendTo(viewer, Infections.state(target, Change.NONE));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            int stage = Infection.of(player).stage();
            if (stage > Stages.HEALTHY && InfezioneConfig.SERVER.deathCures.get()) {
                DIED_INFECTED.put(player.getUUID(), stage);
            }
        }
    }

    /** The new player entity: after a death the infection is gone (unless the config says otherwise). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !InfezioneConfig.SERVER.deathCures.get()) {
            Infection.copy(event.getOriginal(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer stage = DIED_INFECTED.remove(player.getUUID());
        if (stage != null) {
            // the new entity is healthy: say so to everyone who still sees the old look
            InfezioneNetwork.sendToTrackingAndSelf(player, Infections.state(player, Change.DIED));
            player.sendSystemMessage(Component.translatable(stage == Stages.ZOMBIE ? "infezione.death.zombie" : "infezione.death.infected"));
        }
        Infections.refresh(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DIED_INFECTED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        ZombiePlayer.onChangeTarget(event);
    }

    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        ZombiePlayer.onEaten(event);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        ZombiePlayer.onHurt(event);
    }
}
