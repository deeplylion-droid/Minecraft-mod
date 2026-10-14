package com.jumpscares.client.scare;

/** Where the monster is and what the screen does at one moment of a scare (camera space of the victim). */
public final class ScarePose {
    /** Position of the face (blocks); the camera looks down -z. */
    public float x, y, z;
    /** Degrees. */
    public float pitch, yaw, roll;
    /** Jaw (or fangs): 0 closed, 1 wide open. */
    public float jaw;
    /** Arms: 0 hanging, 1 grabbing at you. */
    public float reach;
    /** Spider legs flailing. */
    public float legs;
    /** Screen shake, colour flash, static and black, 0..1. */
    public float shake, flash, noise, black;
    /** Dark vignette, 0..1. */
    public float vignette;
    /** Multiplier of the field of view (punch on impact). */
    public float fov;
    public boolean visible;

    void clear() {
        this.x = this.y = this.z = 0.0F;
        this.pitch = this.yaw = this.roll = 0.0F;
        this.jaw = this.reach = this.legs = 0.0F;
        this.shake = this.flash = this.noise = this.black = this.vignette = 0.0F;
        this.fov = 1.0F;
        this.visible = true;
    }
}
