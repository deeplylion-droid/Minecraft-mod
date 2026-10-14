package com.belowbedrock.registry;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.entity.Inhabitant;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    /** Him: never saved with the chunk, he only exists while he is looking at someone. */
    public static final EntityType<Inhabitant> INHABITANT = Registry.register(BuiltInRegistries.ENTITY_TYPE, BelowBedrock.id("inhabitant"),
            EntityType.Builder.of(Inhabitant::new, MobCategory.MISC)
                    .sized(0.6F, 2.2F)
                    .eyeHeight(2.0F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("inhabitant"));

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(INHABITANT, Inhabitant.createAttributes());
    }
}
