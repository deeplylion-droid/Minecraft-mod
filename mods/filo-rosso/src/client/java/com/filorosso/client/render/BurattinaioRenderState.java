package com.filorosso.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class BurattinaioRenderState extends LivingEntityRenderState {
    public int action;
    /** Ticks since the current action started (with partial tick). */
    public float actionTime;
    /** 1, 2 or 3. */
    public int phase = 1;
    public float phaseTime;
    /** Ticks since it was last hurt (large when it was not). */
    public float sinceHurt = 100.0F;
    public int seed;
    /** Chance that a frame skips. */
    public float glitch;
    /** Its eyes are dark this frame. */
    public boolean glowOff;
}
