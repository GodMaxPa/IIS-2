package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlock;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.FluidOutput;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.menu.ControllerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Обрабатывающий массив (GT_MetaTileEntity_ProcessingArray, D105): куб 3×3×3, пустой внутри, контроллер — в середине стенки.
 * Не меньше 16 прочных вольфрамостальных корпусов, остальное — люки (обслуживание, входы, выходы, энергия). В слот
 * контроллера кладут до 16 одноблочных машин одного вида: массив работает по их карте рецептов и с их напряжением,
 * выполняя рецепт столько раз сразу, сколько машин (и входов) хватает. Разгон — по уровню машин, как в GT: рецепты до
 * 16 EU/t ×4 энергии и ÷2 времени за уровень сверх LV, остальные — ×4 / ÷2, пока EU/t не больше V[T−1].
 * Из жидкостей результата, как в GT, выходит только первая (× число машин). Электропечи (без карты рецептов) и
 * разрушители камня не годятся.
 */
public class ProcessingArrayBlockEntity extends RecipeMultiblockBlockEntity {
    public static final int MIN_CASINGS = 16, MAX_MACHINES = 16;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int parallel;

    public ProcessingArrayBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.PROCESSING_ARRAY_BE.get(), pos, state);
    }

    /** Машина в слоте контроллера (GT isCorrectMachinePart: одноблочная машина GT с картой рецептов). */
    public static @Nullable LvMachineType machine(ItemStack stack) {
        // разрушитель камня берёт лаву и воду из соседних блоков — в массиве (как в GT, где его рецепты показные) не работает
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ElectricMachineBlock m && m.type.recipeMap != null
                && m.type.recipeMap != GtRecipeMap.ROCK_BREAKER) return m.type;
        return null;
    }

    public @Nullable LvMachineType machine() {
        return machine(items.get(0));
    }

    /** Сколько машин сработало в текущем цикле. */
    public int parallel() {
        return parallel;
    }

    @Override
    protected GtRecipeMap recipeMap() {
        LvMachineType m = machine();
        return m == null ? null : m.recipeMap;
    }

    @Override
    protected boolean correctMachinePart() {
        return machine() != null;
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        Direction back = back();
        BlockPos c = worldPosition.relative(back);
        if (!sl.getBlockState(c).isAir()) return false;
        int casings = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (p.equals(c) || p.equals(worldPosition)) continue;
                    if (addPart(sl, p, ALL_IO)) continue;
                    if (!is(sl, p, MultiContent.ROBUST_TUNGSTENSTEEL_CASING.get())) return false;
                    casings++;
                }
            }
        }
        return casings >= MIN_CASINGS && basicParts();
    }

    // --- рецепт -------------------------------------------------------------------------------------------------------
    /** GT checkRecipe массива: найти рецепт по напряжению машин и выполнить его до {@link #MAX_MACHINES} раз сразу. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        LvMachineType m = machine();
        if (m == null) return false;
        int tier = BlastLogic.machineTier(m.voltage());
        long vmax = BlastLogic.V[tier];
        int machines = Math.min(MAX_MACHINES, items.get(0).getCount());
        List<ItemStack> slots = inputSlots(sl);
        List<FluidStack> fluids = storedFluids(sl);
        if (slots.stream().allMatch(ItemStack::isEmpty) && fluids.isEmpty()) return false;
        for (RecipeHolder<GtRecipe> h : GtRecipes.all(sl.recipeAccess().recipeMap(), m.recipeMap)) {
            GtRecipe r = h.value();
            if (r.eut() > vmax || r.allocate(slots) == null || matchFluids(r, fluids) == null) continue;
            int clean = r.cleanroom() ? CleanroomBlockEntity.cleanlinessAt(sl, worldPosition) : 10000;
            if (clean <= 0) continue;
            int done = 0;
            List<ItemStack> outs = new ArrayList<>();
            while (done < machines) {
                int[] take = r.allocate(slots);
                int[] pick = take == null ? null : matchFluids(r, storedFluids(sl));
                if (pick == null) break;
                for (int i = 0; i < take.length; i++) if (take[i] > 0) slots.get(i).shrink(take[i]);
                drainFluids(sl, r, pick);
                outs.addAll(r.cleanroom() ? CleanroomBlockEntity.spoil(r.rollOutputs(sl.getRandom()), clean, sl.getRandom()) : r.rollOutputs(sl.getRandom()));
                done++;
            }
            inputBusesChanged(sl);
            start(sl, r, tier, done, outs);
            return true;
        }
        return false;
    }

    private void start(ServerLevel sl, GtRecipe r, int tier, int done, List<ItemStack> outs) {
        long e = r.eut();
        int d = r.duration();
        if (e <= 16) {
            e = e * (1L << (tier - 1)) * (1L << (tier - 1));
            d = d / (1 << (tier - 1));
        } else {
            while (e <= BlastLogic.V[tier - 1]) {
                e *= 4;
                d /= 2;
            }
        }
        parallel = done;
        eut = (int) (e * done);
        maxProgress = Math.max(1, d);
        efficiency = maintenance.maxEfficiency();
        efficiencyIncrease = 10000;
        progress = 0;
        pendingItems = outs;
        pendingFluids = r.fluidOutputs().isEmpty() ? List.of()
                : List.of(new FluidOutput(r.fluidOutputs().get(0).fluid(), r.fluidOutputs().get(0).amount() * done));
        setChanged();
    }

    // --- GUI и сохранение ---------------------------------------------------------------------------------------------
    public final ContainerData arrayData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 10 -> parallel;
                case 11 -> machine() == null ? 0 : 1;
                default -> data.get(i);
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return ControllerMenu.ARRAY_DATA;
        }
    };

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.processingArray(id, inventory, this, arrayData);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return machine(stack) != null;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("parallel", parallel);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        parallel = in.getIntOr("parallel", 0);
    }
}
