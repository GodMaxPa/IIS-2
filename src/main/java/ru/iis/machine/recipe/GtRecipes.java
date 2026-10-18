package ru.iis.machine.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;

import java.util.*;
import java.util.function.IntPredicate;

/** Регистрация типов рецептов GT и поиск рецепта для машины. */
public final class GtRecipes {
    private GtRecipes() {}

    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, IIS.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, IIS.MODID);

    private static final Map<GtRecipeMap, DeferredHolder<RecipeType<?>, RecipeType<GtRecipe>>> TYPE = new EnumMap<>(GtRecipeMap.class);
    private static final Map<GtRecipeMap, DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GtRecipe>>> SERIALIZER = new EnumMap<>(GtRecipeMap.class);

    static {
        for (GtRecipeMap m : GtRecipeMap.values()) {
            TYPE.put(m, TYPES.register(m.id, () -> RecipeType.simple(Identifier.fromNamespaceAndPath(IIS.MODID, m.id))));
            SERIALIZER.put(m, SERIALIZERS.register(m.id, () -> new RecipeSerializer<>(GtRecipe.codec(m), GtRecipe.streamCodec(m))));
        }
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        // Рецепты машин нужны клиенту для просмотрщика рецептов (JEI)
        gameBus.addListener((OnDatapackSyncEvent e) -> e.sendRecipes(TYPE.values().stream().<RecipeType<?>>map(DeferredHolder::get).toList()));
    }

    public static RecipeType<GtRecipe> type(GtRecipeMap map) {
        return TYPE.get(map).get();
    }

    public static RecipeSerializer<GtRecipe> serializer(GtRecipeMap map) {
        return SERIALIZER.get(map).get();
    }

    public static Collection<RecipeHolder<GtRecipe>> all(RecipeMap recipes, GtRecipeMap map) {
        return recipes.byType(type(map));
    }

    /** Найденный рецепт и сколько забрать из каждого входного слота. */
    public record Match(RecipeHolder<GtRecipe> holder, int[] take) {
        public GtRecipe recipe() {
            return holder.value();
        }
    }

    /** Первый подходящий рецепт карты (GT5: первый зарегистрированный подходящий), с ограничением по EU/t. */
    public static Optional<Match> find(ServerLevel level, GtRecipeMap map, List<ItemStack> inputs, IntPredicate eutAllowed) {
        if (inputs.stream().allMatch(ItemStack::isEmpty)) return Optional.empty();
        for (RecipeHolder<GtRecipe> h : all(level.recipeAccess().recipeMap(), map)) {
            GtRecipe r = h.value();
            if (!eutAllowed.test(r.eut())) continue;
            int[] take = r.allocate(inputs);
            if (take != null) return Optional.of(new Match(h, take));
        }
        return Optional.empty();
    }
}
