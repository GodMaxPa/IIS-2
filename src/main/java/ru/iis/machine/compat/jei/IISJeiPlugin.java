package ru.iis.machine.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import ru.iis.IIS;
import ru.iis.machine.IISMachines;
import ru.iis.machine.SteamMachineType;
import ru.iis.machine.client.ClientRecipes;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Показ рецептов машин ИИС в JEI. JEI необязателен: класс загружается только им. */
@JeiPlugin
public final class IISJeiPlugin implements IModPlugin {
    private final Map<GtRecipeMap, IRecipeHolderType<GtRecipe>> types = new EnumMap<>(GtRecipeMap.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(IIS.MODID, "machines");
    }

    private IRecipeHolderType<GtRecipe> type(GtRecipeMap m) {
        return types.computeIfAbsent(m, k -> IRecipeType.create(GtRecipes.type(k)));
    }

    private static List<ItemLike> stations(GtRecipeMap m) {
        List<ItemLike> r = new ArrayList<>();
        if (m == GtRecipeMap.PRIMITIVE_BLAST) {
            r.add(IISMachines.BRICKED_BLAST_FURNACE.get());
            return r;
        }
        if (m == GtRecipeMap.COKE_OVEN) {
            r.add(ru.iis.lv.LvContent.COKE_OVEN.get());
            return r;
        }
        for (SteamMachineType t : SteamMachineType.values()) {
            if (t.recipeMap != m) continue;
            for (SteamMachineType.Tier tier : SteamMachineType.Tier.values()) r.add(IISMachines.STEAM_MACHINES.get(t.blockId(tier)).get());
        }
        for (ru.iis.lv.LvMachineType t : ru.iis.lv.LvMachineType.values()) {
            if (t.recipeMap == m) r.add(ru.iis.lv.LvContent.MACHINES.get(t).get());
        }
        return r;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (GtRecipeMap m : GtRecipeMap.values()) {
            registration.addRecipeCategories(new GtRecipeCategory(type(m), m, registration.getJeiHelpers().getGuiHelper(), stations(m).getFirst()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (GtRecipeMap m : GtRecipeMap.values()) {
            registration.addRecipes(type(m), new ArrayList<>(GtRecipes.all(ClientRecipes.get(), m)));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (GtRecipeMap m : GtRecipeMap.values()) {
            registration.addCraftingStation(type(m), stations(m).toArray(ItemLike[]::new));
        }
        for (SteamMachineType.Tier tier : SteamMachineType.Tier.values()) {
            registration.addCraftingStation(RecipeTypes.SMELTING, IISMachines.STEAM_MACHINES.get(SteamMachineType.FURNACE.blockId(tier)).get());
        }
        registration.addCraftingStation(RecipeTypes.SMELTING, ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_FURNACE).get());
    }
}
