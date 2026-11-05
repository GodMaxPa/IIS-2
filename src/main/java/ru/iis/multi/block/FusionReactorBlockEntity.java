package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import ru.iis.lv.LvContent;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.ControllerMenu;

import java.util.List;
import java.util.Set;

/**
 * Термоядерный реактор Mark I (GT_MetaTileEntity_FusionComputer, _FusionComputer1).
 * Особенности GT: нет обслуживания и поломок; запас контроллера 160003000 EU × люки / 16, каждый люк отдаёт в него до 2048 EU за тик;
 * зажигание разом снимает со запаса mSpecialValue (в рецепте — temp), дальше EU/t рецепта; следующая порция без перерыва зажигания не платит;
 * простаивающий реактор ищет рецепт раз в 100 тиков.
 * Отличия: центр кольца за спиной контроллера, а не перед лицом (иначе лицо смотрит внутрь кольца); входы тратятся, только если запаса хватает на зажигание: в GT они сгорали впустую.
 */
public class FusionReactorBlockEntity extends RecipeMultiblockBlockEntity {
    public static final long STORE_MARK1 = 160003000L, HATCH_INPUT = 2048;
    public static final int RING = 5, HATCH_TIER = 6, IDLE_CHECK = 100;

    private long stored;
    private int recheck;

    public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.FUSION_BE.get(), pos, state);
    }

    @Override
    protected GtRecipeMap recipeMap() {
        return GtRecipeMap.FUSION;
    }

    public long stored() {
        return stored;
    }

    public void setStored(long eu) {
        stored = eu;
    }

    /** GT maxEUStore. */
    public long capacity() {
        return STORE_MARK1 * Math.min(16, parts.energy.size()) / 16;
    }

    /** На экране в поле нагрева: запас, тысячи EU. */
    @Override
    public int heat() {
        return (int) (stored / 1000);
    }

    /** Центр кольца. */
    public BlockPos center() {
        return worldPosition.relative(getBlockState().getValue(MachineBlock.FACING).getOpposite(), RING);
    }

    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        BlockPos c = center();
        Block casing = LvContent.CASING_LUV.get(), coil = MultiContent.COIL_SUPERCONDUCTOR.get();
        for (int[] d : FusionShape.COILS) if (!sl.getBlockState(c.offset(d[0], d[1], d[2])).is(coil)) return false;
        for (int[] d : FusionShape.CASINGS) {
            BlockPos p = c.offset(d[0], d[1], d[2]);
            if (!p.equals(worldPosition) && !sl.getBlockState(p).is(casing)) return false;
        }
        if (!part(sl, c, FusionShape.ENERGY, Set.of(HatchType.ENERGY_LUV, HatchType.ENERGY_ZPM), parts.energy, casing)) return false;
        if (!part(sl, c, FusionShape.OUTPUT, Set.of(HatchType.OUTPUT_HATCH), parts.outputHatches, casing)) return false;
        if (!part(sl, c, FusionShape.INPUT, Set.of(HatchType.INPUT_HATCH), parts.inputHatches, casing)) return false;
        return !parts.energy.isEmpty() && !parts.outputHatches.isEmpty() && parts.inputHatches.size() >= 2;
    }

    /** GT addIf…: корпус или люк нужного вида уровня LuV и выше. */
    private boolean part(ServerLevel sl, BlockPos c, int[][] cells, Set<HatchType> kinds, List<BlockPos> into, Block casing) {
        for (int[] d : cells) {
            BlockPos p = c.offset(d[0], d[1], d[2]);
            BlockState s = sl.getBlockState(p);
            if (s.is(casing)) continue;
            if (!(s.getBlock() instanceof HatchBlock h)) return false;
            boolean energy = h.type == HatchType.ENERGY_LUV || h.type == HatchType.ENERGY_ZPM;
            if (kinds.contains(h.type) && energy) into.add(p);
            else if (kinds.contains(h.type.kind()) && h.type.tier >= HATCH_TIER) into.add(p);
            else return false;
        }
        return true;
    }

    /** GT onPostTick целиком свой: без обслуживания, с запасом в контроллере. */
    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (--recheck <= 0) {
            recheck = 20;
            formed = checkStructure(sl);
        }
        if (formed) {
            for (BlockPos p : parts.energy) {
                if (sl.getBlockEntity(p) instanceof EnergyHatchBlockEntity e && stored + HATCH_INPUT < capacity() && e.energy.stored >= HATCH_INPUT) {
                    e.energy.stored -= HATCH_INPUT;
                    stored += HATCH_INPUT;
                }
            }
            if (maxProgress > 0) {
                stored -= eut;
                if (stored <= 0) {
                    stored = 0;
                    stop(true);
                } else if (++progress >= maxProgress) {
                    finish(sl);
                    if (allowedToWork) start(sl, false);
                }
            } else if (allowedToWork && sl.getGameTime() % IDLE_CHECK == 0) {
                checkRecipe(sl);
            }
        } else if (maxProgress > 0) {
            stop(false);
        }
        boolean active = maxProgress > 0;
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
    }

    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        return start(sl, true);
    }

    /** ignite: реактор простаивал и должен заплатить за зажигание; иначе плазма ещё горит (GT вызывает checkRecipe без списания). */
    private boolean start(ServerLevel sl, boolean ignite) {
        List<FluidStack> fluids = storedFluids(sl);
        if (fluids.size() < 2) return false;
        for (RecipeHolder<GtRecipe> h : GtRecipes.all(sl.recipeAccess().recipeMap(), GtRecipeMap.FUSION)) {
            GtRecipe r = h.value();
            int[] pick = matchFluids(r, fluids);
            if (pick == null || r.temperature() > capacity() || (ignite && r.temperature() > stored)) continue;
            drainFluids(sl, r, pick);
            if (ignite) stored -= r.temperature();
            eut = r.eut();
            maxProgress = r.duration();
            progress = 0;
            efficiency = 10000;
            efficiencyIncrease = 10000;
            pendingItems = List.of();
            pendingFluids = r.fluidOutputs();
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.fusion(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("FusionStore", stored);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        stored = in.getLongOr("FusionStore", 0L);
    }
}
