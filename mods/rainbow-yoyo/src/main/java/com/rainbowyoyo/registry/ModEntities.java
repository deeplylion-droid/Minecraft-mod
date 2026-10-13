package com.rainbowyoyo.registry;

import com.rainbowyoyo.RainbowYoyoMod;
import com.rainbowyoyo.entity.YoyoEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RainbowYoyoMod.MODID);

    /** The thrown yo-yo. Never saved: it only exists while it is out of the hand. */
    public static final RegistryObject<EntityType<YoyoEntity>> YOYO = ENTITIES.register("rainbow_yoyo",
            () -> EntityType.Builder.<YoyoEntity>of(YoyoEntity::new, MobCategory.MISC)
                    .sized(1.25F, 1.25F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .build("rainbow_yoyo"));

    private ModEntities() {
    }
}
