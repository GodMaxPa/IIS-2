package ru.iis.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.lv.block.ElectricBlockEntity;

/** Панель: 1 EU/t, когда над ней небо и нет ни ночи, ни дождя (IC2). */
public class SolarPanelBlockEntity extends ElectricBlockEntity {
    public static final long VOLTAGE = 8;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorContent.SOLAR_BE.get(), pos, state, 0, 64, 0);
    }

    public static boolean sunlit(ServerLevel sl, BlockPos pos) {
        return sl.canSeeSky(pos.above()) && sl.isBrightOutside() && !sl.isRaining() && !sl.isThundering();
    }

    @Override
    protected void work(ServerLevel sl) {
        if (sunlit(sl, worldPosition)) energy.stored = Math.min(energy.capacity, energy.stored + 1);
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return false;
    }

    @Override
    public boolean outputsEnergyTo(Direction side) {
        return side != Direction.UP;
    }

    @Override
    public long outputVoltage() {
        return VOLTAGE;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, net.minecraft.world.item.ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, net.minecraft.world.item.ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return null;
    }
}
