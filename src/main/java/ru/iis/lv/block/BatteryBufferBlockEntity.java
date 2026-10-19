package ru.iis.lv.block;

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
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvContent;
import ru.iis.lv.item.BatteryItem;
import ru.iis.lv.menu.BatteryBufferMenu;

/**
 * Аккумуляторный буфер LV (GT_MetaTileEntity_BasicBatteryBuffer).
 * Запас V×64×слотов, минимум V×16×слотов; приём — 2 А на каждую заряжаемую батарейку со всех сторон, кроме лицевой;
 * выдача — 1 А на батарейку через лицевую. Запас выше 2/3 — батарейки заряжаются, ниже 1/3 — разряжаются в запас
 * (по 32 EU/t на батарейку).
 */
public class BatteryBufferBlockEntity extends ElectricBlockEntity {
    public static final long VOLTAGE = VoltageTier.LV.voltage();
    private int batteries;

    public BatteryBufferBlockEntity(BlockPos pos, BlockState state) {
        super(LvContent.BATTERY_BUFFER_BE.get(), pos, state, slots(state), VOLTAGE * 64 * slots(state), VOLTAGE);
    }

    private static int slots(BlockState s) {
        return s.getBlock() instanceof BatteryBufferBlock b ? b.slots : 1;
    }

    public int slots() {
        return items.size();
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
        return VOLTAGE;
    }

    @Override
    public long outputAmperes() {
        return batteries;
    }

    @Override
    public long minimumStored() {
        return VOLTAGE * 16 * items.size();
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    protected void work(ServerLevel sl) {
        boolean charge = energy.stored / 2 > energy.capacity / 3;
        boolean discharge = energy.stored < energy.capacity / 3;
        batteries = 0;
        for (ItemStack s : items) {
            if (!(s.getItem() instanceof BatteryItem b) || b.tier > 1) continue;
            batteries++;
            if (charge) {
                long d = b.charge(s, energy.stored);
                energy.stored -= d;
            } else if (discharge && energy.stored < energy.capacity) {
                long d = b.discharge(s, Math.min(VOLTAGE, energy.capacity - energy.stored));
                energy.stored += d;
            }
        }
        energy.maxAmperesIn = batteries * 2L;
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] all = new int[items.size()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
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
                case 0 -> (int) Math.min(Short.MAX_VALUE, energy.stored);
                case 1 -> (int) Math.min(Short.MAX_VALUE, energy.capacity);
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
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new BatteryBufferMenu(id, inventory, this, data, items.size());
    }
}
