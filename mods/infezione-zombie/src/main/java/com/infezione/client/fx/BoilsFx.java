package com.infezione.client.fx;

import com.infezione.InfezioneMod;
import com.infezione.client.ClientInfections;
import com.infezione.client.render.Boils;
import com.infezione.client.render.BoilsLayer;
import com.infezione.config.InfezioneConfig;
import com.infezione.infection.Stages;
import com.infezione.registry.ModParticles;
import com.infezione.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderArmEvent;
import org.joml.Vector3f;

/**
 * When a boil bursts: pus and blood spray out of it, with a wet pop. And the boils of the infected
 * player's own arm, in first person.
 */
public final class BoilsFx {
    private static final RandomSource RANDOM = RandomSource.create();
    /** The latest burst seen of every boil of every player. */
    private static final Map<UUID, long[]> SEEN = new HashMap<>();
    private static final Boils.Shape SHAPE = new Boils.Shape();
    private static final Vector3f POINT = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private BoilsFx() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (Player p : mc.level.players()) {
            AbstractClientPlayer player = (AbstractClientPlayer) p;
            int stage = ClientInfections.stage(player);
            if (stage < 4 || player.isInvisible() || !InfezioneConfig.CLIENT.boils.get()) {
                SEEN.remove(player.getUUID());
                continue;
            }
            long seed = BoilsLayer.seed(player);
            long[] seen = SEEN.computeIfAbsent(player.getUUID(), k -> new long[Boils.SPOTS.length]);
            boolean close = player.distanceToSqr(mc.player) < 24 * 24;
            for (int i = 0; i < Boils.SPOTS.length; i++) {
                long cycle = Boils.burstCycle(i, seed, stage, player.tickCount);
                if (cycle >= 0 && seen[i] != 0 && cycle != seen[i] && close) {
                    burst(mc, player, i, seed, stage);
                }
                seen[i] = cycle;
            }
        }
        Iterator<UUID> it = SEEN.keySet().iterator();
        while (it.hasNext()) {
            if (mc.level.getPlayerByUUID(it.next()) == null) {
                it.remove();
            }
        }
    }

    public static void clear() {
        SEEN.clear();
    }

    /** Pus and blood out of the boil (about where it is: the body standing), and the pop. */
    private static void burst(Minecraft mc, AbstractClientPlayer player, int index, long seed, int stage) {
        Boils.Spot spot = Boils.SPOTS[index];
        Boils.shape(index, seed, stage, player.tickCount - 1, SHAPE);
        boolean slim = "slim".equals(player.getModelName());
        Boils.anchor(spot, SHAPE.a, SHAPE.b, slim, POINT, NORMAL);
        Vector3f pivot = switch (spot.part()) {
            case RIGHT_ARM -> new Vector3f(-5, 2, 0);
            case LEFT_ARM -> new Vector3f(5, 2, 0);
            case RIGHT_LEG -> new Vector3f(-1.9F, 12, 0);
            case LEFT_LEG -> new Vector3f(1.9F, 12, 0);
            default -> new Vector3f();
        };
        Vec3 at = toWorld(player, new Vector3f(POINT).add(pivot).add(new Vector3f(NORMAL).mul(1.2F)));
        Vec3 out = toWorld(player, new Vector3f(POINT).add(pivot).add(new Vector3f(NORMAL).mul(3.0F))).subtract(at).normalize();
        boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
        if (!firstPerson) {
            for (int k = 0; k < 7; k++) {
                player.level().addParticle(k < 5 ? ModParticles.PUS.get() : ModParticles.BLOOD.get(), at.x, at.y, at.z,
                        out.x * 0.12 + (RANDOM.nextDouble() - 0.5) * 0.08, out.y * 0.12 + RANDOM.nextDouble() * 0.06,
                        out.z * 0.12 + (RANDOM.nextDouble() - 0.5) * 0.08);
            }
        }
        InfezioneMod.LOGGER.debug("A boil of {} bursts ({})", player.getGameProfile().getName(), index);
        player.level().playLocalSound(at.x, at.y, at.z, ModSounds.BOIL_POP.get(), SoundSource.PLAYERS, 0.5F,
                0.85F + RANDOM.nextFloat() * 0.3F, false);
    }

    /** A point of the player model (pixels, the body standing) in the world. */
    private static Vec3 toWorld(AbstractClientPlayer player, Vector3f model) {
        float scale = 0.9375F;
        float x = -model.x / 16.0F * scale;
        float y = -(model.y / 16.0F - 1.501F) * scale;
        float z = model.z / 16.0F * scale;
        float angle = (180.0F - player.yBodyRot) * Mth.DEG_TO_RAD;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        return player.position().add(x * cos + z * sin, y, -x * sin + z * cos);
    }

    /**
     * First person: the boils of the arm drawn on the screen, where vanilla is about to draw the arm
     * ({@code PlayerRenderer.renderHand}: the arm at its pivot, no swing, a little tilt from the idle bob).
     */
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        int stage = ClientInfections.stage(player);
        if (stage < 3 || stage > Stages.ZOMBIE || !InfezioneConfig.CLIENT.boils.get()) {
            return;
        }
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        Boils.Part part = right ? Boils.Part.RIGHT_ARM : Boils.Part.LEFT_ARM;
        boolean slim = "slim".equals(player.getModelName());
        long seed = BoilsLayer.seed(player);
        float age = player.tickCount + Minecraft.getInstance().getFrameTime();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(Boils.TEXTURE));
        poseStack.pushPose();
        poseStack.translate((right ? -5.0F : 5.0F) / 16.0F, 2.0F / 16.0F, 0.0F);
        poseStack.mulPose(new org.joml.Quaternionf().rotationZ(right ? 0.1F : -0.1F));
        for (int i = 0; i < Boils.SPOTS.length; i++) {
            Boils.Spot spot = Boils.SPOTS[i];
            if (spot.part() != part) {
                continue;
            }
            Boils.shape(i, seed, stage, age, SHAPE);
            if (!SHAPE.shown) {
                continue;
            }
            Boils.anchor(spot, SHAPE.a, SHAPE.b, slim, POINT, NORMAL);
            Boils.draw(poseStack, buffer, POINT, NORMAL, SHAPE, seed, i, age, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }
}
