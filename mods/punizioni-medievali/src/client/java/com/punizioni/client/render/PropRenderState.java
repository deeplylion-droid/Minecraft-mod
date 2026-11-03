package com.punizioni.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** What a prop (the roast pig, the pot of gruel) needs to be drawn, copied from it each frame. */
public class PropRenderState extends EntityRenderState {
    public float yaw;
    /** The roast pig: the pieces already taken (one bit each). */
    public int taken;
    /** The pot: how full it is (0..1). */
    public float level;
    /** Game time with the partial tick. */
    public float time;
}
