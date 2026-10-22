package ru.iis.bio.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import ru.iis.IIS;
import ru.iis.bio.BioContent;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.production.BioRecipe;
import ru.iis.bio.production.Station;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Рецепты мест Био в JEI (D70). JEI необязателен: класс загружается только им. Таблица рецептов статична — синхронизация не нужна. */
@JeiPlugin
public final class BioJeiPlugin implements IModPlugin {
    private final Map<Station, IRecipeType<BioRecipe>> types = new EnumMap<>(Station.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(IIS.MODID, "bio");
    }

    private IRecipeType<BioRecipe> type(Station s) {
        return types.computeIfAbsent(s, k -> IRecipeType.create(Identifier.fromNamespaceAndPath(IIS.MODID, "bio_" + k.id), BioRecipe.class));
    }

    private static ItemLike station(Station s) {
        return switch (s) {
            case BUTCHER -> BioContent.BUTCHER.get();
            case MORTAR -> BioContent.MORTAR.get();
            case POT -> BioContent.POT.get();
            case MIXER -> BioContent.MIXER.get();
            case POCKET -> BioContent.CORE.get();
            case SUBSTRATE -> BioContent.SUBSTRATE.get();
            case BED -> BioContent.BED.get();
            case PRESS -> BioContent.PRESS.get();
            case FILTER -> BioContent.FILTER.get();
            case NEST -> BioContent.NEST.get();
            case GLAND -> BioContent.GLAND.get();
            case MEMBRANE_FILTER -> BioContent.MEMBRANE_FILTER.get();
        };
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (Station s : Station.values()) registration.addRecipeCategories(new Category(type(s), s, gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (Station s : Station.values()) {
            List<BioRecipe> list = new ArrayList<>();
            for (BioRecipe r : BioRecipe.all()) if (r.station().equals(s.id)) list.add(r);
            registration.addRecipes(type(s), list);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Station s : Station.values()) {
            registration.addCraftingStation(type(s), station(s));
            if (s == Station.POCKET) registration.addCraftingStation(type(s), BioContent.MAW.get());
            if (s == Station.POT) registration.addCraftingStation(type(s), BioContent.HEARTH.get());
        }
    }

    static List<ItemStack> stacks(String key, int count) {
        List<ItemStack> r = new ArrayList<>();
        if ("food".equals(key)) {
            for (Item i : List.of(Items.BREAD, Items.COOKED_BEEF, Items.APPLE, Items.CARROT, Items.ROTTEN_FLESH)) r.add(new ItemStack(i, count));
        } else if (key.startsWith("#")) {
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1))))) r.add(new ItemStack(h, count));
        } else {
            r.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(key)), count));
        }
        return r;
    }

    static final class Category extends AbstractRecipeCategory<BioRecipe> {
        private final Station station;

        Category(IRecipeType<BioRecipe> type, Station station, IGuiHelper gui) {
            super(type, Component.translatable("jei.iis.bio." + station.id), gui.createDrawableItemLike(BioJeiPlugin.station(station)), 160, 76);
            this.station = station;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BioRecipe r, IFocusGroup focuses) {
            int x = 0;
            for (BioRecipe.In in : r.items()) {
                builder.addInputSlot(x, 0).setStandardSlotBackground().addItemStacks(stacks(in.key(), in.count()));
                x += 18;
            }
            if (!r.tool().isEmpty()) {
                IRecipeSlotBuilder tool = builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.CRAFTING_STATION, 142, 0).setStandardSlotBackground();
                tool.addItemStacks(stacks(r.tool(), 1));
            }
            x = 0;
            for (BioRecipe.FluidIn f : r.fluids()) {
                Fluid fl = BioFluids.byKey(f.fluid());
                if (fl != null) builder.addInputSlot(x, 20).setStandardSlotBackground().add(fl, (long) f.amount());
                x += 18;
            }
            x = 0;
            for (BioRecipe.Out o : r.outItems()) {
                builder.addOutputSlot(x, 42).setOutputSlotBackground().add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(o.item())), o.count()));
                x += 18;
            }
            for (BioRecipe.FluidOut f : r.outFluids()) {
                Fluid fl = BioFluids.byKey(f.fluid());
                if (fl == null) continue;
                builder.addOutputSlot(x, 42).setOutputSlotBackground().add(fl, (long) (f.perNutrition() > 0 ? f.perNutrition() : f.amount()));
                x += 18;
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, BioRecipe r, IFocusGroup focuses) {
            List<net.minecraft.network.chat.FormattedText> lines = new ArrayList<>();
            lines.add(Component.translatable("jei.iis.duration", String.format(Locale.ROOT, "%d", r.seconds())));
            if (r.heat()) lines.add(Component.translatable("jei.iis.bio.heat"));
            if (r.upkeep() > 0) lines.add(Component.translatable("jei.iis.bio.upkeep", String.format(Locale.ROOT, "%.3f", r.upkeep())));
            for (BioRecipe.FluidOut f : r.outFluids()) if (f.perNutrition() > 0) lines.add(Component.translatable("jei.iis.bio.per_nutrition", f.perNutrition()));
            if (station.modes > 1) lines.add(Component.translatable("gui.iis.bio." + station.id + ".mode." + r.mode()));
            builder.addText(lines, 160, 16).setPosition(0, 62 - 0);
        }
    }
}
