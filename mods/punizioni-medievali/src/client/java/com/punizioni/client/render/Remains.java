package com.punizioni.client.render;

import com.punizioni.Punizioni;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * What stays in a machine after the victim has gone: a body with the victim's own skin (hanged on the
 * gallows, tied to the wheel), the head in the guillotine's basket, the bones on the stake (charred)
 * and on the rat table (gnawed).
 */
public final class Remains {
    public static final Identifier CHARRED = Punizioni.id("textures/entity/skeleton_charred.png");
    public static final Identifier GNAWED = Punizioni.id("textures/entity/skeleton_gnawed.png");

    /** How a dead body hangs. */
    public static final int HANGED = 0;
    public static final int SPREAD = 1;
    public static final int LYING = 2;
    public static final int STAKE = 3;

    public static final class BodyState {
        public int pose;
        public float time;
        /** Arms and legs broken on the wheel (bent the wrong way). */
        public boolean broken;
    }

    /** A dead player's body (vanilla player geometry), posed for the machine. */
    public static final class Corpse extends Model<BodyState> {
        private final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;

        Corpse(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.head = root.getChild("head");
            this.body = root.getChild("body");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        @Override
        public void setupAnim(BodyState s) {
            super.setupAnim(s);
            switch (s.pose) {
                case HANGED -> {
                    this.head.xRot = 0.45F;
                    this.head.zRot = 0.55F;
                    this.rightArm.xRot = 0.32F;
                    this.rightArm.zRot = 0.18F;
                    this.leftArm.xRot = 0.32F;
                    this.leftArm.zRot = -0.18F;
                    this.rightLeg.xRot = 0.05F;
                    this.leftLeg.xRot = -0.08F;
                    this.rightLeg.zRot = 0.03F;
                }
                case SPREAD -> {
                    this.head.xRot = 0.6F;
                    this.head.zRot = 0.3F;
                    this.rightArm.zRot = 2.3F;
                    this.leftArm.zRot = -2.3F;
                    this.rightLeg.zRot = 0.38F;
                    this.leftLeg.zRot = -0.38F;
                    if (s.broken) {
                        this.rightArm.xRot = -0.5F;
                        this.leftLeg.xRot = 0.6F;
                    }
                }
                default -> {
                }
            }
        }
    }

    /** A head on its own: the head and hat of the player geometry. */
    public static final class Head extends Model<BodyState> {
        Head(ModelPart root) {
            super(root.getChild("head"), RenderTypes::entityTranslucent);
        }

        @Override
        public void setupAnim(BodyState s) {
            super.setupAnim(s);
        }
    }

    /** The bones (geometry/skeleton.bbmodel), on the stake or on the table. */
    public static final class Bones extends Model<BodyState> {
        private final ModelPart skull, armLeft, armRight, legLeft, legRight, jaw;

        Bones(BbModel bb) {
            super(bb.root, RenderTypes::entityCutout);
            this.skull = bb.find("skull");
            this.jaw = bb.find("jaw");
            this.armLeft = bb.find("arm_left");
            this.armRight = bb.find("arm_right");
            this.legLeft = bb.find("leg_left");
            this.legRight = bb.find("leg_right");
        }

        @Override
        public void setupAnim(BodyState s) {
            super.setupAnim(s);
            if (s.pose == STAKE) {
                // tied with the hands behind the post, the skull dropped forward
                rot(this.armLeft, 0.5F, 0.0F, -0.25F);
                rot(this.armRight, 0.5F, 0.0F, 0.25F);
                rot(this.skull, 0.5F, 0.0F, 0.15F);
            } else {
                rot(this.armLeft, 0.0F, 0.0F, -0.2F);
                rot(this.armRight, 0.0F, 0.0F, 0.15F);
                rot(this.legLeft, 0.0F, 0.0F, -0.12F);
                rot(this.legRight, 0.0F, 0.0F, 0.1F);
                rot(this.skull, 0.0F, 0.4F, 0.0F);
            }
            if (this.jaw != null) {
                this.jaw.xRot += 0.35F + 0.05F * Mth.sin(s.time * 0.05F);
            }
        }

        private static void rot(@Nullable ModelPart p, float x, float y, float z) {
            if (p != null) {
                p.xRot += x;
                p.yRot += y;
                p.zRot += z;
            }
        }
    }

    public final Corpse wide;
    public final Corpse slim;
    public final Head head;
    public final Bones bones;
    public final BodyState state = new BodyState();

    public Remains(EntityRendererProvider.Context context) {
        this.wide = new Corpse(context.bakeLayer(ModelLayers.PLAYER));
        this.slim = new Corpse(context.bakeLayer(ModelLayers.PLAYER_SLIM));
        this.head = new Head(context.bakeLayer(ModelLayers.PLAYER));
        this.bones = new Bones(BbModel.load(context.getResourceManager(), Punizioni.id("geometry/skeleton.bbmodel")));
    }

    public Corpse corpse(PlayerSkin skin) {
        return skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
    }

    /** The skin of a player by UUID: theirs if they are on the server, a default one otherwise. */
    public static PlayerSkin skin(@Nullable UUID id) {
        if (id == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(id) : null;
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }
}
