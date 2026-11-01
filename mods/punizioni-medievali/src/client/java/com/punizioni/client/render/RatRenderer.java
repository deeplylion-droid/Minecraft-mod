package com.punizioni.client.render;

import com.punizioni.Punizioni;
import com.punizioni.entity.Rat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** The rats of the rat table (geometry/rat.bbmodel): scurrying legs, a whipping tail, gnawing heads, red eyes. */
public class RatRenderer extends MobRenderer<Rat, RatRenderer.State, RatRenderer.Model> {
    private static final Identifier TEXTURE = Punizioni.id("textures/entity/rat.png");
    private static final RenderType GLOW = RenderTypes.eyes(Punizioni.id("textures/entity/rat_glow.png"));

    public static class State extends LivingEntityRenderState {
        public boolean gnawing;
        public int seed;
    }

    public static class Model extends EntityModel<State> {
        private final ModelPart head, jaw, legFl, legFr, legBl, legBr;
        private final ModelPart[] tail = new ModelPart[4];

        public Model(BbModel bb) {
            super(bb.root, RenderTypes::entityCutout);
            this.head = bb.find("head");
            this.jaw = bb.find("jaw");
            this.legFl = bb.find("leg_fl");
            this.legFr = bb.find("leg_fr");
            this.legBl = bb.find("leg_bl");
            this.legBr = bb.find("leg_br");
            for (int i = 0; i < 4; i++) {
                this.tail[i] = bb.find("tail" + (i + 1));
            }
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float t = s.ageInTicks + s.seed;
            float run = Math.min(1.0F, s.walkAnimationSpeed * 2.0F);
            float swing = Mth.sin(s.walkAnimationPos * 1.6F) * 1.1F * run;
            rotX(this.legFl, swing);
            rotX(this.legBr, swing);
            rotX(this.legFr, -swing);
            rotX(this.legBl, -swing);
            for (int i = 0; i < 4; i++) {
                if (this.tail[i] != null) {
                    this.tail[i].yRot += Mth.sin(t * 0.35F - i * 0.8F) * (0.25F + 0.3F * run);
                    this.tail[i].xRot += 0.08F * i;
                }
            }
            if (s.gnawing) {
                // gnawing: the head jerks down and pulls, the jaw chatters
                float g = Mth.sin(t * 1.3F);
                rotX(this.head, 0.35F + g * 0.25F);
                if (this.head != null) {
                    this.head.yRot += Mth.sin(t * 0.7F) * 0.3F;
                }
                rotX(this.jaw, 0.15F + Math.abs(Mth.sin(t * 2.4F)) * 0.45F);
            } else if (this.head != null) {
                this.head.yRot += Mth.sin(t * 0.21F) * 0.2F;
            }
        }

        private static void rotX(ModelPart p, float a) {
            if (p != null) {
                p.xRot += a;
            }
        }
    }

    public RatRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(BbModel.load(context.getResourceManager(), Punizioni.id("geometry/rat.bbmodel"))), 0.18F);
        this.addLayer(new EyesLayer<State, Model>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Rat rat, State s, float partial) {
        super.extractRenderState(rat, s, partial);
        s.gnawing = rat.gnawing();
        s.seed = rat.getId() * 13;
    }

    @Override
    public Identifier getTextureLocation(State s) {
        return TEXTURE;
    }
}
