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

    /** Recisione's servants ("gli Spaiati"): they come at night and cut threads. */
    public static final EntityType<Servant> SERVANT = register("servant",
            EntityType.Builder.<Servant>of(Servant::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8));
    /** The black giant made of broken threads. */
    public static final EntityType<Recisione> RECISIONE = register("recisione",
            EntityType.Builder.<Recisione>of(Recisione::new, MobCategory.MONSTER).sized(2.0F, 9.6F).eyeHeight(9.0F).fireImmune().clientTrackingRange(16));
    /** Its missiles from the sky. */
    public static final EntityType<Missile> MISSILE = register("missile",
            EntityType.Builder.<Missile>of(Missile::new, MobCategory.MISC).sized(0.9F, 0.9F).clientTrackingRange(10).updateInterval(2).noSave());
    /** Its hand coming out of the ground. */
    public static final EntityType<Grasp> GRASP = register("grasp",
            EntityType.Builder.<Grasp>of(Grasp::new, MobCategory.MISC).sized(1.0F, 3.0F).clientTrackingRange(10).updateInterval(20).noSave());

    /** The super boss of the Theatre. */
    public static final EntityType<Burattinaio> BURATTINAIO = register("burattinaio",
            EntityType.Builder.<Burattinaio>of(Burattinaio::new, MobCategory.MONSTER).sized(3.0F, 15.5F).eyeHeight(15.0F).fireImmune()
                    .clientTrackingRange(16));
    /** The knot of its strings over a puppet's head. */
    public static final EntityType<StringKnot> STRING_KNOT = register("string_knot",
            EntityType.Builder.<StringKnot>of(StringKnot::new, MobCategory.MISC).sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(1).noSave());
    /** Its threads falling from the dark. */
    public static final EntityType<ThreadSpear> THREAD_SPEAR = register("thread_spear",
            EntityType.Builder.<ThreadSpear>of(ThreadSpear::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(20).noSave());
    /** The black spectators of the stalls. */
    public static final EntityType<Spectator> SPECTATOR = register("spectator",
            EntityType.Builder.<Spectator>of(Spectator::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).noSave());

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FiloRosso.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(SERVANT, Servant.createServantAttributes());
        FabricDefaultAttributeRegistry.register(RECISIONE, Recisione.createBossAttributes());
        FabricDefaultAttributeRegistry.register(BURATTINAIO, Burattinaio.createBossAttributes());
        FabricDefaultAttributeRegistry.register(SPECTATOR, Spectator.createSpectatorAttributes());
    }
}
