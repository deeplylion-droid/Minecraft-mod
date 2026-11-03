package com.punizioni.client.render;

import com.punizioni.machine.Guillotine;
import com.punizioni.machine.Wheel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * A machine's Blockbench model (geometry/&lt;kind&gt;.bbmodel), with its moving parts set from the
 * render state: doors, blade, lunette, wheel, hatch, trapdoor, cage door. Blockbench bones by name;
 * a missing bone is simply left alone (the files can be edited).
 */
public class MachineModel extends EntityModel<MachineRenderState> {
    private final ModelPart doorRight, doorLeft, cageDoor, blade, lunetteTop, wheel, hatch, trapLeft, trapRight, lever, chainLeft, chainRight;

    public MachineModel(BbModel bb) {
        super(bb.root, RenderTypes::entityCutout);
        this.doorRight = bb.find("door_right");
        this.doorLeft = bb.find("door_left");
        this.cageDoor = bb.find("cage_door");
        this.blade = bb.find("blade");
        this.lunetteTop = bb.find("lunette_top");
        this.wheel = bb.find("wheel");
        this.hatch = bb.find("hatch");
        this.trapLeft = bb.find("trap_left");
        this.trapRight = bb.find("trap_right");
        this.lever = bb.find("lever");
        this.chainLeft = bb.find("chain_left");
        this.chainRight = bb.find("chain_right");
    }

    @Override
    public void setupAnim(MachineRenderState s) {
        super.setupAnim(s);
        switch (s.kind) {
            case WHIPPING_POST -> {
                float sway = s.hasVictim ? 0.0F : Mth.sin(s.time * 0.05F) * 0.06F;
                if (this.chainLeft != null) {
                    this.chainLeft.zRot += sway;
                }
                if (this.chainRight != null) {
                    this.chainRight.zRot -= sway;
                }
            }
            case IRON_MAIDEN -> {
                // the doors swing out on their hinges: 100 degrees open, 0 shut
                float open = (1.0F - s.a) * 100.0F * Mth.DEG_TO_RAD;
                if (this.doorRight != null) {
                    this.doorRight.yRot += open;
                }
                if (this.doorLeft != null) {
                    this.doorLeft.yRot -= open;
                }
            }
            case RAT_TABLE -> {
                if (this.cageDoor != null) {
                    this.cageDoor.xRot -= s.a * 1.7F;
                }
            }
            case GUILLOTINE -> {
                if (this.blade != null) {
                    this.blade.y += (Guillotine.BLADE_UP - s.a) * 16.0F;
                }
                if (this.lunetteTop != null) {
                    this.lunetteTop.y -= (1.0F - s.b) * 7.0F;
                }
            }
            case WHEEL -> {
                if (this.wheel != null) {
                    this.wheel.y -= (float) (s.a - Wheel.HUB_LOW) * 16.0F;
                    this.wheel.zRot += s.b * Mth.DEG_TO_RAD;
                }
            }
            case BRAZEN_BULL -> {
                if (this.hatch != null) {
                    this.hatch.yRot -= s.a * 1.75F;
                }
            }
            case PILLORY -> {
                // the upper half of the board slides up on its posts when open
                if (this.lunetteTop != null) {
                    this.lunetteTop.y -= (1.0F - s.a) * 7.0F;
                }
            }
            case GALLOWS -> {
                float drop = s.a * 1.55F;
                if (this.trapLeft != null) {
                    this.trapLeft.zRot -= drop;
                }
                if (this.trapRight != null) {
                    this.trapRight.zRot += drop;
                }
                if (this.lever != null) {
                    this.lever.xRot += s.state >= 2 ? 0.9F : -0.5F;
                }
            }
            default -> {
            }
        }
    }
}
