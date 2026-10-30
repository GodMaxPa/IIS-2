package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.FluidOutput;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.menu.EbfMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Многоблок GT5-II, работающий по карте рецептов (D104): предметы из входных шин, жидкости из входных люков (каждая
 * жидкость рецепта — из своего люка, где её хватает), результат — в выходные шины и люки. Форму, карту, разгон и загрязнение
 * задаёт наследник.
 */
public abstract class RecipeMultiblockBlockEntity extends MultiblockControllerBlockEntity {
    protected RecipeMultiblockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Карта рецептов. */
    protected abstract GtRecipeMap recipeMap();

    /** GT calculateOverclockedNessMulti: разгон по напряжению люков (у имплозионного компрессора — нет). */
    protected boolean overclocks() {
        return true;
    }

    /** GT getPollutionPerTick. */
    protected int pollutionPerTick() {
        return 0;
    }

    /** Длительность после разгона (пиролизная печь ускоряется катушками). */
    protected int adjustDuration(int duration) {
        return duration;
    }

    @Override
    protected boolean runningTick(ServerLevel sl) {
        if (!super.runningTick(sl)) return false;
        if (pollutionPerTick() > 0 && !polluteEnvironment(sl, pollutionPerTick())) {
            stop(true);
            return false;
        }
        return true;
    }

    // --- форма ------------------------------------------------------------------------------------------------------
    protected Direction back() {
        return getBlockState().getValue(MachineBlock.FACING).getOpposite();
    }

    /**
     * GT add*ToMachineList: люк разрешённого вида — в {@link #parts}; true, если это люк такого вида. Не люк — false, тогда
     * клетка проверяется как корпус.
     */
    protected boolean addPart(ServerLevel sl, BlockPos p, Set<HatchType> allowed) {
        HatchType t = hatchAt(sl, p);
        if (t == null || !allowed.contains(t.energy() && !t.dynamo() ? HatchType.ENERGY : t)) return false;
        switch (t) {
            case MAINTENANCE -> parts.maintenance.add(p);
            case INPUT_BUS -> parts.inputBuses.add(p);
            case OUTPUT_BUS -> parts.outputBuses.add(p);
            case INPUT_HATCH -> parts.inputHatches.add(p);
            case OUTPUT_HATCH -> parts.outputHatches.add(p);
            case MUFFLER -> parts.mufflers.add(p);
            default -> {
                if (t.energy() && !t.dynamo()) parts.energy.add(p);
                else return false;
            }
        }
        return true;
    }

    protected static final Set<HatchType> ALL_IO = Set.of(HatchType.MAINTENANCE, HatchType.INPUT_BUS, HatchType.OUTPUT_BUS, HatchType.INPUT_HATCH,
            HatchType.OUTPUT_HATCH, HatchType.ENERGY);

    protected boolean is(ServerLevel sl, BlockPos p, Block b) {
        return sl.getBlockState(p).is(b);
    }

    /** Общие требования GT к форме: люк обслуживания и хотя бы один энергетический люк. */
    protected boolean basicParts() {
        return !parts.maintenance.isEmpty() && !parts.energy.isEmpty();
    }

    // --- рецепт -----------------------------------------------------------------------------------------------------
    /** Сколько жидкости каждого вида во входных люках (GT getStoredFluids). */
    protected List<FluidStack> storedFluids(ServerLevel sl) {
        List<FluidStack> out = new ArrayList<>();
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0) {
                out.add(new FluidStack(h.tank().resource().getFluid(), h.tank().amount()));
            }
        }
        return out;
    }

    /** Для каждой жидкости рецепта — люк, где её хватает; null — не хватает. */
    protected int @Nullable [] matchFluids(GtRecipe r, List<FluidStack> stored) {
        int[] pick = new int[r.fluidInputs().size()];
        boolean[] used = new boolean[stored.size()];
        for (int k = 0; k < pick.length; k++) {
            SizedFluidIngredient f = r.fluidInputs().get(k);
            pick[k] = -1;
            for (int i = 0; i < stored.size(); i++) {
                if (used[i] || !f.ingredient().test(stored.get(i)) || stored.get(i).getAmount() < f.amount()) continue;
                pick[k] = i;
                used[i] = true;
                break;
            }
            if (pick[k] < 0) return null;
        }
        return pick;
    }

    protected void drainFluids(ServerLevel sl, GtRecipe r, int[] pick) {
        List<FluidHatchBlockEntity> hatches = new ArrayList<>();
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0) hatches.add(h);
        }
        for (int k = 0; k < pick.length; k++) hatches.get(pick[k]).tank().drainInternal(r.fluidInputs().get(k).amount());
    }

    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        int tier = BlastLogic.machineTier(maxInputVoltage(sl));
        long vmax = BlastLogic.V[tier];
        List<ItemStack> slots = inputSlots(sl);
        List<FluidStack> fluids = storedFluids(sl);
        if (slots.stream().allMatch(ItemStack::isEmpty) && fluids.isEmpty()) return false;
        for (RecipeHolder<GtRecipe> h : GtRecipes.all(sl.recipeAccess().recipeMap(), recipeMap())) {
            GtRecipe r = h.value();
            if (r.eut() > vmax) continue;
            int[] take = r.allocate(slots);
            if (take == null) continue;
            int[] pick = matchFluids(r, fluids);
            if (pick == null) continue;
            for (int i = 0; i < take.length; i++) if (take[i] > 0) slots.get(i).shrink(take[i]);
            inputBusesChanged(sl);
            drainFluids(sl, r, pick);
            start(sl, r, tier);
            return true;
        }
        return false;
    }

    protected void start(ServerLevel sl, GtRecipe r, int tier) {
        efficiency = maintenance.maxEfficiency();
        efficiencyIncrease = 10000;
        if (overclocks()) {
            BlastLogic.Run run = BlastLogic.overclock(r.eut(), r.duration(), tier, 0, 0);
            eut = run.eut();
            maxProgress = adjustDuration(run.duration());
        } else {
            eut = r.eut();
            maxProgress = adjustDuration(Math.max(1, r.duration()));
        }
        progress = 0;
        pendingItems = r.rollOutputs(sl.getRandom());
        pendingFluids = new ArrayList<>(r.fluidOutputs());
        setChanged();
    }

    @Override
    protected void outputFluids(ServerLevel sl, List<FluidOutput> fluids) {
        for (FluidOutput f : fluids) addFluidOutput(sl, f.fluid(), f.amount());
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return EbfMenu.freezer(id, inventory, data);
    }
}
