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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import ru.iis.IIS;
import ru.iis.machine.IISMachines;
import ru.iis.machine.SteamMachineType;
import ru.iis.machine.client.ClientRecipes;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Показ рецептов машин ИИС в JEI. JEI необязателен: класс загружается только им.
 * D101: рецепты на энергии разделены по напряжению — отдельная категория для LV, MV, HV, EV и IV+ (по EU/t рецепта);
 * машина показана в категориях своего уровня и ниже, паровые — в LV.
 */
@JeiPlugin
public final class IISJeiPlugin implements IModPlugin {
    /** LV, MV, HV, EV, IV+: рецепт попадает в первую, где EU/t не больше напряжения. */
    static final String[] TIERS = {"LV", "MV", "HV", "EV", "IV"};
    private static final long[] VOLTS = {32, 128, 512, 2048, Long.MAX_VALUE};
    private final Map<String, IRecipeHolderType<GtRecipe>> types = new HashMap<>();

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(IIS.MODID, "machines");
    }

    /** Карты без EU (кирпичная доменная печь, коксовая печь) не делятся. */
    static boolean tiered(GtRecipeMap m) {
        return m != GtRecipeMap.PRIMITIVE_BLAST && m != GtRecipeMap.COKE_OVEN;
    }

    static int recipeTier(GtRecipe r) {
        int t = 0;
        while (r.eut() > VOLTS[t]) t++;
        return t;
    }

    /** tier — индекс в TIERS, -1 — неделимая карта. */
    private IRecipeHolderType<GtRecipe> type(GtRecipeMap m, int tier) {
        if (tier < 0) return types.computeIfAbsent(m.id, k -> IRecipeType.create(GtRecipes.type(m)));
        return types.computeIfAbsent(m.id + "_" + tier, k -> IRecipeHolderType.create(
                Identifier.fromNamespaceAndPath(IIS.MODID, m.id + "_" + TIERS[tier].toLowerCase(java.util.Locale.ROOT))));
    }

    private static int[] tiersOf(GtRecipeMap m) {
        return tiered(m) ? new int[]{0, 1, 2, 3, 4} : new int[]{-1};
    }

    /** Машины, на которых идут рецепты уровня tier (-1 — все). */
    private static List<ItemLike> stations(GtRecipeMap m, int tier) {
        List<ItemLike> r = new ArrayList<>();
        if (m == GtRecipeMap.PRIMITIVE_BLAST) {
            r.add(IISMachines.BRICKED_BLAST_FURNACE.get());
            return r;
        }
        if (m == GtRecipeMap.BLAST) {
            r.add(ru.iis.multi.MultiContent.EBF.get());
            return r;
        }
        if (m == GtRecipeMap.VACUUM_FREEZER) {
            r.add(ru.iis.multi.MultiContent.VACUUM_FREEZER.get());
            return r;
        }
        // D104: многоблоки
        if (m == GtRecipeMap.IMPLOSION) {
            r.add(ru.iis.multi.MultiContent.IMPLOSION_COMPRESSOR.get());
            return r;
        }
        if (m == GtRecipeMap.DISTILLATION_TOWER) {
            r.add(ru.iis.multi.MultiContent.DISTILLATION_TOWER.get());
            return r;
        }
        if (m == GtRecipeMap.CRACKING) {
            r.add(ru.iis.multi.MultiContent.OIL_CRACKER.get());
            return r;
        }
        if (m == GtRecipeMap.PYROLYSE) {
            r.add(ru.iis.multi.MultiContent.PYROLYSE_OVEN.get());
            return r;
        }
        if (m == GtRecipeMap.COKE_OVEN) {
            r.add(ru.iis.lv.LvContent.COKE_OVEN.get());
            return r;
        }
        if (tier <= 0) {
            for (SteamMachineType t : SteamMachineType.values()) {
                if (t.recipeMap != m) continue;
                for (SteamMachineType.Tier st : SteamMachineType.Tier.values()) r.add(IISMachines.STEAM_MACHINES.get(t.blockId(st)).get());
            }
        }
        for (ru.iis.lv.LvMachineType t : ru.iis.lv.LvMachineType.values()) {
            if (t.recipeMap == m && t.tier.tier() - 1 >= tier) r.add(ru.iis.lv.LvContent.MACHINES.get(t).get());
        }
        return r;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (GtRecipeMap m : GtRecipeMap.values()) {
            List<ItemLike> all = stations(m, -1);
            if (all.isEmpty()) continue;   // карта без машины (её многоблок ещё не перенесён)
            for (int tier : tiersOf(m)) {
                List<ItemLike> st = stations(m, tier);
                ItemLike icon = st.isEmpty() ? all.getLast() : st.getFirst();
                registration.addRecipeCategories(new GtRecipeCategory(type(m, tier), m, tier < 0 ? null : TIERS[tier],
                        registration.getJeiHelpers().getGuiHelper(), icon));
            }
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (GtRecipeMap m : GtRecipeMap.values()) {
            if (stations(m, -1).isEmpty()) continue;
            List<RecipeHolder<GtRecipe>> all = new ArrayList<>(GtRecipes.all(ClientRecipes.get(), m));
            for (int tier : tiersOf(m)) {
                registration.addRecipes(type(m, tier), tier < 0 ? all : all.stream().filter(h -> recipeTier(h.value()) == tier).toList());
            }
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (GtRecipeMap m : GtRecipeMap.values()) {
            for (int tier : tiersOf(m)) {
                List<ItemLike> st = stations(m, tier);
                if (!st.isEmpty()) registration.addCraftingStation(type(m, tier), st.toArray(ItemLike[]::new));
            }
        }
        for (SteamMachineType.Tier tier : SteamMachineType.Tier.values()) {
            registration.addCraftingStation(RecipeTypes.SMELTING, IISMachines.STEAM_MACHINES.get(SteamMachineType.FURNACE.blockId(tier)).get());
        }
        registration.addCraftingStation(RecipeTypes.SMELTING, ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_FURNACE).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_FURNACE_MV).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_OVEN).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_OVEN_MV).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_FURNACE_HV).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_OVEN_HV).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_FURNACE_EV).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_OVEN_EV).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_FURNACE_IV).get(),
                ru.iis.lv.LvContent.MACHINES.get(ru.iis.lv.LvMachineType.ELECTRIC_OVEN_IV).get());
    }
}
