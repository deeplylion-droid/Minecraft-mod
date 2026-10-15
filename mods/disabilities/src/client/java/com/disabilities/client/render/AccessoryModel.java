package com.disabilities.client.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One accessory (glasses, wheelchair, crutches...) loaded from JSON. It is posed when it is drawn,
 * from the player's render state, so every player's wheels spin on their own.
 */
public final class AccessoryModel extends Model<AvatarRenderState> {
    public final Identifier texture;
    private final Map<String, ModelPart> parts;
    private BiConsumer<AccessoryModel, AvatarRenderState> animator = (m, s) -> {
    };

    AccessoryModel(ModelPart root, Map<String, ModelPart> parts, Identifier texture) {
        super(root, RenderTypes::entityTranslucent);
        this.parts = parts;
        this.texture = texture;
    }

    public ModelPart part(String name) {
        return this.parts.get(name);
    }

    public AccessoryModel animate(BiConsumer<AccessoryModel, AvatarRenderState> animator) {
        this.animator = animator;
        return this;
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        this.animator.accept(this, state);
    }
}
