package com.filorosso.entity;

import com.filorosso.FiloRosso;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private ModEntities() {
    }

    /** Lo Spezzacuori's servants ("gli Spaiati"): they come at night and cut threads. */
    public static final EntityType<Servant> SERVANT = register("servant",
            EntityType.Builder.<Servant>of(Servant::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8));
    /** The giant that hates love. */
    public static final EntityType<Spezzacuori> SPEZZACUORI = register("spezzacuori",
            EntityType.Builder.<Spezzacuori>of(Spezzacuori::new, MobCategory.MONSTER).sized(2.2F, 7.2F).eyeHeight(6.4F).fireImmune().clientTrackingRange(16));
    /** His thrown scissors. */
    public static final EntityType<Scissors> SCISSORS = register("scissors",
            EntityType.Builder.<Scissors>of(Scissors::new, MobCategory.MISC).sized(0.8F, 0.8F).clientTrackingRange(8).updateInterval(2));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FiloRosso.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(SERVANT, Servant.createServantAttributes());
        FabricDefaultAttributeRegistry.register(SPEZZACUORI, Spezzacuori.createBossAttributes());
    }
}
