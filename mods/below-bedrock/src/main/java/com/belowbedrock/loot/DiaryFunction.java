package com.belowbedrock.loot;

import com.belowbedrock.registry.ModLoot;
import com.belowbedrock.world.Diaries;
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

/** {@code belowbedrock:diary}: turns the item into a volume of the diary ({@code volume} 1-3, or random). */
public class DiaryFunction extends LootItemConditionalFunction {
    public static final MapCodec<DiaryFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(Codec.intRange(1, Diaries.VOLUMES).optionalFieldOf("volume").forGetter(function -> function.volume))
            .apply(instance, DiaryFunction::new));
    private final Optional<Integer> volume;

    protected DiaryFunction(List<LootItemCondition> conditions, Optional<Integer> volume) {
        super(conditions);
        this.volume = volume;
    }

    @Override
    public LootItemFunctionType<DiaryFunction> getType() {
        return ModLoot.DIARY;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        return Diaries.diary(this.volume.orElseGet(() -> Diaries.randomVolume(context.getRandom())));
    }
}
