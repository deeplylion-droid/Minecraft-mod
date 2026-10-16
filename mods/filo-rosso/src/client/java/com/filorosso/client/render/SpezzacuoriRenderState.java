package com.filorosso.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class SpezzacuoriRenderState extends LivingEntityRenderState {
    public int action;
    /** Ticks since the current action started (with partial tick). */
    public float actionTime;
    public boolean raging;
}
