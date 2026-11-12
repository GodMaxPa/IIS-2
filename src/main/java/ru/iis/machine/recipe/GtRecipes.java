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

    /** Рецепты карты в порядке выбора: кэш на набор рецептов игры (после /reload набор новый). */
    private static final Map<RecipeMap, Map<GtRecipeMap, List<RecipeHolder<GtRecipe>>>> ORDERED = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Рецепты карты, самые требовательные первыми (D150): все, кто перебирает их «до первого подходящего», берут рецепт,
     * которому нужно больше всего из лежащего в слотах. Иначе рецепт «4 стержня → рама» навсегда закрывал бы любой рецепт
     * с четырьмя такими же стержнями и чем-то ещё. При равенстве — по id, как отдаёт игра.
     */
    public static Collection<RecipeHolder<GtRecipe>> all(RecipeMap recipes, GtRecipeMap map) {
        return ORDERED.computeIfAbsent(recipes, k -> Collections.synchronizedMap(new EnumMap<>(GtRecipeMap.class))).computeIfAbsent(map, m -> {
            List<RecipeHolder<GtRecipe>> list = new ArrayList<>(recipes.byType(type(m)));
            list.sort(Comparator.comparing((RecipeHolder<GtRecipe> h) -> specificity(h.value()), Arrays::compare).reversed());
            return List.copyOf(list);
        });
    }

    /** Мера требовательности рецепта: сколько разных входов (с неизрасходуемыми и схемой), жидкостей, предметов и мБ. */
    public static long[] specificity(GtRecipe r) {
        long items = 0, mb = 0;
        for (var i : r.inputs()) items += i.count();
        for (var f : r.fluidInputs()) mb += f.amount();
        return new long[] {r.inputs().size() + r.keep().size() + (r.circuit() >= 0 ? 1 : 0), r.fluidInputs().size(), items, mb};
    }

    /** Используется ли жидкость во входах рецептов карты (GT: машина принимает только такие жидкости). */
    public static boolean acceptsFluid(ServerLevel level, GtRecipeMap map, net.minecraft.world.level.material.Fluid fluid) {
        net.neoforged.neoforge.fluids.FluidStack probe = new net.neoforged.neoforge.fluids.FluidStack(fluid, 1);
        for (RecipeHolder<GtRecipe> h : all(level.recipeAccess().recipeMap(), map)) {
            for (var f : h.value().fluidInputs()) if (f.ingredient().test(probe)) return true;
        }
        return false;
    }

    /** Найденный рецепт и сколько забрать из каждого входного слота. */
    public record Match(RecipeHolder<GtRecipe> holder, int[] take) {
        public GtRecipe recipe() {
            return holder.value();
        }
    }

    /** Первый подходящий рецепт карты (GT5: первый зарегистрированный подходящий), с ограничением по EU/t. */
    public static Optional<Match> find(ServerLevel level, GtRecipeMap map, List<ItemStack> inputs, IntPredicate eutAllowed) {
        return find(level, map, inputs, null, eutAllowed);
    }

    /** То же с входным баком жидкости (миксер, промывка, сборщик). */
    public static Optional<Match> find(ServerLevel level, GtRecipeMap map, List<ItemStack> inputs,
                                       net.neoforged.neoforge.fluids.@org.jspecify.annotations.Nullable FluidStack tank, IntPredicate eutAllowed) {
        boolean noItems = inputs.stream().allMatch(ItemStack::isEmpty);
        if (noItems && (tank == null || tank.isEmpty())) return Optional.empty();
        for (RecipeHolder<GtRecipe> h : all(level.recipeAccess().recipeMap(), map)) {
            GtRecipe r = h.value();
            if (!eutAllowed.test(r.eut())) continue;
            if (!r.fluidMatches(tank)) continue;
            if (r.inputs().isEmpty() && noItems && r.fluidInputs().isEmpty()) continue;
            int[] take = r.allocate(inputs);
            if (take != null) return Optional.of(new Match(h, take));
        }
        return Optional.empty();
    }
}
