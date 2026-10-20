package com.frusta.recipe;

import com.frusta.item.WhipItem;
import com.frusta.registry.ModRecipes;
import com.google.gson.JsonObject;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A shaped crafting recipe that upgrades a whip and, like the smithing table, keeps the whip's
 * enchantments, name and other data. Written like {@code minecraft:crafting_shaped}.
 */
public class WhipUpgradeRecipe extends ShapedRecipe {

    public WhipUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(),
                recipe.getIngredients(), recipe.getResultItem(RegistryAccess.EMPTY));
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = super.assemble(container, registryAccess);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() instanceof WhipItem && stack.hasTag()) {
                result.setTag(stack.getTag().copy());
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WHIP_UPGRADE.get();
    }

    public static class Serializer implements RecipeSerializer<WhipUpgradeRecipe> {
        @Override
        public WhipUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new WhipUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public @Nullable WhipUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapedRecipe recipe = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer);
            return recipe == null ? null : new WhipUpgradeRecipe(recipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, WhipUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
