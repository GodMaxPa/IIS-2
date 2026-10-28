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

/** Категория JEI для одной карты рецептов GT (D101: и одного уровня напряжения — tier, null у карт без EU). */
public class GtRecipeCategory extends AbstractRecipeCategory<RecipeHolder<GtRecipe>> {
    private final GtRecipeMap map;

    public GtRecipeCategory(IRecipeHolderType<GtRecipe> type, GtRecipeMap map, @org.jspecify.annotations.Nullable String tier, IGuiHelper gui, ItemLike icon) {
        super(type, tier == null ? Component.translatable("jei.iis." + map.id)
                : Component.translatable("jei.iis.tiered", Component.translatable("jei.iis." + map.id), tier), gui.createDrawableItemLike(icon), 150, 78);
        this.map = map;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<GtRecipe> holder, IFocusGroup focuses) {
        GtRecipe r = holder.value();
        List<SizedIngredient> in = r.inputs();
        for (int i = 0; i < in.size(); i++) {
            SizedIngredient s = in.get(i);
            boolean grid = in.size() > 3;
            int x = map == GtRecipeMap.PRIMITIVE_BLAST ? 4 : grid ? 4 + 18 * (i % 3) : 4 + 18 * i;
            int y = map == GtRecipeMap.PRIMITIVE_BLAST ? 1 + 18 * i : grid ? 10 + 18 * (i / 3) : 19;
            builder.addInputSlot(x, y).setStandardSlotBackground()
                    .addItemStacks(s.ingredient().items().map((Holder<Item> h) -> new ItemStack(h, s.count())).toList());
        }
        // программная схема: над стрелкой, не расходуется (D75)
        if (r.circuit() >= 0) {
            builder.addInputSlot(62, 1).setStandardSlotBackground()
                    .add(ru.iis.lv.item.ProgrammedCircuitItem.withConfig(new ItemStack(ru.iis.lv.LvContent.PROGRAMMED_CIRCUIT.get()), r.circuit()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.iis.circuit_not_consumed").withStyle(ChatFormatting.GRAY)));
        }
        // формы отвердителя: тоже над стрелкой, не расходуются (D76)
        for (int k = 0; k < r.keep().size(); k++) {
            builder.addInputSlot(62 - 18 * (k + (r.circuit() >= 0 ? 1 : 0)), 1).setStandardSlotBackground()
                    .addItemStacks(r.keep().get(k).items().map((Holder<Item> h) -> new ItemStack(h)).toList())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.iis.circuit_not_consumed").withStyle(ChatFormatting.GRAY)));
        }
        // жидкости: вход под стрелкой, выход под выходами
        for (var f : r.fluidInputs()) {
            var slot = builder.addInputSlot(62, 41).setStandardSlotBackground();
            for (var h : f.ingredient().fluids()) slot.add(h.value(), (long) f.amount());
        }
        for (var f : r.fluidOutputs()) {
            builder.addOutputSlot(90, 41).setOutputSlotBackground().add(f.fluid(), (long) f.amount());
        }
        List<ChanceOutput> out = r.outputs();
        for (int i = 0; i < out.size(); i++) {
            ChanceOutput o = out.get(i);
            // больше трёх выходов (центрифуга 6, просеиватель 9) — сетка 3 в ряд
            int ox = out.size() > 3 ? 90 + 18 * (i % 3) : 90 + 18 * i;
            int oy = out.size() > 6 ? 1 + 18 * (i / 3) : out.size() > 3 ? 10 + 18 * (i / 3) : 19;
            IRecipeSlotBuilder slot = builder.addOutputSlot(ox, oy).setOutputSlotBackground().add(o.stack());
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
        if (map == GtRecipeMap.BLAST) {
            builder.addText(List.of(Component.translatable("jei.iis.eut", r.eut(), 2 * r.eut()),
                    Component.translatable("jei.iis.duration", seconds), Component.translatable("jei.iis.temp", r.temperature())), 150, 30).setPosition(0, 46);
        } else if (map == GtRecipeMap.PRIMITIVE_BLAST || map == GtRecipeMap.COKE_OVEN) {
            builder.addText(Component.translatable("jei.iis.duration", seconds), 90, 10).setPosition(62, 46);
        } else {
            builder.addText(List.of(Component.translatable("jei.iis.eut", r.eut(), 2 * r.eut()),
                    Component.translatable("jei.iis.duration", seconds)), 150, 20).setPosition(0, 58);
        }
    }
}
