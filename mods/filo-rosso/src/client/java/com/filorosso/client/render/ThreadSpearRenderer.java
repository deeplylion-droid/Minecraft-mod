package com.filorosso.client.render;

import com.filorosso.entity.ThreadSpear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A thread falling from the dark: a red circle on the boards that tightens, a faint line up into the
 * dark, then the thread comes down like a spear and stays stuck in the boards for a moment.
 */
public class ThreadSpearRenderer extends EntityRenderer<ThreadSpear, ThreadSpearRenderer.State> {
    public static class State extends EntityRenderState {
        public double ground;
        public double height;
        public float age;
    }

    public ThreadSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ThreadSpear entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.ground = entity.ground();
        state.height = entity.height(partial);
        state.age = entity.tickCount + partial;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vec3 origin = new Vec3(state.x, state.y, state.z);
        Vec3 cam = camera.pos;
        Vec3 base = new Vec3(state.x, state.ground + 0.06, state.z);
        float age = state.age;
        int light = LightCoordsUtil.FULL_BRIGHT;
        if (age < ThreadSpear.WARN + ThreadSpear.FALL_TICKS) {
            // the circle where it will land, tightening
            float k = Mth.clamp(age / ThreadSpear.WARN, 0.0F, 1.0F);
            double r = ThreadSpear.RADIUS * (1.35 - 0.35 * k);
            float pulse = 0.6F + 0.4F * Mth.sin(age * 1.2F);
            Ribbons.glow(collector, poseStack, origin, cam, Ribbons.circle(base, r, 24, age * 0.1), 0.12F, 0xFF2018, 0.9F * pulse);
            Ribbons.glow(collector, poseStack, origin, cam, Ribbons.circle(base, r * 0.45, 16, -age * 0.15), 0.08F, 0xFF2018, 0.5F * k);
            // and, faintly, the thread waiting up in the dark
            Vec3 top = base.add(0.0, ThreadSpear.FALL + 6.0, 0.0);
            Vec3 tip = base.add(0.0, state.height, 0.0);
            Ribbons.glow(collector, poseStack, origin, cam, List.of(tip, top), 0.05F, 0xD8202A, 0.25F + 0.5F * k);
            if (age >= ThreadSpear.WARN) {
                // falling: a bright spear
                Vec3 tail = tip.add(0.0, 7.0, 0.0);
                Ribbons.thread(collector, poseStack, origin, cam, List.of(tip, tail), 0.09F, 0, light);
                Ribbons.glow(collector, poseStack, origin, cam, List.of(tip, tail), 0.3F, 0xFF3020, 0.9F);
            }
        } else {
            // stuck in the boards, then gone
            float k = 1.0F - Mth.clamp((age - ThreadSpear.WARN - ThreadSpear.FALL_TICKS) / 30.0F, 0.0F, 1.0F);
            Vec3 tail = base.add(0.0, 5.0 * k + 0.3, 0.0);
            Ribbons.thread(collector, poseStack, origin, cam, List.of(base, tail), 0.08F, 0, light);
            Ribbons.glow(collector, poseStack, origin, cam, List.of(base, tail), 0.22F, 0xD8202A, 0.6F * k);
        }
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(ThreadSpear entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(2.0, 0.0, 2.0).expandTowards(0.0, 34.0, 0.0);
    }
}
