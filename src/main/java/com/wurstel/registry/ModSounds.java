package com.wurstel.registry;

import com.wurstel.WurstelMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WurstelMod.MODID);

    /** The würstel smacking into something. */
    public static final RegistryObject<SoundEvent> SLAP = register("slap");
    /** The würstel cutting through the air. */
    public static final RegistryObject<SoundEvent> WHOOSH = register("whoosh");
    /** Putting the belt on. */
    public static final RegistryObject<SoundEvent> SQUISH = register("squish");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(WurstelMod.MODID, name)));
    }

    private ModSounds() {
    }
}
