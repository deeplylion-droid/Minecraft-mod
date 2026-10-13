package com.wurstel.recipe;

import com.google.gson.JsonObject;
import com.wurstel.item.WurstelBeltItem;
import com.wurstel.registry.ModRecipes;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A shaped crafting recipe that upgrades a würstel belt and, like the smithing table, keeps the
 * belt's enchantments, name and other data. Written like {@code minecraft:crafting_shaped}.
 */
public class BeltUpgradeRecipe extends ShapedRecipe {

    public BeltUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(),
                recipe.getIngredients(), recipe.getResultItem(RegistryAccess.EMPTY));
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = super.assemble(container, registryAccess);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() instanceof WurstelBeltItem && stack.hasTag()) {
                result.setTag(stack.getTag().copy());
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.BELT_UPGRADE.get();
    }

    public static class Serializer implements RecipeSerializer<BeltUpgradeRecipe> {
        @Override
        public BeltUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new BeltUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public @Nullable BeltUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapedRecipe recipe = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer);
            return recipe == null ? null : new BeltUpgradeRecipe(recipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, BeltUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
