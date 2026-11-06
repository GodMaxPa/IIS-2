package ru.iis.lv.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.LvContent;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;

import java.util.function.Consumer;

/** Флешка (GT Tool_DataStick, Behaviour_DataStick, D110): хранит чертёж сборочной линии, записанный сканером. */
public class DataStickItem extends Item {
    /** Какой рецепт сборочной линии записан (GT копирует весь рецепт в NBT, у нас хватает ссылки) и что он даёт — для подсказки. */
    public record Research(Identifier recipe, Identifier output) {
        public static final Codec<Research> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("recipe").forGetter(Research::recipe),
                Identifier.CODEC.fieldOf("output").forGetter(Research::output)).apply(i, Research::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Research> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Research::recipe, Identifier.STREAM_CODEC, Research::output, Research::new);
    }

    public DataStickItem(Properties properties) {
        super(properties);
    }

    /** Флешка с чертежом этого рецепта. */
    public static ItemStack written(RecipeHolder<GtRecipe> h) {
        ItemStack s = new ItemStack(LvContent.DATA_STICK.get());
        Identifier out = h.value().outputs().isEmpty() ? h.id().identifier() : BuiltInRegistries.ITEM.getKey(h.value().outputs().getFirst().create().getItem());
        s.set(LvContent.RESEARCH.get(), new Research(h.id().identifier(), out));
        return s;
    }

    /** Рецепт с флешки; null — она пуста или такого рецепта больше нет. */
    public static @Nullable GtRecipe recipe(ServerLevel sl, ItemStack stick) {
        Research r = stick.get(LvContent.RESEARCH.get());
        if (r == null) return null;
        for (RecipeHolder<GtRecipe> h : GtRecipes.all(sl.recipeAccess().recipeMap(), GtRecipeMap.ASSEMBLY_LINE)) {
            if (h.id().identifier().equals(r.recipe())) return h.value();
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Prospection p = stack.get(LvContent.PROSPECTION.get());
        if (p != null) {
            builder.accept(Component.translatable(p.analyzed() ? "item.iis.prospection.analyzed" : "item.iis.prospection.raw").withStyle(net.minecraft.ChatFormatting.GRAY));
            if (p.analyzed()) p.report().forEach(c -> builder.accept(c.copy().withStyle(net.minecraft.ChatFormatting.GRAY)));
            return;
        }
        Research r = stack.get(LvContent.RESEARCH.get());
        if (r == null) {
            builder.accept(Component.translatable("item.iis.data_stick.empty").withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        Item out = BuiltInRegistries.ITEM.getValue(r.output());
        builder.accept(Component.translatable("item.iis.data_stick.research", new ItemStack(out).getHoverName()).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
