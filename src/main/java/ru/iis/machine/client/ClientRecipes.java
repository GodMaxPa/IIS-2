package ru.iis.machine.client;

import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import org.jspecify.annotations.Nullable;

/** Рецепты, присланные сервером клиенту (для JEI). */
public final class ClientRecipes {
    private ClientRecipes() {}

    private static @Nullable RecipeMap recipes;

    public static void onReceived(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
    }

    public static RecipeMap get() {
        return recipes != null ? recipes : RecipeMap.EMPTY;
    }
}
