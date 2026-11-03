package com.punizioni.entity;

import com.punizioni.Punizioni;
import com.punizioni.machine.BrazenBull;
import com.punizioni.machine.ChoppingBlock;
import com.punizioni.machine.Gallows;
import com.punizioni.machine.Guillotine;
import com.punizioni.machine.IronMaiden;
import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import com.punizioni.machine.Pillory;
import com.punizioni.machine.Pyre;
import com.punizioni.machine.QuarteringRack;
import com.punizioni.machine.RatTable;
import com.punizioni.machine.WhippingPost;
import com.punizioni.machine.Wheel;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import java.util.EnumMap;
import java.util.Map;

public final class ModEntities {
    private ModEntities() {
    }

    public static final Map<MachineKind, EntityType<? extends Machine>> MACHINES = new EnumMap<>(MachineKind.class);

    public static final EntityType<WhippingPost> WHIPPING_POST = machine(MachineKind.WHIPPING_POST,
            EntityType.Builder.of(WhippingPost::new, MobCategory.MISC).sized(0.9F, 2.8F));
    public static final EntityType<IronMaiden> IRON_MAIDEN = machine(MachineKind.IRON_MAIDEN,
            EntityType.Builder.of(IronMaiden::new, MobCategory.MISC).sized(1.3F, 3.0F));
    public static final EntityType<QuarteringRack> QUARTERING_RACK = machine(MachineKind.QUARTERING_RACK,
            EntityType.Builder.of(QuarteringRack::new, MobCategory.MISC).sized(2.4F, 1.0F));
    public static final EntityType<ChoppingBlock> CHOPPING_BLOCK = machine(MachineKind.CHOPPING_BLOCK,
            EntityType.Builder.of(ChoppingBlock::new, MobCategory.MISC).sized(0.9F, 0.9F));
    public static final EntityType<RatTable> RAT_TABLE = machine(MachineKind.RAT_TABLE,
            EntityType.Builder.of(RatTable::new, MobCategory.MISC).sized(2.2F, 1.1F));
    public static final EntityType<Pyre> PYRE = machine(MachineKind.PYRE,
            EntityType.Builder.of(Pyre::new, MobCategory.MISC).sized(2.6F, 3.4F));
    public static final EntityType<Guillotine> GUILLOTINE = machine(MachineKind.GUILLOTINE,
            EntityType.Builder.of(Guillotine::new, MobCategory.MISC).sized(1.6F, 4.3F));
    public static final EntityType<Wheel> WHEEL = machine(MachineKind.WHEEL,
            EntityType.Builder.of(Wheel::new, MobCategory.MISC).sized(2.4F, 5.8F));
    public static final EntityType<BrazenBull> BRAZEN_BULL = machine(MachineKind.BRAZEN_BULL,
            EntityType.Builder.of(BrazenBull::new, MobCategory.MISC).sized(3.4F, 2.6F));
    public static final EntityType<Gallows> GALLOWS = machine(MachineKind.GALLOWS,
            EntityType.Builder.of(Gallows::new, MobCategory.MISC).sized(3.2F, 5.2F));
    public static final EntityType<Pillory> PILLORY = machine(MachineKind.PILLORY,
            EntityType.Builder.of(Pillory::new, MobCategory.MISC).sized(2.3F, 2.5F));

    public static final EntityType<DraftHorse> DRAFT_HORSE = register("draft_horse",
            EntityType.Builder.of(DraftHorse::new, MobCategory.MISC).sized(1.3964844F, 1.6F).eyeHeight(1.52F).clientTrackingRange(10)
                    .updateInterval(1).noSave());
    public static final EntityType<Rat> RAT = register("rat",
            EntityType.Builder.of(Rat::new, MobCategory.MISC).sized(0.45F, 0.3F).eyeHeight(0.2F).clientTrackingRange(6).updateInterval(1)
                    .noSave());

    /** A thrown lump of dung. */
    public static final EntityType<PoopProjectile> POOP = register("poop",
            EntityType.Builder.<PoopProjectile>of(PoopProjectile::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4)
                    .updateInterval(10));

    /** A roast pig on its platter, to set on a table. */
    public static final EntityType<RoastPig> ROAST_PIG = register("roast_pig",
            EntityType.Builder.<RoastPig>of(RoastPig::new, MobCategory.MISC).sized(1.4F, 0.6F).clientTrackingRange(8).updateInterval(10).fireImmune());
    /** A pot of gruel on its fire. */
    public static final EntityType<GruelPot> GRUEL_POT = register("gruel_pot",
            EntityType.Builder.<GruelPot>of(GruelPot::new, MobCategory.MISC).sized(1.0F, 1.1F).clientTrackingRange(8).updateInterval(10).fireImmune());

    private static <T extends Machine> EntityType<T> machine(MachineKind kind, EntityType.Builder<T> builder) {
        EntityType<T> type = register(kind.id(), builder.clientTrackingRange(10).updateInterval(2).fireImmune());
        MACHINES.put(kind, type);
        return type;
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Punizioni.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(DRAFT_HORSE, AbstractHorse.createBaseHorseAttributes());
        FabricDefaultAttributeRegistry.register(RAT, Rat.createAttributes());
    }
}
