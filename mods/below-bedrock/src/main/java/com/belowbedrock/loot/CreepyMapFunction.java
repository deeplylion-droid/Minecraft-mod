package com.belowbedrock.loot;

import com.belowbedrock.registry.ModLoot;
import com.belowbedrock.world.FadedMaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** {@code belowbedrock:creepy_map}: turns the item into one of His maps ({@code picture} or a random one). */
public class CreepyMapFunction extends LootItemConditionalFunction {
    public static final MapCodec<CreepyMapFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(Codec.STRING.optionalFieldOf("picture").forGetter(function -> function.picture))
            .apply(instance, CreepyMapFunction::new));
    private final Optional<String> picture;

    protected CreepyMapFunction(List<LootItemCondition> conditions, Optional<String> picture) {
        super(conditions);
        this.picture = picture;
    }

    @Override
    public LootItemFunctionType<CreepyMapFunction> getType() {
        return ModLoot.CREEPY_MAP;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        String name = this.picture.orElseGet(() -> FadedMaps.random(context.getRandom()));
        return FadedMaps.create(context.getLevel(), name);
    }
}
