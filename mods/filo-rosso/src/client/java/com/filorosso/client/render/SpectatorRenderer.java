package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.entity.Spectator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The spectators of the Theatre: black figures with no face. Sitting they only turn their heads;
 * standing, two white points light up where the eyes should be. At the end they all applaud, eyes open.
 */
public class SpectatorRenderer extends AbstractZombieRenderer<Spectator, SpectatorRenderer.State, SpectatorRenderer.Model> {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/spectator.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/spectator_glow.png"));

    public static class State extends ZombieRenderState {
        public boolean sitting;
        public boolean clapping;
        public boolean awake;
    }

    public static class Model extends ZombieModel<State> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float age = s.ageInTicks;
            if (s.sitting) {
                // sitting up straight, legs forward, hands on the knees
                this.rightLeg.xRot = -1.4137167F;
                this.rightLeg.yRot = 0.10F;
                this.rightLeg.zRot = 0.04F;
                this.leftLeg.xRot = -1.4137167F;
                this.leftLeg.yRot = -0.10F;
                this.leftLeg.zRot = -0.04F;
                this.rightArm.xRot = -0.75F;
                this.rightArm.yRot = 0.0F;
                this.rightArm.zRot = 0.05F;
                this.leftArm.xRot = -0.75F;
                this.leftArm.yRot = 0.0F;
                this.leftArm.zRot = -0.05F;
            }
            if (s.clapping) {
                // slow, even applause, all the same
                float clap = Mth.abs(Mth.sin(age * 0.55F));
                this.rightArm.xRot = -1.25F;
                this.leftArm.xRot = -1.25F;
                this.rightArm.yRot = -0.45F + 0.35F * clap;
                this.leftArm.yRot = 0.45F - 0.35F * clap;
                this.rightArm.zRot = 0.0F;
                this.leftArm.zRot = 0.0F;
            }
        }
    }

    public SpectatorRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.ZOMBIE)), new Model(context.bakeLayer(ModelLayers.ZOMBIE_BABY)),
                ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), Model::new),
                ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, context.getModelSet(), Model::new));
        this.addLayer(new EyesLayer<State, Model>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
                // standing, or applauding at the end: the white points open in the dark
                if (state.awake || state.clapping) {
                    super.submit(poseStack, collector, light, state, yRot, xRot);
                }
            }
        });
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Spectator entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.sitting = !entity.awake();
        state.clapping = entity.clapping();
        state.awake = entity.awake();
        // a sitting one never looks like it wants to attack
        if (state.sitting) {
            state.isAggressive = false;
        }
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURE;
    }
}
