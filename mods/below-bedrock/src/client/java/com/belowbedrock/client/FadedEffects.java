package com.belowbedrock.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The faded sky: no sun, no moon, no stars, no clouds. Only a pale grey that fades into the fog. */
public class FadedEffects extends DimensionSpecialEffects {
    private static final Vec3 FOG = new Vec3(0.74, 0.745, 0.73);

    public FadedEffects() {
        super(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFog, float brightness) {
        return FOG;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return true;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick) {
        return null;
    }
}
