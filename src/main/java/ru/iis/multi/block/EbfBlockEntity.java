package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.recipe.GtRecipe;
import ru.iis.machine.recipe.GtRecipeMap;
import ru.iis.machine.recipe.GtRecipes;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.menu.EbfMenu;

import java.util.List;

/**
 * Электрическая доменная печь (GT_MetaTileEntity_ElectricBlastFurnace + GT_MetaTileEntity_MultiBlockBase).
 *
 * Форма 3×3×4, контроллер — в середине нижнего слоя лицом наружу:
 *  - низ (кроме контроллера): теплоустойчивые корпуса или люки (энергия, входные шины/люки, выходные шины, обслуживание);
 *  - слои 2–3: по 8 катушек одного вида вокруг пустой середины;
 *  - верх: в центре выхлопной люк, вокруг — корпуса или выходные люки жидкости.
 */
public class EbfBlockEntity extends MultiblockControllerBlockEntity {
    public static final int CUPRONICKEL_HEAT = 1800;
    public static final int KANTHAL_HEAT = 2700;
    public static final int NICHROME_HEAT = 3600;
    public static final int TUNGSTENSTEEL_HEAT = 4500;
    /** D112: GT checkMachine: HSS-G 5400, наквада 7200. */
    public static final int HSSG_HEAT = 5400;
    public static final int NAQUADAH_HEAT = 7200;

    private int heat;

    public EbfBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.EBF_BE.get(), pos, state);
    }

    @Override
    public int heat() {
        return heat;
    }

    // --- форма ----------------------------------------------------------------------------------------------------
    private boolean isCasing(ServerLevel sl, BlockPos p) {
        return sl.getBlockState(p).is(MultiContent.HEAT_PROOF_CASING.get());
    }

    /** GT checkMachine. */
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        heat = 0;
        Direction back = getBlockState().getValue(MachineBlock.FACING).getOpposite();
        BlockPos center = worldPosition.relative(back);
        if (!sl.getBlockState(center.above(1)).isAir() || !sl.getBlockState(center.above(2)).isAir()) return false;
        BlockPos muffler = center.above(3);
        if (hatchAt(sl, muffler) != HatchType.MUFFLER) return false;
        parts.mufflers.add(muffler);
        // GT checkMachine: the coil east of the centre sets the type, all 16 coils must be the same
        net.minecraft.world.level.block.Block coil = sl.getBlockState(center.above(2).east()).getBlock();
        int coilHeat = coilHeat(coil);
        if (coilHeat == 0) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos c = center.offset(dx, 0, dz);
                if (!sl.getBlockState(c.above(1)).is(coil)) return false;
                if (!sl.getBlockState(c.above(2)).is(coil)) return false;
                BlockPos top = c.above(3);
                HatchType t = hatchAt(sl, top);
                if (t == HatchType.OUTPUT_HATCH) parts.outputHatches.add(top);
                else if (!isCasing(sl, top)) return false;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos b = center.offset(dx, 0, dz);
                if (b.equals(worldPosition)) continue;
                HatchType t = hatchAt(sl, b);
                if (t == null) {
                    if (!isCasing(sl, b)) return false;
                    continue;
                }
                switch (t) {
                    case MAINTENANCE -> parts.maintenance.add(b);
                    case INPUT_BUS -> parts.inputBuses.add(b);
                    case INPUT_HATCH -> parts.inputHatches.add(b);
                    case ENERGY, ENERGY_MV, ENERGY_HV, ENERGY_EV, ENERGY_IV, ENERGY_LUV, ENERGY_ZPM -> parts.energy.add(b);
                    case OUTPUT_BUS -> parts.outputBuses.add(b);
                    default -> {
                        return false;
                    }
                }
            }
        }
        heat = coilHeat;
        return true;
    }

    /** GT mHeatingCapacity by coil meta: cupronickel 1800, kanthal 2700 (D93), nichrome 3600 (D98), tungstensteel 4500 (D103); 0 — not a coil. */
    public static int coilHeat(net.minecraft.world.level.block.Block b) {
        if (b == MultiContent.COIL_CUPRONICKEL.get()) return CUPRONICKEL_HEAT;
        if (b == MultiContent.COIL_KANTHAL.get()) return KANTHAL_HEAT;
        if (b == MultiContent.COIL_NICHROME.get()) return NICHROME_HEAT;
        if (b == MultiContent.COIL_TUNGSTENSTEEL.get()) return TUNGSTENSTEEL_HEAT;
        if (b == MultiContent.COIL_HSSG.get()) return HSSG_HEAT;
        if (b == MultiContent.COIL_NAQUADAH.get()) return NAQUADAH_HEAT;
        return 0;
    }

    // --- работа ------------------------------------------------------------------------------------------------------
    /** GT onRunningTick + polluteEnvironment. */
    @Override
    protected boolean runningTick(ServerLevel sl) {
        if (!super.runningTick(sl)) return false;
        if (!polluteEnvironment(sl, BlastLogic.POLLUTION_PER_TICK)) {
            stop(true);
            return false;
        }
        return true;
    }

    /** GT checkRecipe. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        List<ItemStack> slots = inputSlots(sl);
        if (slots.stream().allMatch(ItemStack::isEmpty)) return false;
        FluidHatchBlockEntity fluidHatch = null;
        FluidStack fluid = FluidStack.EMPTY;
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0) {
                fluidHatch = h;
                fluid = h.fluid();
                break;
            }
        }
        int tier = BlastLogic.machineTier(maxInputVoltage(sl));
        long vmax = BlastLogic.V[tier];
        for (RecipeHolder<GtRecipe> h : GtRecipes.all(sl.recipeAccess().recipeMap(), GtRecipeMap.BLAST)) {
            GtRecipe r = h.value();
            if (r.eut() > vmax || r.temperature() > heat || !r.fluidMatches(fluid)) continue;
            int[] take = r.allocate(slots);
            if (take == null) continue;
            for (int s = 0; s < take.length; s++) if (take[s] > 0) slots.get(s).shrink(take[s]);
            for (var f : r.fluidInputs()) if (fluidHatch != null) fluidHatch.tank().drainInternal(f.amount());
            inputBusesChanged(sl);
            efficiency = maintenance.maxEfficiency();
            efficiencyIncrease = 10000;
            BlastLogic.Run run = BlastLogic.overclock(r.eut(), r.duration(), tier, heat, r.temperature());
            eut = run.eut();
            maxProgress = run.duration();
            progress = 0;
            pendingItems = r.rollOutputs(sl.getRandom());
            pendingFluids = r.fluidOutputs();
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new EbfMenu(id, inventory, data);
    }
}
