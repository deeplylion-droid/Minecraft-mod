package com.frusta.client;

import com.frusta.FrustaMod;
import com.frusta.client.fx.ScreenShake;
import com.frusta.client.fx.WhipFx;
import com.frusta.client.render.FirstPersonWhip;
import com.frusta.client.render.KneelLayer;
import com.frusta.client.render.ViewSpace;
import com.frusta.client.render.WhipRenderer;
import com.frusta.item.WhipItem;
import com.frusta.server.Kneel;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game events: the attack key, the ticking and drawing of the whips, kneeling, the shake. */
@Mod.EventBusSubscriber(modid = FrustaMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static int lashCooldown;

    /** Before vanilla reads the keys: attack clicks with a whip in hand become lashes. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END) {
            if (mc.level != null && !mc.isPaused()) {
                Kneel.clientTicks++;
                standUp(mc);
                WhipAnims.tick(mc.level);
            }
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (lashCooldown > 0) {
            lashCooldown--;
        }
        if (WhipItem.held(player).isEmpty() || mc.screen != null || player.isSpectator()) {
            return;
        }
        while (mc.options.keyAttack.consumeClick()) {
            if (lashCooldown == 0 && !player.isUsingItem()
                    && !player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())) {
                WhipAnims.lashLocal();
                lashCooldown = 3;
            }
        }
    }

    /** Whoever has knelt long enough stands up (box and eyes back to normal) without waiting for the server. */
    private static void standUp(Minecraft mc) {
        Iterator<Map.Entry<Integer, Long>> it = Kneel.CLIENT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (entry.getValue() <= Kneel.clientTicks) {
                it.remove();
                Entity entity = mc.level.getEntity(entry.getKey());
                if (entity != null) {
                    entity.refreshDimensions();
                }
            }
        }
    }

    /** Holding the attack key with a whip never mines or swings. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && !WhipItem.held(player).isEmpty()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            WhipAnims.newFrame();
            ViewSpace.set(event.getPoseStack().last().pose(), event.getCamera().getPosition());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            FirstPersonWhip.anchor(event.getPartialTick());
            Iterable<Map.Entry<Integer, WhipAnim>> anims = WhipAnims.update(event.getPartialTick());
            WhipRenderer.renderAll(event.getPoseStack(), event.getCamera().getPosition(), anims);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            WhipFx.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick(), WhipAnims.all());
        }
    }

    /** In first person the handle is drawn by the mod, which moves it with the whip. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (FirstPersonWhip.renderHand(event)) {
            event.setCanceled(true);
        }
    }

    /** Last, so it sees the field of view other mods may have changed. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        FirstPersonWhip.onFov(event);
    }

    /** A kneeling player: lower, legs bent (drawn by {@link KneelLayer}), head bowed. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer player) || !Kneel.isKneeling(player)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.rightLeg.visible = false;
        model.leftLeg.visible = false;
        model.rightPants.visible = false;
        model.leftPants.visible = false;
        model.rightArmPose = WhipPoses.KNEEL;
        model.leftArmPose = WhipPoses.KNEEL;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0F, -KneelLayer.DROP, 0.0F);
        KNEEL_PUSHED.add(player.getId());
    }

    private static final java.util.Set<Integer> KNEEL_PUSHED = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (KNEEL_PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    /** Kneeling: no walking, no jumping, no sneaking. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (Kneel.isKneeling(event.getEntity())) {
            Input input = event.getInput();
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ScreenShake.apply(event);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WhipAnims.clear();
        WhipFx.clear();
        ScreenShake.clear();
        Kneel.CLIENT.clear();
    }

    private ClientEvents() {
    }
}
