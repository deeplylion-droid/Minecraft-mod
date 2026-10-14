package com.jumpscares.registry;

import com.jumpscares.Jumpscares;
import com.jumpscares.entity.ScarePearl;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<ScarePearl> SCARE_PEARL = Registry.register(BuiltInRegistries.ENTITY_TYPE, Jumpscares.id("scare_pearl"),
            EntityType.Builder.<ScarePearl>of(ScarePearl::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .noSave()
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("scare_pearl"));

    private ModEntities() {
    }

    public static void register() {
    }
}
