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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.IISFluids;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.TurbineRotorItem;
import ru.iis.multi.logic.LargeTurbineLogic;
import ru.iis.multi.menu.ControllerMenu;

/**
 * Большая паровая турбина (GT_MetaTileEntity_LargeTurbine_Steam, D89). Форма 3×3×4, контроллер — в середине передней
 * стороны; перед ним 3×3 воздуха (место для вращения), внутри за ним — два блока воздуха. Стенки — турбинные корпуса;
 * в серединах сторон второго и третьего слоёв можно ставить люки (вход пара, выход дистиллированной воды, обслуживание);
 * в середине задней стороны — динамо-люк. В слоте контроллера — ротор. Расчёты — {@link LargeTurbineLogic}.
 */
public class LargeTurbineBlockEntity extends MultiblockControllerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int optFlow, baseEff, storedFluid;
    private float water;

    public LargeTurbineBlockEntity(BlockPos pos, BlockState state) {
        this(MultiContent.LARGE_TURBINE_BE.get(), pos, state);
    }

    protected LargeTurbineBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Корпус турбины (у газовой — стальной турбинный корпус из нержавейки, D104). */
    protected net.minecraft.world.level.block.Block casing() {
        return MultiContent.TURBINE_CASING.get();
    }

    public ItemStack rotor() {
        return items.get(0);
    }

    public int eut() {
        return eut;
    }

    /** Отдача в динамо-люк за тик: EU/t × «скорость» (GT onRunningTick). */
    public long output() {
        return (long) eut * efficiency / 10000;
    }

    // --- форма --------------------------------------------------------------------------------------------------------
    /** GT checkMachine. */
    @Override
    boolean checkStructure(ServerLevel sl) {
        parts.clear();
        Direction facing = getBlockState().getValue(MachineBlock.FACING), back = facing.getOpposite();
        if (!sl.getBlockState(worldPosition.relative(back, 1)).isAir() || !sl.getBlockState(worldPosition.relative(back, 2)).isAir()) return false;
        int air = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) if (sl.getBlockState(worldPosition.offset(dx, dy, dz)).isAir()) air++;
        if (air != 10) return false;
        // соседняя турбина с той же стороной лица вплотную (через 2 блока) — нельзя: у них были бы общие корпуса
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (sl.getBlockEntity(worldPosition.relative(d, 2)) instanceof LargeTurbineBlockEntity t
                    && t.getBlockState().getValue(MachineBlock.FACING) == facing) return false;
        }
        Direction lateral = facing.getClockWise();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                for (int k = 0; k < 4; k++) {
                    BlockPos p = worldPosition.relative(lateral, i).above(j).relative(back, k);
                    if (sl.getBlockState(p).is(casing())) continue;
                    if (!((i == 0 || j == 0) && (k == 1 || k == 2)) || !addToMachineList(sl, p)) return false;
                }
            }
        }
        BlockPos dynamo = worldPosition.relative(back, 3);
        HatchType t = hatchAt(sl, dynamo);
        if (t == null || !t.dynamo()) return false;
        parts.dynamos.add(dynamo);
        return true;
    }

    /** GT addToMachineList: обслуживание, вход (люк или шина), выход (люк или шина), глушитель. */
    private boolean addToMachineList(ServerLevel sl, BlockPos p) {
        HatchType t = hatchAt(sl, p);
        if (t == null) return false;
        switch (t) {
            case MAINTENANCE -> parts.maintenance.add(p);
            case INPUT_HATCH -> parts.inputHatches.add(p);
            case INPUT_BUS -> parts.inputBuses.add(p);
            case OUTPUT_HATCH -> parts.outputHatches.add(p);
            case OUTPUT_BUS -> parts.outputBuses.add(p);
            case MUFFLER -> parts.mufflers.add(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    @Override
    protected boolean correctMachinePart() {
        return rotor().getItem() instanceof TurbineRotorItem;
    }

    @Override
    protected int idleInterval() {
        return 100;
    }

    /** GT checkRecipe + fluidIntoPower: пар из входных люков (до 125 % оптимума), вода в выходные, выход стремится к мощности потока. */
    @Override
    protected boolean checkRecipe(ServerLevel sl) {
        ItemStack rotor = rotor();
        if (!(rotor.getItem() instanceof TurbineRotorItem r) || TurbineRotorItem.material(rotor) == null) return false;
        optFlow = r.optimalFlow(rotor);
        baseEff = r.baseEfficiency(rotor);
        eut = LargeTurbineLogic.approach(eut, targetPower(sl));
        if (eut <= 0) {
            efficiency = 0;
            maxProgress = 0;
            return false;
        }
        maxProgress = 1;
        progress = 0;
        efficiencyIncrease = 10;
        pendingItems = java.util.List.of();
        pendingFluids = java.util.List.of();
        setChanged();
        return true;
    }

    /** GT fluidIntoPower паровой турбины: пар до 125 % оптимума, на каждые 160 л — 1 л дистиллированной воды. */
    protected int targetPower(ServerLevel sl) {
        int remaining = LargeTurbineLogic.flowLimit(optFlow), total = 0;
        for (BlockPos p : parts.inputHatches) {
            if (remaining <= 0) break;
            if (!(sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) || h.tank().amount() <= 0
                    || !h.tank().resource().getFluid().isSame(IISFluids.STEAM.get())) continue;
            int flow = Math.min(h.tank().amount(), remaining);
            h.tank().drainInternal(flow);
            storedFluid = h.tank().amount();
            remaining -= flow;
            total += flow;
        }
        water += total / 160.0f;
        int distilled = (int) water;
        water -= distilled;
        if (distilled > 0) addFluidOutput(sl, GtFluids.get("distilled_water").source().get(), distilled);
        return LargeTurbineLogic.steamPower(total, optFlow, baseEff);
    }

    protected int optFlow() {
        return optFlow;
    }

    protected int baseEff() {
        return baseEff;
    }

    protected void setStoredFluid(int amount) {
        storedFluid = amount;
    }

    /**
     * GT onRunningTick → addEnergyOutputMultipleDynamos: EU/t × «скорость» в динамо-люк по амперу V; если люк не способен
     * принять столько (V × 1 А меньше выхода), машина взрывается.
     */
    @Override
    protected boolean runningTick(ServerLevel sl) {
        long out = output();
        if (out <= 0 || parts.dynamos.isEmpty()) return true;
        long capacity = 0;
        for (BlockPos p : parts.dynamos) if (sl.getBlockEntity(p) instanceof DynamoHatchBlockEntity d) capacity += d.voltage();
        if (capacity < out) {
            explodeMultiblock(sl);
            return false;
        }
        long injected = 0;
        for (BlockPos p : parts.dynamos) {
            if (!(sl.getBlockEntity(p) instanceof DynamoHatchBlockEntity d)) continue;
            long left = out - injected, v = d.voltage();
            long amps = Math.min(1, left / v), remainder = left - amps * v;
            for (int a = 0; a < amps; a++) d.inject(v);
            injected += v * amps;
            if (remainder > 0 && amps < 1) {
                d.inject(remainder);
                injected += remainder;
            }
        }
        return true;
    }

    /** GT doRandomMaintenanceDamage: раз в 1000 тиков работы с шансом 1/2 ротор изнашивается; сломанный пропадает. */
    @Override
    protected void componentWear(ServerLevel sl) {
        ItemStack rotor = rotor();
        if (!(rotor.getItem() instanceof TurbineRotorItem) || sl.getRandom().nextInt(2) != 0) return;
        long damage = rotor.getDamageValue() + LargeTurbineLogic.wear(eut);
        if (damage >= rotor.getMaxDamage()) items.set(0, ItemStack.EMPTY);
        else rotor.setDamageValue((int) damage);
        setChanged();
    }

    // --- GUI и сохранение ---------------------------------------------------------------------------------------------
    public final ContainerData turbineData = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 10 -> (int) output();
                case 11 -> LargeTurbineLogic.shownOptimalFlow(optFlow);
                case 12 -> storedFluid;
                case 13 -> rotor().getItem() instanceof TurbineRotorItem
                        ? LargeTurbineLogic.damagePercent(rotor().getDamageValue(), rotor().getMaxDamage()) : 0;
                case 14 -> rotor().getItem() instanceof TurbineRotorItem ? 1 : 0;
                default -> data.get(i);
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return ControllerMenu.TURBINE_DATA;
        }
    };

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ControllerMenu.turbine(id, inventory, this, turbineData);
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
        return stack.getItem() instanceof TurbineRotorItem;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putFloat("water", water);
        out.putInt("storedFluid", storedFluid);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        water = in.getFloatOr("water", 0);
        storedFluid = in.getIntOr("storedFluid", 0);
    }
}
