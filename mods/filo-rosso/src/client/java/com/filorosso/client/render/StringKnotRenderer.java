package com.filorosso.client.render;

import com.filorosso.entity.Burattinaio;
import com.filorosso.entity.StringKnot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Burattinaio's strings on a puppet: a thick red thread from its control down to a knot over the
 * puppet's head, and from the knot five thin strings to the head, the hands and the feet, quivering.
 * The knot glows: hit it to cut them.
 */
public class StringKnotRenderer extends EntityRenderer<StringKnot, StringKnotRenderer.State> {
    public static class State extends EntityRenderState {
        public Vec3 knot = Vec3.ZERO;
        public Vec3 control;
        public final List<Vec3> limbs = new ArrayList<>();
        public int hits;
        public float time;
        public int seed;
    }

    public StringKnotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StringKnot entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.knot = entity.getPosition(partial);
        state.hits = entity.hits();
        state.time = entity.tickCount + partial;
        state.seed = entity.getId();
        state.control = null;
        state.limbs.clear();
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity boss = level.getEntity(entity.puppeteerId());
        if (boss instanceof Burattinaio b) {
            Vec3 pos = b.getPosition(partial);
            float yaw = Mth.rotLerp(partial, b.yBodyRotO, b.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
            state.control = pos.add(fwd.scale(2.6)).add(right.scale(-1.6)).add(0.0, 8.8, 0.0);
        }
        Entity puppet = level.getEntity(entity.puppetId());
        if (puppet instanceof LivingEntity p) {
            Vec3 pos = p.getPosition(partial);
            float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
            double h = p.getBbHeight();
            state.limbs.add(pos.add(0.0, h + 0.02, 0.0));
            state.limbs.add(pos.add(right.scale(0.42)).add(fwd.scale(0.1)).add(0.0, h * 0.42, 0.0));
            state.limbs.add(pos.add(right.scale(-0.42)).add(fwd.scale(0.1)).add(0.0, h * 0.42, 0.0));
            state.limbs.add(pos.add(right.scale(0.15)).add(0.0, 0.1, 0.0));
            state.limbs.add(pos.add(right.scale(-0.15)).add(0.0, 0.1, 0.0));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vec3 origin = new Vec3(state.x, state.y, state.z);
        Vec3 cam = camera.pos;
        Vec3 knot = state.knot;
        float t = state.time;
        int light = LightCoordsUtil.FULL_BRIGHT;
        if (state.control != null) {
            List<Vec3> main = Ribbons.line(state.control, knot, 24, 0.25, 0.04, t, state.seed);
            Ribbons.thread(collector, poseStack, origin, cam, main, 0.08F, 0, light);
            Ribbons.glow(collector, poseStack, origin, cam, main, 0.18F, 0xD8202A, 0.45F);
        }
        for (int i = 0; i < state.limbs.size(); i++) {
            List<Vec3> s = Ribbons.line(knot, state.limbs.get(i), 10, 0.02, 0.025, t, state.seed + i * 7);
            Ribbons.thread(collector, poseStack, origin, cam, s, 0.035F, 0, light);
            Ribbons.glow(collector, poseStack, origin, cam, s, 0.09F, 0xD8202A, 0.35F);
        }
        // the knot: a few tight loops, fraying more with every hit
        float fray = 1.0F + state.hits * 0.35F;
        for (int k = 0; k < 4; k++) {
            List<Vec3> loop = new ArrayList<>();
            double tilt = k * 0.8 + t * 0.05;
            for (int i = 0; i <= 14; i++) {
                double a = i * Math.PI * 2.0 / 14;
                double r = 0.16 * fray + 0.03 * Math.sin(a * 3 + k);
                Vec3 p = new Vec3(Math.cos(a) * r, Math.sin(a) * r * Math.cos(tilt), Math.sin(a) * r * Math.sin(tilt));
                double cy = Math.cos(k * 1.3), sy = Math.sin(k * 1.3);
                p = new Vec3(p.x * cy - p.z * sy, p.y, p.x * sy + p.z * cy);
                loop.add(knot.add(p));
            }
            Ribbons.thread(collector, poseStack, origin, cam, loop, 0.06F, 0, light);
        }
        float pulse = 0.6F + 0.4F * Mth.sin(t * 0.5F);
        Ribbons.glow(collector, poseStack, origin, cam, Ribbons.circle(knot, 0.28 * fray, 16, t * 0.2), 0.3F, 0xFF3040, 0.5F * pulse);
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(StringKnot entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(12.0, 12.0, 12.0);
    }
}
