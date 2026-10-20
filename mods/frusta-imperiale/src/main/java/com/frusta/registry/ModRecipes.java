package com.frusta.registry;

import com.frusta.FrustaMod;
import com.frusta.recipe.WhipUpgradeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, FrustaMod.MODID);

    /** Shaped crafting that upgrades a whip and keeps its enchantments. */
    public static final RegistryObject<RecipeSerializer<WhipUpgradeRecipe>> WHIP_UPGRADE =
            SERIALIZERS.register("whip_upgrade", WhipUpgradeRecipe.Serializer::new);

    private ModRecipes() {
    }
}
