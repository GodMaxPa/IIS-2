package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.ControllerMenu;

/**
 * Большой теплообменник (GT_MetaTileEntity_HeatExchanger, D105). Форма 3×4×3, контроллер — в середине передней стороны
 * внизу. Средний столб за ним: снизу — люк горячей жидкости, над ним два корпуса с титановыми трубами, сверху — люк
 * остывшей жидкости. Остальное — стабильные титановые корпуса (не меньше 24) или люки: вход дистиллированной воды,
 * выход пара, обслуживание. Раз в секунду берёт лаву (до двух порогов перегрева), выдаёт лаву пахоэхоэ и греет воду:
 * пар каждый тик; если лавы не меньше порога — вдвое меньше, но перегретого. Нет воды — взрыв. Программная схема 1–25
 * в контроллере снижает порог на 150 л/с за ступень ценой 1,5 % выхода. Горячего хладагента IC2 у нас нет (D5).
 */
public class HeatExchangerBlockEntity extends MultiblockControllerBlockEntity {
    public static final int MIN_CASINGS = 24, THRESHOLD = 4000, THRESHOLD_PER_CIRCUIT = 150;
    public static final float PENALTY_PER_CIRCUIT = 0.015f, LAVA_MULTIPLIER = 4f;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private @Nullable BlockPos hotInput, coldOutput;
    private boolean superheated;
    private int threshold, generated;
    private float water;

    public HeatExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.HEAT_EXCHANGER_BE.get(), pos, state);
    }

    public boolean superheated() {
        return superheated;
    }

    /** Пар (или перегретый пар) за последний тик, л. */
    public int generated() {
        return generated;
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        hotInput = coldOutput = null;
        Direction facing = getBlockState().getValue(MachineBlock.FACING), back = facing.getOpposite(), lateral = facing.getClockWise();
        BlockPos c = worldPosition.relative(back);
        int casings = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = 0; k <= 3; k++) {
                    BlockPos p = c.relative(lateral, i).relative(back, j).above(k);
                    if (i == 0 && j == 0) {
                        HatchType t = hatchAt(sl, p);
                        if (k == 0) {
                            if (t != HatchType.INPUT_HATCH) return false;
                            hotInput = p;
                        } else if (k == 3) {
                            if (t != HatchType.OUTPUT_HATCH) return false;
                            coldOutput = p;
                        } else if (!sl.getBlockState(p).is(MultiContent.TITANIUM_PIPE_CASING.get())) {
                            return false;
                        }
                        continue;
                    }
                    if (p.equals(worldPosition) || addToMachineList(sl, p)) continue;
                    if (!sl.getBlockState(p).is(MultiContent.STABLE_TITANIUM_CASING.get())) return false;
                    casings++;
                }
            }
        }
        return casings >= MIN_CASINGS;
    }

    /** GT addOutputToMachineList, addInputToMachineList, addMaintenanceToMachineList. */
    private boolean addToMachineList(ServerLevel sl, BlockPos p) {
        HatchType t = hatchAt(sl, p);
        if (t == null) return false;
        switch (t) {
            case MAINTENANCE -> parts.maintenance.add(p);
            case INPUT_HATCH -> parts.inputHatches.add(p);
            case INPUT_BUS -> parts.inputBuses.add(p);
            case OUTPUT_HATCH -> parts.outputHatches.add(p);
            case OUTPUT_BUS -> parts.outputBuses.add(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    @Override
    protected int idleInterval() {
        return 100;
    }

    /** GT checkRecipe: раз в 20 тиков забрать лаву, отдать лаву пахоэхоэ, задать «EU/t» пара. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        if (hotInput == null || coldOutput == null || !(sl.getBlockEntity(hotInput) instanceof FluidHatchBlockEntity hot)
                || hot.tank().amount() <= 0) return false;
        int amount = hot.tank().amount();
        threshold = THRESHOLD;
        float eff = 1f;
        ItemStack circuit = items.get(0);
        if (circuit.getItem() instanceof ProgrammedCircuitItem) {
            int config = ProgrammedCircuitItem.config(circuit);
            if (config >= 1 && config <= 25) {
                eff -= (config - 1) * PENALTY_PER_CIRCUIT;
                threshold -= THRESHOLD_PER_CIRCUIT * (config - 1);
            }
        }
        if (!hot.tank().resource().getFluid().isSame(Fluids.LAVA)) return false;
        threshold /= 4;
        superheated = amount >= threshold;
        int consume = Math.min(amount, threshold * 2);
        hot.tank().drainInternal(consume);
        hot.setChanged();
        if (sl.getBlockEntity(coldOutput) instanceof FluidHatchBlockEntity cold) {
            cold.fillInternal(GtFluids.get("pahoehoe_lava").source().get(), consume);
        }
        maxProgress = 20;
        progress = 0;
        eut = (int) (consume * LAVA_MULTIPLIER * eff);
        efficiencyIncrease = 80;
        pendingItems = java.util.List.of();
        pendingFluids = java.util.List.of();
        setChanged();
        return true;
    }

    /** GT onRunningTick: пар = EU/t × 2 × КПД; перегретого — вдвое меньше; на 160 л — 1 л дистиллированной воды, нет её — взрыв. */
    @Override
    protected boolean runningTick(ServerLevel sl) {
        generated = 0;
        if (eut <= 0) return true;
        int steam = (int) ((long) eut * 2L * efficiency / 10000L);
        if (steam <= 0) return true;
        if (superheated) steam /= 2;
        water += steam / 160f;
        int distilled = (int) water;
        water -= distilled;
        if (!depleteInput(sl, GtFluids.get("distilled_water").source().get(), distilled)) {
            explodeMultiblock(sl);
            return false;
        }
        addFluidOutput(sl, superheated ? GtFluids.get("superheated_steam").source().get() : IISFluids.STEAM.get(), steam);
        generated = steam;
        return true;
    }

    // --- GUI и сохранение ---------------------------------------------------------------------------------------------
    public final ContainerData exchangerData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 10 -> generated;
                case 11 -> superheated ? 1 : 0;
                case 12 -> threshold;
                case 13 -> items.get(0).getItem() instanceof ProgrammedCircuitItem ? ProgrammedCircuitItem.config(items.get(0)) : 0;
                default -> data.get(i);
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return ControllerMenu.HEAT_EXCHANGER_DATA;
        }
    };

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.heatExchanger(id, inventory, this, exchangerData);
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
        return stack.getItem() instanceof ProgrammedCircuitItem;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putBoolean("superheated", superheated);
        out.putFloat("water", water);
        out.putInt("threshold", threshold);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        superheated = in.getBooleanOr("superheated", false);
        water = in.getFloatOr("water", 0);
        threshold = in.getIntOr("threshold", 0);
    }
}
