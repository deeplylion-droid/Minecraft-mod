package com.belowbedrock.client;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.net.ScreenFxPayload;
import com.belowbedrock.net.ScreenFxPayload.Fx;
import com.belowbedrock.world.Faded;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * Everything that happens to the picture: the faded look of the faded world, the long fall, and
 * the glitches the server asks for. One post-processing pass of our own, run right after the world
 * is drawn (so the HUD stays readable), only while something is actually showing.
 */
public final class ScreenFx {
    private static final ResourceLocation CHAIN = BelowBedrock.id("shaders/post/screen.json");
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private static PostChain chain;
    private static boolean failed;
    private static int chainWidth;
    private static int chainHeight;

    private static float fade;
    private static float previousFade;
    private static int fallAge;
    private static int fallTotal;
    private static int fogAge;
    private static int fogTotal;
    private static float fogDistance;
    private static float seed;
    private static final List<Pulse> PULSES = new ArrayList<>();

    private ScreenFx() {
    }

    /** One timed effect. */
    static final class Pulse {
        final Fx fx;
        final int duration;
        final float strength;
        int age;

        Pulse(Fx fx, int duration, float strength) {
            this.fx = fx;
            this.duration = Math.max(1, duration);
            this.strength = strength;
        }

        float progress(float partialTick) {
            return Mth.clamp((this.age + partialTick) / this.duration, 0.0F, 1.0F);
        }
    }

    public static void receive(ScreenFxPayload payload) {
        Fx[] all = Fx.values();
        if (payload.effect() < 0 || payload.effect() >= all.length) {
            return;
        }
        Fx fx = all[payload.effect()];
        Minecraft minecraft = Minecraft.getInstance();
        switch (fx) {
            case FALLING -> {
                fallTotal = payload.ticks();
                fallAge = 0;
            }
            case FALL_END -> fallTotal = 0;
            case FOG -> {
                fogTotal = payload.ticks();
                fogAge = 0;
                fogDistance = payload.strength();
            }
            case JUMPSCARE -> {
                PULSES.add(new Pulse(fx, payload.ticks(), payload.strength()));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_SCREAM, 0.55F, 1.0F));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ELDER_GUARDIAN_CURSE, 0.75F, 1.0F));
            }
            default -> PULSES.add(new Pulse(fx, payload.ticks(), payload.strength()));
        }
    }

    public static void tick(Minecraft minecraft) {
        previousFade = fade;
        float target = minecraft.level != null && Faded.is(minecraft.level) ? 1.0F : 0.0F;
        fade += (target - fade) * 0.12F;
        if (Math.abs(target - fade) < 0.002F) {
            fade = target;
        }
        for (Iterator<Pulse> it = PULSES.iterator(); it.hasNext(); ) {
            Pulse pulse = it.next();
            if (++pulse.age > pulse.duration) {
                it.remove();
            }
        }
        if (fallTotal > 0 && ++fallAge > fallTotal + 100) {
            fallTotal = 0;
        }
        if (fogTotal > 0 && ++fogAge > fogTotal) {
            fogTotal = 0;
        }
        if (minecraft.level == null) {
            reset();
        }
    }

    public static void reset() {
        PULSES.clear();
        fallTotal = 0;
        fogTotal = 0;
    }

    // ------------------------------------------------------------------ values for this frame

    public static float fade(float partialTick) {
        return Mth.lerp(partialTick, previousFade, fade);
    }

    /** 0..1 progress of the fall, or 0 when not falling. */
    public static float fall(float partialTick) {
        return fallTotal > 0 ? Mth.clamp((fallAge + partialTick) / fallTotal, 0.0F, 1.0F) : 0.0F;
    }

    public static float glitch(float partialTick) {
        float glitch = 0.0F;
        for (Pulse pulse : PULSES) {
            float p = pulse.progress(partialTick);
            switch (pulse.fx) {
                case GLITCH -> glitch = Math.max(glitch, pulse.strength * (1.0F - p * p));
                case JUMPSCARE -> glitch = Math.max(glitch, 0.9F * (1.0F - p));
                case FACE -> glitch = Math.max(glitch, 0.5F);
                case INVERT -> glitch = Math.max(glitch, 0.3F);
                default -> {
                }
            }
        }
        return glitch;
    }

    /** How black the screen is: blackouts fade in fast, hold, and fade out slowly. */
    public static float black(float partialTick) {
        float black = 0.0F;
        for (Pulse pulse : PULSES) {
            if (pulse.fx == Fx.BLACKOUT) {
                float t = pulse.age + partialTick;
                float in = Mth.clamp(t / 15.0F, 0.0F, 1.0F);
                float out = Mth.clamp((pulse.duration - t) / 35.0F, 0.0F, 1.0F);
                black = Math.max(black, Math.min(in, out) * pulse.strength);
            }
        }
        return black;
    }

    public static float invert(float partialTick) {
        for (Pulse pulse : PULSES) {
            if (pulse.fx == Fx.INVERT && pulse.age % 3 != 2) {
                return 1.0F;
            }
        }
        return 0.0F;
    }

    @Nullable
    public static Pulse active(Fx fx) {
        for (Pulse pulse : PULSES) {
            if (pulse.fx == fx) {
                return pulse;
            }
        }
        return null;
    }

    /** How far you can see in the faded world, in blocks. */
    public static float fogEnd(float renderDistance, float partialTick) {
        float normal = Math.min(renderDistance, 176.0F) * 0.62F;
        if (fogTotal <= 0) {
            return normal;
        }
        float t = fogAge + partialTick;
        float in = Mth.clamp(t / 80.0F, 0.0F, 1.0F);
        float out = Mth.clamp((fogTotal - t) / 120.0F, 0.0F, 1.0F);
        return Mth.lerp(Math.min(in, out), normal, Math.min(normal, fogDistance));
    }

    // ------------------------------------------------------------------ drawing

    /** Runs our pass over the world picture, right after the world is drawn and before the HUD. */
    public static void renderPost(float partialTick, float frameTicks) {
        float fadeNow = fade(partialTick);
        float fallNow = fall(partialTick);
        float glitch = glitch(partialTick);
        float black = black(partialTick);
        float invert = invert(partialTick);
        if (fadeNow <= 0.0F && fallNow <= 0.0F && glitch <= 0.0F && black <= 0.0F && invert <= 0.0F) {
            return;
        }
        PostChain postChain = chain();
        if (postChain == null) {
            return;
        }
        if (glitch > 0.0F && RANDOM.nextInt(2) == 0) {
            seed = RANDOM.nextFloat() * 100.0F;
        }
        postChain.setUniform("Fade", fadeNow);
        postChain.setUniform("Glitch", glitch);
        postChain.setUniform("Seed", seed);
        postChain.setUniform("Black", black);
        postChain.setUniform("Invert", invert);
        postChain.setUniform("Grain", 0.07F * fadeNow + 0.1F * fallNow);
        postChain.setUniform("Vignette", 0.45F * fadeNow + 0.9F * fallNow);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        postChain.process(frameTicks);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    @Nullable
    private static PostChain chain() {
        Minecraft minecraft = Minecraft.getInstance();
        if (chain == null && !failed) {
            try {
                chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), CHAIN);
                chainWidth = -1;
            } catch (IOException | JsonSyntaxException e) {
                BelowBedrock.LOGGER.error("Could not load the faded screen shader", e);
                failed = true;
            }
        }
        if (chain != null) {
            int width = minecraft.getWindow().getWidth();
            int height = minecraft.getWindow().getHeight();
            if (width != chainWidth || height != chainHeight) {
                chain.resize(width, height);
                chainWidth = width;
                chainHeight = height;
            }
        }
        return chain;
    }

    /** Shaders are reloaded with the resource packs. */
    public static void reload() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        failed = false;
    }
}
