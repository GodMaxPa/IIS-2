package ru.iis.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.lv.item.BatteryItem;
import ru.iis.lv.menu.BatteryBufferMenu;

/**
 * Энергохранилище: приём со всех сторон, кроме лицевой, выдача 1 А через лицевую. Верхний ряд окна заряжает предметы из запаса,
 * нижний разряжает батареи в запас. Хранит в отличие от буфера GT всё сам, батареи для этого не нужны.
 */
public class EnergyStorageBlockEntity extends ElectricBlockEntity {
    private final long voltage;
    private final int tier;

    public EnergyStorageBlockEntity(BlockPos pos, BlockState state) {
        super(GearContent.STORAGE_BE.get(), pos, state, 4, block(state).capacity, block(state).tier.voltage());
        this.voltage = block(state).tier.voltage();
        this.tier = block(state).tier.tier();
        energy.maxAmperesIn = 4;
    }

    private static EnergyStorageBlock block(BlockState s) {
        return (EnergyStorageBlock) s.getBlock();
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return side != front();
    }

    @Override
    public boolean outputsEnergyTo(Direction side) {
        return side == front();
    }

    @Override
    public long outputVoltage() {
        return voltage;
    }

    @Override
    public long outputAmperes() {
        return 1;
    }

    @Override
    protected void work(ServerLevel sl) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = items.get(i);
            if (!(s.getItem() instanceof BatteryItem b) || b.tier > tier) continue;
            if (i < 2) {
                energy.stored -= b.charge(s, Math.min(energy.stored, voltage * 4));
            } else if (b.isBattery() && energy.stored < energy.capacity) {
                energy.stored += b.discharge(s, Math.min(voltage * 4, energy.capacity - energy.stored));
            }
        }
        setChanged();
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? new int[]{2, 3} : new int[]{0, 1};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof BatteryItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> (int) (energy.stored & 0xFFFF);
                case 1 -> (int) ((energy.stored >> 16) & 0xFFFF);
                case 2 -> (int) (energy.capacity & 0xFFFF);
                case 3 -> (int) ((energy.capacity >> 16) & 0xFFFF);
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
        return new BatteryBufferMenu(id, inventory, this, data, 4);
    }
}
