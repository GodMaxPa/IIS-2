package ru.iis.machine.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/** Содержимое входных слотов машины. */
public record GtRecipeInput(List<ItemStack> stacks) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return stacks.get(index);
    }

    @Override
    public int size() {
        return stacks.size();
    }
}
