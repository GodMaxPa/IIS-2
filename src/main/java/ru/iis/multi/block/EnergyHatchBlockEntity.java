package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.multi.MultiContent;

/**
 * Энергетический люк LV (GT_MetaTileEntity_Hatch_Energy): вход 32 EU/t до 2 А только спереди,
 * запас 512 + 8 × 32 = 768 EU. Машина снимает с люка энергию целиком за тик (нужно, чтобы в одном люке хватало).
 */
public class EnergyHatchBlockEntity extends ElectricBlockEntity {
    public static final long VOLTAGE = 32;

    public EnergyHatchBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.ENERGY_HATCH_BE.get(), pos, state, 0, 512 + VOLTAGE * 8, VOLTAGE);
        energy.maxAmperesIn = 2;
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return side == front();
    }

    @Override
    protected void work(ServerLevel level) {
        energy.maxAmperesIn = 2;
    }

    /** BaseMetaTileEntity#decreaseStoredEnergyUnits(aEU, false): только если хватает целиком. */
    public boolean drain(long eu) {
        if (energy.stored < eu) return false;
        energy.stored -= eu;
        setChanged();
        return true;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return null;
    }
}
