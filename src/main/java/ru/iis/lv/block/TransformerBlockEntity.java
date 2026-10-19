package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvContent;

/**
 * Трансформатор LV (GT_MetaTileEntity_Transformer). Понижающий (по умолчанию): вход MV 128 EU/t 1 А через лицевую
 * сторону, выход LV 32 EU/t 4 А через остальные пять. Повышающий: вход LV 4 А через пять сторон, выход MV 1 А через лицевую.
 * Запас 512 + 2 × V(MV), минимальный запас 512. Без GUI.
 */
public class TransformerBlockEntity extends ElectricBlockEntity {
    public static final long LOW = VoltageTier.LV.voltage(), HIGH = VoltageTier.MV.voltage();

    public TransformerBlockEntity(BlockPos pos, BlockState state) {
        super(LvContent.TRANSFORMER_BE.get(), pos, state, 0, 512 + HIGH * 2, HIGH);
    }

    public boolean stepUp() {
        return getBlockState().getValue(TransformerBlock.STEP_UP);
    }

    private boolean isInputSide(Direction side) {
        return stepUp() ? side != front() : side == front();
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return isInputSide(side);
    }

    @Override
    public boolean outputsEnergyTo(Direction side) {
        return !isInputSide(side);
    }

    @Override
    public long outputVoltage() {
        return stepUp() ? HIGH : LOW;
    }

    @Override
    public long outputAmperes() {
        return stepUp() ? 1 : HIGH / LOW;
    }

    @Override
    public long minimumStored() {
        return 512;
    }

    @Override
    protected void work(ServerLevel level) {
        configure();
    }

    private void configure() {
        energy.inputVoltage = stepUp() ? LOW : HIGH;
        energy.maxAmperesIn = stepUp() ? HIGH / LOW : 1;
    }

    @Override
    public long injectEnergy(Direction from, long voltage, long amperes) {
        configure();
        return super.injectEnergy(from, voltage, amperes);
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
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return null;
    }
}
