package com.frusta.client.render;

import com.frusta.FrustaMod;
import com.frusta.client.WhipAnims;
import com.frusta.client.WhipPoses;
import com.frusta.config.FrustaConfig;
import com.frusta.item.WhipItem;
import com.frusta.motion.V3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The whip in first person. The handle is drawn where vanilla draws a held item, at the bottom right
 * (in the hand's own view, which has a fixed field of view), and swings about the fist with the moves,
 * following the arm of the player model. The lash, drawn in the world, leaves the top of the handle:
 * that point is carried over from the hand's view to the world's, whose field of view can differ.
 */
public final class FirstPersonWhip {
    /** Top centre of the handle model (where the lash leaves it) and the bottom of the grip, in blocks. */
    private static final Vector3f TIP = new Vector3f(8.0F / 16.0F, 11.0F / 16.0F, 8.0F / 16.0F);
    private static final Vector3f BUTT = new Vector3f(8.0F / 16.0F, 1.0F / 16.0F, 8.0F / 16.0F);
    /** How much of the arm's turn the handle follows, and how far the fist follows the hand. */
    private static final float TURN = 0.75F;
    private static final float HAND_SHIFT = 0.3F;
    /** The handle always points at least this much away from the camera (z of its axis in view space). */
    private static final float MAX_TOWARDS_CAMERA = -0.2F;

    private static double worldFov = 70.0;
    private static double handFov = 70.0;
    /** Vanilla's equip animation of the main hand, as of the last hand frame (used if it cannot be read). */
    private static float equip;
    /** {@code ItemInHandRenderer.mainHandHeight} and {@code oMainHandHeight}, read to know the equip animation now. */
    @Nullable
    private static Field mainHandHeight;
    @Nullable
    private static Field oMainHandHeight;
    private static boolean unreadable;

    private FirstPersonWhip() {
    }

    /** True when the local player's whip is shown in first person (handle in the hand, lash from it). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null && mc.options.getCameraType().isFirstPerson()
                && mc.getCameraEntity() == player && !player.isSpectator() && !WhipItem.held(player).isEmpty();
    }

    /** Remembers the fields of view of the world and of the hand (they can differ: settings, sprinting). */
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            worldFov = event.getFOV();
        } else {
            handFov = event.getFOV();
        }
    }

    /** The hand pass: draws the handle instead of vanilla's held item. Returns true if it did. */
    public static boolean renderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getItemStack().getItem() instanceof WhipItem)
                || !active()) {
            return false;
        }
        equip = event.getEquipProgress();
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        handTransform(poseStack, player, event.getItemStack(), event.getPartialTick(), equipProgress(player, event.getPartialTick()));
        mc.getItemRenderer().renderStatic(player, event.getItemStack(), context, left, poseStack, event.getMultiBufferSource(),
                player.level(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId() + context.ordinal());
        poseStack.popPose();
        return true;
    }

    /**
     * The world pass, before the whips are drawn: where the lash leaves the handle this frame. With the
     * interface hidden (F1) vanilla draws no hand, and the lash comes from the hand of the player model.
     */
    public static void anchor(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active() || mc.options.hideGui) {
            return;
        }
        ItemStack whip = WhipItem.held(player);
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        PoseStack poseStack = new PoseStack();
        // vanilla's lag of the hand behind the camera (ItemInHandRenderer.renderHandsWithItems)
        float xBob = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float yBob = Mth.lerp(partialTick, player.yBobO, player.yBob);
        poseStack.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTick) - xBob) * 0.1F));
        poseStack.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTick) - yBob) * 0.1F));
        handTransform(poseStack, player, whip, partialTick, equipProgress(player, partialTick));
        itemTransform(poseStack, player, whip, left);
        Matrix4f m = poseStack.last().pose();
        Vector3f tip = m.transformPosition(new Vector3f(TIP));
        Vector3f dir = m.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
        // the same place on screen, seen with the world's field of view
        float k = (float) (Math.tan(Math.toRadians(worldFov) * 0.5) / Math.tan(Math.toRadians(handFov) * 0.5));
        tip.mul(k, k, 1.0F);
        dir.mul(k, k, 1.0F);
        WhipAnims.anchor(player, ViewSpace.toWorld(tip), ViewSpace.dirToWorld(dir).normalize(new V3(0, -1, 0)));
    }

    /**
     * How far down vanilla's equip animation has the hand this frame (0 = all the way up), the same for
     * the hand pass and for the world pass that comes before it. During the moves it is 0: pressing use
     * makes vanilla dip the held item ({@code ItemInHandRenderer.itemUsed}), but the moves raise the arm
     * themselves, and a dip would only pull the handle away from the whip.
     */
    private static float equipProgress(LocalPlayer player, float partialTick) {
        if (WhipPoses.isMoving(player)) {
            return 0.0F;
        }
        if (!unreadable) {
            try {
                if (mainHandHeight == null || oMainHandHeight == null) {
                    mainHandHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109302_");
                    oMainHandHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109303_");
                }
                ItemInHandRenderer renderer = Minecraft.getInstance().gameRenderer.itemInHandRenderer;
                return 1.0F - Mth.lerp(partialTick, oMainHandHeight.getFloat(renderer), mainHandHeight.getFloat(renderer));
            } catch (Exception e) {
                unreadable = true;
                FrustaMod.LOGGER.warn("Cannot read the first-person hand height, using the last frame's", e);
            }
        }
        return equip;
    }

    /** Vanilla's place for a held item, then the whip moves: the handle turns about the fist like the arm. */
    private static void handTransform(PoseStack poseStack, LocalPlayer player, ItemStack whip, float partialTick, float equipProgress) {
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        float side = left ? -1.0F : 1.0F;
        poseStack.translate(side * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
        if (!FrustaConfig.CLIENT.firstPerson.get() || !WhipPoses.isMoving(player)) {
            return;
        }
        float pitch = player.getViewXRot(partialTick);
        float[] rest = WhipPoses.firstPersonRest(pitch);
        float[] now = WhipPoses.angles(player, partialTick, rest, true);
        Vector3f from = armDirection(rest, pitch);
        Vector3f to = armDirection(now, pitch);
        // the handle as the right hand holds it: it turns about its bottom end (the corner of the screen,
        // so the end never swings at the camera), and which way it points
        PoseStack grip = new PoseStack();
        itemTransform(grip, player, whip, false);
        Vector3f pivot = grip.last().pose().transformPosition(new Vector3f(BUTT));
        Vector3f axis = grip.last().pose().transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        // It turns like the arm, but less, and never so far that it points back at the camera.
        Quaternionf full = new Quaternionf().rotationTo(from, to);
        Quaternionf turn = new Quaternionf();
        float amount = TURN;
        for (int i = 0; i < 12; i++) {
            turn.identity().slerp(full, amount);
            if (turn.transform(new Vector3f(axis)).z <= MAX_TOWARDS_CAMERA) {
                break;
            }
            amount *= 0.8F;
        }
        // the fist follows the hand of the arm, but never comes towards the camera
        Vector3f shift = new Vector3f(to).sub(from).mul(HAND_SHIFT);
        shift.z = Math.min(shift.z, 0.0F);
        if (left) {
            turn.y = -turn.y;
            turn.z = -turn.z;
            shift.x = -shift.x;
            pivot.x = -pivot.x;
        }
        poseStack.translate(pivot.x + shift.x, pivot.y + shift.y, pivot.z + shift.z);
        poseStack.mulPose(turn);
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** The model's own first-person transform, as {@code ItemRenderer} applies it. */
    private static void itemTransform(PoseStack poseStack, LocalPlayer player, ItemStack whip, boolean left) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(whip, player.level(), player, player.getId());
        ForgeHooksClient.handleCameraTransforms(poseStack, model,
                left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, left);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * Where the right arm of the player model points for these angles, in view space (the body turned
     * where the camera looks, the camera {@code pitch} degrees down).
     */
    private static Vector3f armDirection(float[] angles, float pitch) {
        Vector3f d = new Quaternionf().rotationZYX(angles[2], angles[1], angles[0]).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        // model space is upside down and mirrored (the renderer scales it by -1, -1, 1)
        d.set(-d.x, -d.y, d.z);
        return new Quaternionf().rotationX((float) Math.toRadians(pitch)).transform(d);
    }
}
