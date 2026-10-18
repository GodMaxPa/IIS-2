package ru.iis.machine.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Рецепт машины GT: несколько входов с количеством, выходы с шансами, длительность и EU/t.
 * Входы не привязаны к слотам: как в GT5, ингредиенты ищутся во всех входных слотах.
 */
public final class GtRecipe implements Recipe<GtRecipeInput> {
    private final GtRecipeMap map;
    private final List<SizedIngredient> inputs;
    private final List<ChanceOutput> outputs;
    private final int duration;
    private final int eut;

    public GtRecipe(GtRecipeMap map, List<SizedIngredient> inputs, List<ChanceOutput> outputs, int duration, int eut) {
        this.map = map;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.duration = duration;
        this.eut = eut;
    }

    public GtRecipeMap map() { return map; }
    public List<SizedIngredient> inputs() { return inputs; }
    public List<ChanceOutput> outputs() { return outputs; }
    public int duration() { return duration; }
    public int eut() { return eut; }

    static MapCodec<GtRecipe> codec(GtRecipeMap map) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SizedIngredient.NESTED_CODEC.listOf(1, map.maxInputs).fieldOf("inputs").forGetter(GtRecipe::inputs),
                ChanceOutput.CODEC.listOf(1, map.maxOutputs).fieldOf("outputs").forGetter(GtRecipe::outputs),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration").forGetter(GtRecipe::duration),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("eut", 0).forGetter(GtRecipe::eut)
        ).apply(i, (in, out, d, e) -> new GtRecipe(map, in, out, d, e)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, GtRecipe> streamCodec(GtRecipeMap map) {
        return StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), GtRecipe::inputs,
                ChanceOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), GtRecipe::outputs,
                ByteBufCodecs.VAR_INT, GtRecipe::duration,
                ByteBufCodecs.VAR_INT, GtRecipe::eut,
                (in, out, d, e) -> new GtRecipe(map, in, out, d, e));
    }

    /**
     * Распределить ингредиенты по слотам. Возвращает, сколько забрать из каждого слота, или null, если не хватает.
     */
    public int[] allocate(List<ItemStack> slots) {
        int[] left = new int[slots.size()];
        for (int s = 0; s < slots.size(); s++) left[s] = slots.get(s).getCount();
        int[] take = new int[slots.size()];
        for (SizedIngredient ing : inputs) {
            int need = ing.count();
            for (int s = 0; s < slots.size() && need > 0; s++) {
                ItemStack st = slots.get(s);
                if (left[s] <= 0 || st.isEmpty() || !ing.ingredient().test(st)) continue;
                int t = Math.min(need, left[s]);
                left[s] -= t;
                take[s] += t;
                need -= t;
            }
            if (need > 0) return null;
        }
        return take;
    }

    /** Выходы с учётом шансов (бросок на каждый). */
    public List<ItemStack> rollOutputs(RandomSource random) {
        List<ItemStack> r = new ArrayList<>(outputs.size());
        for (ChanceOutput o : outputs) {
            r.add(o.chance() >= ChanceOutput.ALWAYS || random.nextInt(ChanceOutput.ALWAYS) < o.chance() ? o.create() : ItemStack.EMPTY);
        }
        return r;
    }

    @Override
    public boolean matches(GtRecipeInput input, Level level) {
        return allocate(input.stacks()) != null;
    }

    @Override
    public ItemStack assemble(GtRecipeInput input) {
        return outputs.getFirst().create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<GtRecipe> getSerializer() {
        return GtRecipes.serializer(map);
    }

    @Override
    public RecipeType<GtRecipe> getType() {
        return GtRecipes.type(map);
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
