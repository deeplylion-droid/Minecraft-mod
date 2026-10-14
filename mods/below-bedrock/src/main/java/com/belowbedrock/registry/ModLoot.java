package com.belowbedrock.registry;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.loot.CreepyMapFunction;
import com.belowbedrock.loot.DiaryFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

public final class ModLoot {
    public static final LootItemFunctionType<CreepyMapFunction> CREEPY_MAP = Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE,
            BelowBedrock.id("creepy_map"), new LootItemFunctionType<>(CreepyMapFunction.CODEC));
    public static final LootItemFunctionType<DiaryFunction> DIARY = Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE,
            BelowBedrock.id("diary"), new LootItemFunctionType<>(DiaryFunction.CODEC));

    /** Chests left in the faded world. */
    public static final ResourceKey<LootTable> CAMP_CHEST = ResourceKey.create(Registries.LOOT_TABLE, BelowBedrock.id("chests/faded_camp"));
    public static final ResourceKey<LootTable> ROOM_CHEST = ResourceKey.create(Registries.LOOT_TABLE, BelowBedrock.id("chests/faded_room"));
    public static final ResourceKey<LootTable> BURIED_CHEST = ResourceKey.create(Registries.LOOT_TABLE, BelowBedrock.id("chests/faded_buried"));

    private ModLoot() {
    }

    public static void register() {
    }
}
