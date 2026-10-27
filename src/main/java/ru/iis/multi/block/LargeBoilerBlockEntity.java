package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.FluidContainers;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.Fuels;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.logic.LargeBoilerLogic;
import ru.iis.multi.menu.ControllerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Большой котёл (GT_MetaTileEntity_LargeBoiler, D88). Форма 3×5×3, контроллер — в середине передней стороны нижнего слоя:
 *  - низ: топки (не меньше 3) или люки: обслуживание, входные люки (вода, жидкое топливо) и шины (твёрдое), глушители;
 *  - слои 2–5: полые корпуса котла (не меньше 24) или выходные люки и шины; в середине слоёв 2–4 — трубчатые корпуса.
 * В слоте контроллера — программная схема: 1–25 снижает выход пара на 50 л/т за единицу. Расчёты — {@link LargeBoilerLogic}.
 */
public class LargeBoilerBlockEntity extends MultiblockControllerBlockEntity {
    /** Блоки одного уровня: корпус, трубчатый корпус, топка. */
    public record Casings(Block casing, Block pipe, Block firebox) {}

    private final LargeBoilerLogic logic;
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int generated;

    public LargeBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.LARGE_BOILER_BE.get(), pos, state);
        this.logic = new LargeBoilerLogic(state.getBlock() instanceof LargeBoilerBlock b ? b.tier : LargeBoilerLogic.Tier.BRONZE);
    }

    public LargeBoilerLogic logic() {
        return logic;
    }

    private Casings casings() {
        return MultiContent.boilerCasings(logic.tier);
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    /** GT checkMachine. */
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        Casings c = casings();
        Direction back = getBlockState().getValue(MachineBlock.FACING).getOpposite();
        BlockPos center = worldPosition.relative(back);
        int casingCount = 0, fireboxCount = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos col = center.offset(i, 0, j);
                if (i != 0 || j != 0) {
                    for (int k = 1; k <= 4; k++) {
                        BlockPos p = col.above(k);
                        if (addOutput(sl, p)) continue;
                        if (!sl.getBlockState(p).is(c.casing())) return false;
                        casingCount++;
                    }
                } else {
                    for (int k = 1; k <= 3; k++) if (!sl.getBlockState(col.above(k)).is(c.pipe())) return false;
                    BlockPos top = col.above(4);
                    if (!addOutput(sl, top)) {
                        if (!sl.getBlockState(top).is(c.casing())) return false;
                        casingCount++;
                    }
                }
            }
        }
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos p = center.offset(i, 0, j);
                if (p.equals(worldPosition)) continue;
                HatchType t = hatchAt(sl, p);
                if (t == HatchType.MAINTENANCE) parts.maintenance.add(p);
                else if (t == HatchType.INPUT_HATCH) parts.inputHatches.add(p);
                else if (t == HatchType.INPUT_BUS) parts.inputBuses.add(p);
                else if (t == HatchType.MUFFLER) parts.mufflers.add(p);
                else if (sl.getBlockState(p).is(c.firebox())) fireboxCount++;
                else return false;
            }
        }
        return casingCount >= 24 && fireboxCount >= 3;
    }

    /** GT addOutputToMachineList: выходной люк или шина. */
    private boolean addOutput(ServerLevel sl, BlockPos p) {
        HatchType t = hatchAt(sl, p);
        if (t == HatchType.OUTPUT_HATCH) parts.outputHatches.add(p);
        else if (t == HatchType.OUTPUT_BUS) parts.outputBuses.add(p);
        else return false;
        return true;
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    private IntPredicate oxygen(ServerLevel sl) {
        return amount -> depleteInput(sl, GtFluids.get("oxygen").source().get(), amount);
    }

    /** GT checkRecipe: запас → дизельное → полужидкое → твёрдое топливо → остаток запаса. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        ItemStack slot = items.get(0);
        logic.readCircuit(ProgrammedCircuitItem.config(slot), slot.getItem() instanceof ProgrammedCircuitItem);
        int max = maintenance.maxEfficiency();
        IntPredicate oxygen = oxygen(sl);
        pendingItems = List.of();
        if (logic.fromStash(efficiency, max, oxygen)) return started();
        logic.solidSuperFuel = false;
        for (Fluid f : hatchFluids(sl)) {
            int special = Fuels.power("diesel", f);
            if (special > 1 && depleteInput(sl, f, LargeBoilerLogic.LIQUID_PORTION)) {
                logic.fromDiesel(special, efficiency, max, oxygen);
                return started();
            }
        }
        for (Fluid f : hatchFluids(sl)) {
            int special = Fuels.power("dense", f);
            if (special > 0 && depleteInput(sl, f, LargeBoilerLogic.LIQUID_PORTION)) {
                logic.fromDense(special, efficiency, max, oxygen);
                return started();
            }
        }
        for (ItemStack st : inputSlots(sl)) {
            if (st.isEmpty()) continue;
            int burn = sl.fuelValues().burnDuration(st);
            logic.solidSuperFuel = burn / LargeBoilerLogic.FUEL_DIVIDER >= LargeBoilerLogic.SUPER_FUEL;
            if (burn / LargeBoilerLogic.FUEL_DIVIDER > 0 && !fluidContainer(st)) {
                var tpl = st.getCraftingRemainder();
                ItemStack remainder = tpl == null ? ItemStack.EMPTY : tpl.create();
                st.shrink(logic.fromSolid(burn, st.getCount(), efficiency, max, oxygen));
                pendingItems = remainder.isEmpty() ? List.of() : List.of(remainder);
                inputBusesChanged(sl);
                return started();
            }
        }
        if (logic.fromLeftover(efficiency, max, oxygen)) return started();
        maxProgress = 0;
        eut = 0;
        return false;
    }

    /** GT getFluidForFilledItem != null: вёдра и капсулы с жидкостью котёл не жжёт (лава — только в котлах на лаве). */
    private static boolean fluidContainer(ItemStack st) {
        if (st.getItem() instanceof BucketItem b && b.getContent() != Fluids.EMPTY) return true;
        var c = st.get(FluidContainers.CONTENT.get());
        return c != null && c.amount() > 0;
    }

    private List<Fluid> hatchFluids(ServerLevel sl) {
        List<Fluid> l = new ArrayList<>();
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0) {
                Fluid f = h.tank().resource().getFluid();
                if (!l.contains(f)) l.add(f);
            }
        }
        return l;
    }

    private boolean started() {
        maxProgress = logic.maxProgress;
        eut = logic.eut;
        efficiencyIncrease = logic.efficiencyIncrease;
        progress = 0;
        pendingFluids = List.of();
        setChanged();
        return true;
    }

    /** GT onRunningTick + polluteEnvironment: пар в выходные люки, вода из входных; нет воды — взрыв. */
    @Override
    protected boolean runningTick(ServerLevel sl) {
        generated = 0;
        if (eut > 0) {
            efficiency = Math.max(0, Math.min(efficiency, maintenance.maxEfficiency()));
            int steam = logic.steamPerTick(efficiency);
            if (steam > 0) {
                int water = logic.waterFor(steam);
                if (depleteInput(sl, GtFluids.get("distilled_water").source().get(), water) || depleteInput(sl, Fluids.WATER, water)) {
                    addFluidOutput(sl, IISFluids.STEAM.get(), steam);
                    generated = steam;
                } else {
                    explodeMultiblock(sl);
                    return false;
                }
            }
        }
        if (!polluteEnvironment(sl, logic.pollutionPerTick())) {
            stop(true);
            return false;
        }
        return true;
    }

    /** GT: простаивающий котёл ищет топливо раз в 100 тиков и остывает на 10 % за раз. */
    @Override
    protected int idleInterval() {
        return 100;
    }

    @Override
    protected void stop(boolean disable) {
        super.stop(disable);
        generated = 0;
    }

    // --- GUI и сохранение ---------------------------------------------------------------------------------------------
    public final ContainerData boilerData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 10 -> generated;
                case 11 -> logic.progressTimeStash;
                case 12 -> logic.circuitConfig;
                case 13 -> logic.oxygenBoost ? 1 : 0;
                default -> data.get(i);
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return ControllerMenu.BOILER_DATA;
        }
    };

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.boiler(id, inventory, this, boilerData);
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
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putFloat("water", logic.water);
        out.putInt("excessProjectedEU", logic.excessProjectedEU);
        out.putInt("progressTimeStash", logic.progressTimeStash);
        out.putInt("excessFuel", logic.excessFuel);
        out.putInt("lastFuelEfficiencyIncrease", logic.lastFuelEfficiencyIncrease);
        out.putInt("circuitConfig", logic.circuitConfig);
        out.putBoolean("oxygenBoost", logic.oxygenBoost);
        out.putBoolean("solidSuperFuel", logic.solidSuperFuel);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        logic.water = in.getFloatOr("water", 0);
        logic.excessProjectedEU = in.getIntOr("excessProjectedEU", 0);
        logic.progressTimeStash = in.getIntOr("progressTimeStash", 0);
        logic.excessFuel = in.getIntOr("excessFuel", 0);
        logic.lastFuelEfficiencyIncrease = in.getIntOr("lastFuelEfficiencyIncrease", 0);
        logic.circuitConfig = in.getIntOr("circuitConfig", 0);
        logic.oxygenBoost = in.getBooleanOr("oxygenBoost", false);
        logic.solidSuperFuel = in.getBooleanOr("solidSuperFuel", false);
    }
}
