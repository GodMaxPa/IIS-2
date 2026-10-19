package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.VoltageTier;
import ru.iis.energy.logic.GeneratorLogic;
import ru.iis.lv.Fuels;
import ru.iis.lv.GeneratorType;
import ru.iis.lv.LvContent;
import ru.iis.lv.menu.GeneratorMenu;
import ru.iis.machine.block.FluidTank;
import ru.iis.machine.block.MachineBlock;

/**
 * Генератор LV на жидком топливе (GT_MetaTileEntity_BasicGenerator): бак, раз в 10 тиков сжигает топливо
 * в запас, выдаёт 32 EU/t 1 А через лицевую сторону. Топливо — по трубам с любой стороны, кроме лицевой,
 * или ведром в верхний слот (пустое ведро — в нижний).
 */
public class GeneratorBlockEntity extends ElectricBlockEntity {
    public static final long VOLTAGE = VoltageTier.LV.voltage();
    /** GT_MetaTileEntity_BasicGenerator#getMinimumStoredEU = 512. */
    public static final long MIN_STORED = 512;
    public static final int SLOT_IN = 0, SLOT_OUT = 1;

    private final GeneratorLogic logic = new GeneratorLogic(VOLTAGE, MIN_STORED);
    private final FluidTank tank;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(LvContent.GENERATOR_BE.get(), pos, state, 2, VOLTAGE * 40 + MIN_STORED, 0);
        GeneratorType t = type(state);
        this.tank = new FluidTank(t.tank, f -> Fuels.fuelPower(t, f) > 0, false, this::setChanged);
    }

    private static GeneratorType type(BlockState s) {
        return s.getBlock() instanceof GeneratorBlock b ? b.type : GeneratorType.STEAM_TURBINE;
    }

    public GeneratorType type() {
        return type(getBlockState());
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return false;
    }

    @Override
    public boolean outputsEnergyTo(Direction side) {
        return side == front();
    }

    @Override
    public long outputVoltage() {
        return VOLTAGE;
    }

    @Override
    public long outputAmperes() {
        return 1;
    }

    @Override
    public long minimumStored() {
        return MIN_STORED;
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return side == front() ? null : tank;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    /** EU за операцию и литров за операцию для жидкости. */
    private long[] fuel(Fluid f) {
        GeneratorType t = type();
        int power = Fuels.fuelPower(t, f);
        if (power <= 0) return null;
        if (t == GeneratorType.STEAM_TURBINE) {
            return new long[]{GeneratorLogic.STEAM_TURBINE_EU_PER_OPERATION, GeneratorLogic.steamTurbineLitersPerOperation(1)};
        }
        return new long[]{power * (long) GeneratorLogic.combustionEfficiency(1) / 100, 1};
    }

    @Override
    protected void work(ServerLevel sl) {
        if (sl.getGameTime() % 10 == 0) {
            fillFromBucket();
            if (tank.amount() > 0) {
                long[] f = fuel(tank.resource().getFluid());
                if (f != null) {
                    long used = logic.burn(energy, tank.amount(), f[0], f[1]);
                    if (used > 0) {
                        tank.drainInternal((int) used);
                        setChanged();
                    }
                }
            }
        }
        boolean active = logic.active(energy);
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) sl.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
    }

    /** Ведро с топливом из верхнего слота — в бак, пустое ведро — в нижний слот. */
    private void fillFromBucket() {
        ItemStack in = items.get(SLOT_IN);
        if (!(in.getItem() instanceof BucketItem bucket) || in.is(Items.BUCKET)) return;
        Fluid f = bucket.getContent();
        if (Fuels.fuelPower(type(), f) <= 0) return;
        if (tank.amount() > 0 && !tank.resource().getFluid().isSame(f)) return;
        if (tank.amount() + 1000 > tank.capacity()) return;
        ItemStack out = items.get(SLOT_OUT);
        if (!out.isEmpty() && (!out.is(Items.BUCKET) || out.getCount() >= out.getMaxStackSize())) return;
        tank.setAmount(f, tank.amount() + 1000);
        in.shrink(1);
        if (out.isEmpty()) items.set(SLOT_OUT, new ItemStack(Items.BUCKET));
        else out.grow(1);
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == front() ? new int[0] : new int[]{SLOT_IN, SLOT_OUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == SLOT_IN;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_IN;
    }

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> (int) Math.min(Short.MAX_VALUE, energy.stored);
                case 1 -> (int) Math.min(Short.MAX_VALUE, energy.capacity);
                case 2 -> tank.amount();
                case 3 -> tank.amount() > 0 ? BuiltInRegistries.FLUID.getId(tank.resource().getFluid()) : -1;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 4;
        }
    };

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new GeneratorMenu(id, inventory, this, data, type());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
    }
}
