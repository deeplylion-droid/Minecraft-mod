package com.wurstel.registry;

import com.wurstel.WurstelMod;
import com.wurstel.recipe.BeltUpgradeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, WurstelMod.MODID);

    /** Shaped crafting that upgrades a belt and keeps its enchantments. */
    public static final RegistryObject<RecipeSerializer<BeltUpgradeRecipe>> BELT_UPGRADE =
            SERIALIZERS.register("belt_upgrade", BeltUpgradeRecipe.Serializer::new);

    private ModRecipes() {
    }
}
