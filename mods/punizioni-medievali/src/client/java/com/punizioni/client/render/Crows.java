package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.punizioni.Punizioni;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The crows of the wheel and of the gallows: drawn by the machine (they are not mobs). They circle
 * overhead, glide down one after the other and perch on the beam and on the body, pecking.
 */
public final class Crows {
    private static final Identifier TEXTURE = Punizioni.id("textures/entity/crow.png");

    public static final class State extends EntityRenderState {
        public float flap;
        public float peck;
        public boolean flying;
    }

    public static final class Model extends EntityModel<State> {
        private final ModelPart head, wingLeft, wingRight, tail;

        Model(BbModel bb) {
            super(bb.root, RenderTypes::entityCutout);
            this.head = bb.find("head");
            this.wingLeft = bb.find("wing_left");
            this.wingRight = bb.find("wing_right");
            this.tail = bb.find("tail");
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float wing = s.flying ? Mth.sin(s.flap) * 1.1F : 0.08F * Mth.sin(s.flap * 0.3F);
            if (this.wingLeft != null) {
                this.wingLeft.zRot -= wing + (s.flying ? 0.2F : 0.0F);
            }
            if (this.wingRight != null) {
                this.wingRight.zRot += wing + (s.flying ? 0.2F : 0.0F);
            }
            if (this.head != null) {
                this.head.xRot += s.peck * 0.9F;
            }
            if (this.tail != null) {
                this.tail.xRot += s.flying ? -0.2F : Mth.sin(s.flap * 0.5F) * 0.1F;
            }
        }
    }

    private final Model model;
    private final State[] states = new State[8];

    public Crows(ResourceManager manager) {
        this.model = new Model(BbModel.load(manager, Punizioni.id("geometry/crow.bbmodel")));
        for (int i = 0; i < this.states.length; i++) {
            this.states[i] = new State();
        }
    }

    /**
     * Crows over a machine. {@code since}: ticks since they started coming; {@code perches}: where
     * they land, in the pose stack's frame (blocks, relative to the machine); {@code centre}: what they
     * circle around before landing.
     */
    public void submit(SubmitNodeCollector collector, PoseStack poseStack, float since, float time, Vec3[] perches, Vec3 centre, int light) {
        if (since <= 0.0F) {
            return;
        }
        for (int i = 0; i < Math.min(perches.length, this.states.length); i++) {
            float start = i * 35.0F;
            float t = since - start;
            if (t <= 0.0F) {
                continue;
            }
            State s = this.states[i];
            float circle = 90.0F + i * 20.0F;
            Vec3 at;
            float yaw;
            if (t < circle) {
                // circling, coming lower and closer
                float k = t / circle;
                float a = time * 0.06F + i * 1.7F;
                double r = Mth.lerp(k, 7.0, 2.5);
                at = centre.add(Math.cos(a) * r, Mth.lerp(k, 6.0, 2.0) + Math.sin(time * 0.1F + i) * 0.3, Math.sin(a) * r);
                yaw = -a * Mth.RAD_TO_DEG;
                s.flying = true;
            } else if (t < circle + 20.0F) {
                // gliding down onto the perch
                float k = (t - circle) / 20.0F;
                float a = (circle) * 0.06F + i * 1.7F;
                Vec3 from = centre.add(Math.cos(a) * 2.5, 2.0, Math.sin(a) * 2.5);
                at = from.lerp(perches[i], k * k);
                Vec3 d = perches[i].subtract(from);
                yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
                s.flying = true;
            } else {
                // perched: hopping a little, pecking
                at = perches[i];
                yaw = (i * 77.0F + Mth.sin(time * 0.02F + i) * 40.0F);
                s.flying = (int) (time + i * 13) % 140 < 6;
            }
            s.flap = time * (s.flying ? 1.4F : 0.4F) + i;
            float pk = (time * 0.35F + i * 2.1F) % 6.0F;
            s.peck = !s.flying && pk < 1.0F ? Mth.sin(pk * Mth.PI) : 0.0F;
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            poseStack.rotateDegrees(Axis.YP, 180.0F - yaw);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            collector.submitModel(this.model, s, poseStack, this.model.renderType(TEXTURE), light, OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
            poseStack.popPose();
        }
    }
}
