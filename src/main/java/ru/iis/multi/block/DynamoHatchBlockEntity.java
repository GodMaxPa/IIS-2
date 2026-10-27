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
 * Динамо-люк (GT_MetaTileEntity_Hatch_Dynamo, D89): выход V[mTier] на 1 А только спереди, запас 512 + 2 × V[mTier + 1]
 * (LV: 32 EU/t, 768 EU; MV: 128 EU/t, 1536 EU), отдаёт, когда в запасе больше 512. Генератор кладёт энергию, пока запас
 * не полон (BaseMetaTileEntity#increaseStoredEnergyUnits(aEU, false)): последняя порция может выйти за край.
 */
public class DynamoHatchBlockEntity extends ElectricBlockEntity {
    public static final long MIN_STORED = 512;

    public DynamoHatchBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof HatchBlock h && h.type.dynamo() ? h.type.voltage : HatchType.DYNAMO.voltage);
    }

    private DynamoHatchBlockEntity(BlockPos pos, BlockState state, long voltage) {
        super(MultiContent.DYNAMO_HATCH_BE.get(), pos, state, 0, MIN_STORED + voltage * 4 * 2, 0);
    }

    /** GT maxEUOutput: V[mTier]. */
    public long voltage() {
        return outputVoltage();
    }

    /** BaseMetaTileEntity#increaseStoredEnergyUnits(aEU, false): только пока запас не полон. */
    public boolean inject(long eu) {
        if (energy.stored >= energy.capacity) return false;
        energy.stored += eu;
        setChanged();
        return true;
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
        return getBlockState().getBlock() instanceof HatchBlock h && h.type.dynamo() ? h.type.voltage : HatchType.DYNAMO.voltage;
    }

    @Override
    public long outputAmperes() {
        return 1;
    }

    @Override
    public long minimumStored() {
        return MIN_STORED;
    }

    @Override
    protected void work(ServerLevel level) {}

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
