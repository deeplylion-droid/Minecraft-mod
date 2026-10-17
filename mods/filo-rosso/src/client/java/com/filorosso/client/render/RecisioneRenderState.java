package com.filorosso.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class RecisioneRenderState extends LivingEntityRenderState {
    public int action;
    /** Ticks since the current action started (with partial tick). */
    public float actionTime;
    /** 1, 2 or 3. */
    public int phase = 1;
    /** Ticks since the current phase started (the tendrils grow, the eyes open). */
    public float phaseTime;
    /** Ticks since it was last hurt (large when it was not). */
    public float sinceHurt = 100.0F;
    /** Different for every boss, so two of them never twitch together. */
    public int seed;
    /** Chance that a frame skips (more in the later forms, when hurt, when it rings or blinks away). */
    public float glitch;
    /** Its eyes and heart are dark this frame. */
    public boolean glowOff;
}
