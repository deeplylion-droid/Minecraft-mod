package com.punizioni.client.render;

import com.punizioni.entity.RoastPig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A prop's Blockbench model with its moving bits set from the render state at draw time: the
 * pieces of the roast pig that have been taken are hidden; the gruel's surface sinks as the pot
 * empties and the bits floating in it drift round.
 */
public class PropModel extends EntityModel<PropRenderState> {
    /** How far the slop's surface sinks from full to empty (model pixels). */
    public static final float SLOP_DEPTH = 4.5F;

    private final @Nullable ModelPart[] pieces = new ModelPart[RoastPig.PIECES.length];
    private final @Nullable ModelPart slop;
    private final @Nullable ModelPart floaters;

    public PropModel(BbModel bb) {
        super(bb.root, RenderTypes::entityCutout);
        for (int i = 0; i < this.pieces.length; i++) {
            this.pieces[i] = bb.find(RoastPig.PIECES[i].bone());
        }
        this.slop = bb.find("slop");
        this.floaters = bb.find("floaters");
    }

    @Override
    public void setupAnim(PropRenderState s) {
        super.setupAnim(s);
        for (int i = 0; i < this.pieces.length; i++) {
            ModelPart p = this.pieces[i];
            if (p != null) {
                p.visible = (s.taken & 1 << i) == 0;
            }
        }
        float sink = (1.0F - s.level) * SLOP_DEPTH;
        if (this.slop != null) {
            this.slop.visible = s.level > 0.001F;
            this.slop.y += sink;
            // the surface heaves a little as it boils
            this.slop.y += Mth.sin(s.time * 0.31F) * 0.08F;
        }
        if (this.floaters != null) {
            this.floaters.visible = s.level > 0.001F;
            this.floaters.y += sink + Mth.sin(s.time * 0.23F + 1.0F) * 0.12F;
            this.floaters.yRot += s.time * 0.012F;
        }
    }
}
