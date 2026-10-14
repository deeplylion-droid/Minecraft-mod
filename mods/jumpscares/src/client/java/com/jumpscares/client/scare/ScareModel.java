package com.jumpscares.client.scare;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** One baked monster: its parts by name, its textures and the size of one model unit in blocks. */
public final class ScareModel {
    public final ModelPart root;
    private final Map<String, ModelPart> parts;
    public final ResourceLocation texture;
    public final ResourceLocation glow;
    public final float unit;

    ScareModel(ModelPart root, Map<String, ModelPart> parts, ResourceLocation texture, ResourceLocation glow, float unit) {
        this.root = root;
        this.parts = parts;
        this.texture = texture;
        this.glow = glow;
        this.unit = unit;
    }

    /** The part, or null if this model has none with that name. */
    public ModelPart part(String name) {
        return this.parts.get(name);
    }

    public void reset() {
        this.root.getAllParts().forEach(ModelPart::resetPose);
    }
}
