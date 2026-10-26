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
 * Энергетический люк (GT_MetaTileEntity_Hatch_Energy): вход V[mTier] до 2 А только спереди, запас 512 + 8 × V
 * (LV: 32 EU/t, 768 EU; MV: 128 EU/t, 1536 EU). Машина снимает с люка энергию целиком за тик (нужно, чтобы в одном люке хватало).
 */
public class EnergyHatchBlockEntity extends ElectricBlockEntity {
    public EnergyHatchBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof HatchBlock h && h.type.energy() ? h.type.voltage : HatchType.ENERGY.voltage);
    }

    private EnergyHatchBlockEntity(BlockPos pos, BlockState state, long voltage) {
        super(MultiContent.ENERGY_HATCH_BE.get(), pos, state, 0, 512 + voltage * 8, voltage);
        energy.maxAmperesIn = 2;
    }

    /** GT maxEUInput: V[mTier]. */
    public long voltage() {
        return energy.inputVoltage;
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
