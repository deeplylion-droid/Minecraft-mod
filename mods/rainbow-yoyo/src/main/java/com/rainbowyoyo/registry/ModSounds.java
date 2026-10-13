package com.rainbowyoyo.registry;

import com.rainbowyoyo.RainbowYoyoMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RainbowYoyoMod.MODID);

    /** The yo-yo leaving the hand. */
    public static final RegistryObject<SoundEvent> THROW = register("throw");
    /** Loop of the yo-yo spinning; pitch and volume follow its speed. */
    public static final RegistryObject<SoundEvent> SPIN = register("spin");
    /** The yo-yo smashing into something. */
    public static final RegistryObject<SoundEvent> HIT = register("hit");
    /** The yo-yo snapping back into the hand. */
    public static final RegistryObject<SoundEvent> CATCH = register("catch");
    /** A trick starting. */
    public static final RegistryObject<SoundEvent> TRICK = register("trick");
    /** Shoot the Moon hitting the ground. */
    public static final RegistryObject<SoundEvent> SLAM = register("slam");
    /** The Supernova charging up. */
    public static final RegistryObject<SoundEvent> CHARGE = register("charge");
    /** The Supernova exploding. */
    public static final RegistryObject<SoundEvent> BOOM = register("boom");
    /** All four tricks done: the Supernova is ready. */
    public static final RegistryObject<SoundEvent> READY = register("ready");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RainbowYoyoMod.MODID, name)));
    }

    private ModSounds() {
    }
}
