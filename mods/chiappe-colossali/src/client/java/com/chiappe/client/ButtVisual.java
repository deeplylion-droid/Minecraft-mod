package com.chiappe.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.resources.Identifier;

/** What the layer needs to draw one player's cheeks this frame (filled when the player's render state is). */
public final class ButtVisual {
    public static final RenderStateDataKey<ButtVisual> KEY = RenderStateDataKey.create(() -> "chiappe:cheeks");

    /** Diameter of one cheek, in blocks. */
    public float diameter;
    /** Per cheek (0 right, 1 left): offsets in radii (x outwards, y up, z back) and squash. */
    public final float[] x = new float[2], y = new float[2], z = new float[2], s = new float[2];
    /** The player's skin: the cheeks wear the back of its legs (trousers, mostly). */
    public Identifier skin;
}
