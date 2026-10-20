package com.frusta.server;

import com.frusta.FrustaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Game events on both sides: ticking the moves and the kneeling, and the kneeling hitbox. */
@Mod.EventBusSubscriber(modid = FrustaMod.MODID)
public final class ServerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WhipMoves.tick(event.getServer());
            Kneel.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WhipMoves.forget(event.getEntity().getUUID());
        Kneel.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) {
            Kneel.forget(living);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer) {
            Kneel.onStartTracking(event.getTarget(), viewer);
        }
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        Kneel.onSize(event);
    }

    private ServerEvents() {
    }
}
