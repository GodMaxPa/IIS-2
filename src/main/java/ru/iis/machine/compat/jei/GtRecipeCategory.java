package ru.iis.machine.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import ru.iis.machine.recipe.ChanceOutput;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;

import java.util.List;

/** Категория JEI для одной карты рецептов GT. */
public class GtRecipeCategory extends AbstractRecipeCategory<RecipeHolder<GtRecipe>> {
    private final GtRecipeMap map;

    public GtRecipeCategory(IRecipeHolderType<GtRecipe> type, GtRecipeMap map, IGuiHelper gui, ItemLike icon) {
        super(type, Component.translatable("jei.iis." + map.id), gui.createDrawableItemLike(icon), 150, 64);
        this.map = map;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<GtRecipe> holder, IFocusGroup focuses) {
        GtRecipe r = holder.value();
        List<SizedIngredient> in = r.inputs();
        for (int i = 0; i < in.size(); i++) {
            SizedIngredient s = in.get(i);
            int x = map == GtRecipeMap.PRIMITIVE_BLAST ? 4 : 4 + 18 * i, y = map == GtRecipeMap.PRIMITIVE_BLAST ? 1 + 18 * i : 19;
            builder.addInputSlot(x, y).setStandardSlotBackground()
                    .addItemStacks(s.ingredient().items().map((Holder<Item> h) -> new ItemStack(h, s.count())).toList());
        }
        List<ChanceOutput> out = r.outputs();
        for (int i = 0; i < out.size(); i++) {
            ChanceOutput o = out.get(i);
            IRecipeSlotBuilder slot = builder.addOutputSlot(90 + 18 * i, 19).setOutputSlotBackground().add(o.stack());
            if (o.chance() < ChanceOutput.ALWAYS) {
                String pct = String.format(java.util.Locale.ROOT, "%.2f", o.chance() / 100.0);
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.iis.chance", pct).withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<GtRecipe> holder, IFocusGroup focuses) {
        GtRecipe r = holder.value();
        builder.addAnimatedRecipeArrowWidget(Math.max(20, r.duration() / 4)).setPosition(62, 19);
        String seconds = String.format(java.util.Locale.ROOT, "%.1f", r.duration() / 20.0);
        if (map == GtRecipeMap.PRIMITIVE_BLAST) {
            builder.addText(Component.translatable("jei.iis.duration", seconds), 90, 10).setPosition(62, 46);
        } else {
            builder.addText(List.of(Component.translatable("jei.iis.eut", r.eut(), 2 * r.eut()),
                    Component.translatable("jei.iis.duration", seconds)), 150, 20).setPosition(0, 42);
        }
    }
}
