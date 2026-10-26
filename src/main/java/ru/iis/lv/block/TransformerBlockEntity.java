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
 * Трансформатор (GT_MetaTileEntity_Transformer, mTier = нижний уровень). Понижающий (по умолчанию): вход V[mTier + 1] 1 А
 * через лицевую сторону, выход V[mTier] 4 А через остальные пять (LV: 128 → 32, MV: 512 → 128). Повышающий: вход V[mTier]
 * 4 А через пять сторон, выход V[mTier + 1] 1 А через лицевую. Запас 512 + 2 × V[mTier + 1], минимальный запас 512. Без GUI.
 */
public class TransformerBlockEntity extends ElectricBlockEntity {
    public final long low, high;

    public TransformerBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof TransformerBlock b ? b.low : VoltageTier.LV);
    }

    private TransformerBlockEntity(BlockPos pos, BlockState state, VoltageTier tier) {
        super(LvContent.TRANSFORMER_BE.get(), pos, state, 0, 512 + VoltageTier.byTier(tier.tier() + 1).voltage() * 2,
                VoltageTier.byTier(tier.tier() + 1).voltage());
        this.low = tier.voltage();
        this.high = VoltageTier.byTier(tier.tier() + 1).voltage();
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
        return stepUp() ? high : low;
    }

    @Override
    public long outputAmperes() {
        return stepUp() ? 1 : high / low;
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
        energy.inputVoltage = stepUp() ? low : high;
        energy.maxAmperesIn = stepUp() ? high / low : 1;
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
