package com.rainbowyoyo.client;

import com.rainbowyoyo.RainbowYoyoMod;
import com.rainbowyoyo.client.render.FirstPersonArm;
import com.rainbowyoyo.entity.YoyoEntity;
import com.rainbowyoyo.item.YoyoItem;
import com.rainbowyoyo.network.YoyoActionPacket;
import com.rainbowyoyo.network.YoyoNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game events: the yo-yo keys and the rendering hooks. */
@Mod.EventBusSubscriber(modid = RainbowYoyoMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean useWasDown;
    private static int attackCooldown;

    /**
     * Before vanilla reads the keys: attack clicks while holding the yo-yo become yo-yo shots or
     * tricks, and letting go of the use key calls the yo-yo back.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            useWasDown = false;
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        boolean holding = player.getMainHandItem().getItem() instanceof YoyoItem;
        boolean useDown = holding && mc.options.keyUse.isDown();
        if (useWasDown && !useDown) {
            YoyoNetwork.sendToServer(new YoyoActionPacket(YoyoActionPacket.RELEASE));
        }
        useWasDown = useDown;
        if (!holding || mc.screen != null || player.isSpectator()) {
            return;
        }
        while (mc.options.keyAttack.consumeClick()) {
            if (attackCooldown == 0) {
                YoyoNetwork.sendToServer(new YoyoActionPacket(YoyoActionPacket.ATTACK));
                attackCooldown = 3;
            }
        }
    }

    /** Holding the attack key with the yo-yo never mines or swings. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && player.getMainHandItem().getItem() instanceof YoyoItem) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        YoyoRenderState.begin(event.getEntity());
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        YoyoRenderState.end();
    }

    /** With the yo-yo out, the first-person hand is drawn in the world instead (see {@link FirstPersonArm}). */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getHand() == InteractionHand.MAIN_HAND && player != null
                && event.getItemStack().getItem() instanceof YoyoItem && YoyoEntity.clientYoyoOf(player) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            FirstPersonArm.render(event);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        YoyoEntity.clearClient();
        useWasDown = false;
    }

    private ClientEvents() {
    }
}
