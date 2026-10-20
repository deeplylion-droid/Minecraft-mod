package com.frusta.registry;

import com.frusta.FrustaMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FrustaMod.MODID);

    /** The tip breaking the sound barrier. */
    public static final RegistryObject<SoundEvent> CRACK = register("crack");
    /** The Imperial Crack: a crack with a thunderclap and a long echo. */
    public static final RegistryObject<SoundEvent> CRACK_IMPERIAL = register("crack_imperial");
    /** The whip cutting through the air. */
    public static final RegistryObject<SoundEvent> WHOOSH = register("whoosh");
    /** The whip spinning over the head (a loop). */
    public static final RegistryObject<SoundEvent> TWIRL = register("twirl");
    /** The whip landing on someone. */
    public static final RegistryObject<SoundEvent> HIT = register("hit");
    /** The whip wrapping around someone. */
    public static final RegistryObject<SoundEvent> WRAP = register("wrap");
    /** Pulling someone in. */
    public static final RegistryObject<SoundEvent> YANK = register("yank");
    /** Someone falling to their knees. */
    public static final RegistryObject<SoundEvent> KNEEL = register("kneel");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(FrustaMod.id(name)));
    }

    private ModSounds() {
    }
}
