package com.punizioni.client.render;

import com.punizioni.machine.MachineKind;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** What a machine needs to be drawn, copied from it each frame. */
public class MachineRenderState extends EntityRenderState {
    public MachineKind kind = MachineKind.WHIPPING_POST;
    public float yaw;
    public int state;
    /** Ticks since the state began, with the partial tick. */
    public float age;
    /** Machine-specific numbers: doors, stretch, fire, blade, hub, spin, drop... */
    public float a;
    public float b;
    public float c;
    public float d;
    public int choice;
    public boolean hasVictim;
    /** The skin of whoever's remains are in the machine (the head in the basket, the hanged body). */
    public @Nullable PlayerSkin remains;
    public java.util.@Nullable UUID remainsId;
    /** Game time with the partial tick, for things that never stop moving (flames, crows, swaying). */
    public float time;
    public double x;
    public double y;
    public double z;
}
