package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.FluidTank;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.FluidHatchMenu;

/**
 * Входной/выходной люк жидкости LV, 16000 л. Входной заливается трубами или ведром (верхний слот),
 * выходной отдаёт трубам или наливает в пустое ведро.
 */
public class FluidHatchBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MachineBlock.TickingMachine {
    public static final int SLOT_IN = 0, SLOT_OUT = 1;
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final FluidTank tank;

    public FluidHatchBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.FLUID_HATCH_BE.get(), pos, state);
        boolean input = isInput(state);
        this.tank = new FluidTank(HatchType.TANK, f -> input, !input, this::setChanged);
    }

    private static boolean isInput(BlockState s) {
        return s.getBlock() instanceof HatchBlock h && h.type == HatchType.INPUT_HATCH;
    }

    public boolean isInput() {
        return isInput(getBlockState());
    }

    public FluidTank tank() {
        return tank;
    }

    public FluidStack fluid() {
        return tank.amount() > 0 ? new FluidStack(tank.resource().getFluid(), tank.amount()) : FluidStack.EMPTY;
    }

    /** Добавить жидкость (выход машины); вернуть, сколько поместилось. */
    public int fillInternal(Fluid f, int amount) {
        if (tank.amount() > 0 && !tank.resource().getFluid().isSame(f)) return 0;
        int put = Math.min(amount, tank.capacity() - tank.amount());
        if (put > 0) tank.setAmount(f, tank.amount() + put);
        return put;
    }

    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return tank;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    public void serverTick() {
        ItemStack in = items.get(SLOT_IN);
        ItemStack out = items.get(SLOT_OUT);
        if (in.isEmpty() || !out.isEmpty()) return;
        if (isInput() && in.getItem() instanceof BucketItem b && !in.is(Items.BUCKET) && b.getContent() != Fluids.EMPTY) {
            Fluid f = b.getContent();
            if ((tank.amount() == 0 || tank.resource().getFluid().isSame(f)) && tank.amount() + 1000 <= tank.capacity()) {
                tank.setAmount(f, tank.amount() + 1000);
                in.shrink(1);
                items.set(SLOT_OUT, new ItemStack(Items.BUCKET));
                setChanged();
            }
        } else if (!isInput() && in.is(Items.BUCKET) && tank.amount() >= 1000) {
            var bucket = tank.resource().getFluid().getBucket();
            if (bucket != Items.AIR) {
                tank.drainInternal(1000);
                in.shrink(1);
                items.set(SLOT_OUT, new ItemStack(bucket));
                setChanged();
            }
        }
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{SLOT_IN, SLOT_OUT};
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
                case 0 -> tank.amount();
                case 1 -> tank.amount() > 0 ? BuiltInRegistries.FLUID.getId(tank.resource().getFluid()) : -1;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 2;
        }
    };

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
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
        return items.size();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new FluidHatchMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        tank.serialize(out.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        tank.deserialize(in.childOrEmpty("tank"));
    }
}
