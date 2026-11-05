package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.logic.BlastLogic;

/**
 * Настраиваемый трансформатор (GT_MetaTileEntity_Digital_Transformer, D108): вход V[уровня блока] 0–64 А, выход V[настройка] (от ULV до уровня
 * блока) 0–64 А. Включён — вход спереди, выход с остальных сторон; выключен киянкой — наоборот. Запас 512 + 256 × V.
 */
public class AdjustableTransformerBlockEntity extends ElectricBlockEntity {
    public static final int DATA = 4;

    private final int tier;
    public int oTier, oAmp, iAmp;
    public boolean allowed = true;

    public AdjustableTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(AutoContent.ADJUSTABLE_TRANSFORMER_BE.get(), pos, state, 0, 512 + tier(state).voltage() * 256, tier(state).voltage());
        this.tier = tier(state).tier();
    }

    private static VoltageTier tier(BlockState s) {
        return s.getBlock() instanceof AdjustableTransformerBlock b ? b.tier : VoltageTier.EV;
    }

    /** GT_Container_DigitalTransformer.slotClick: столбец 0 — минус, 1 — плюс; ряд 0 вход А, 1 уровень, 2 выход А; с Shift шаг 8 (уровень — 4). */
    public void adjust(int row, boolean plus, boolean shift) {
        int mult = plus ? 1 : -1, step = mult * (shift ? 8 : 1);
        switch (row) {
            case 0 -> iAmp = Math.max(0, Math.min(64, iAmp + step));
            case 1 -> oTier = Math.max(0, Math.min(tier, oTier + mult * (shift ? 4 : 1)));
            default -> oAmp = Math.max(0, Math.min(64, oAmp + step));
        }
        setChanged();
    }

    private boolean isInput(Direction side) {
        return allowed ? side == front() : side != front();
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return isInput(side);
    }

    @Override
    public boolean outputsEnergyTo(Direction side) {
        return !isInput(side);
    }

    @Override
    public long outputVoltage() {
        return BlastLogic.V[oTier];
    }

    @Override
    public long outputAmperes() {
        return oAmp;
    }

    @Override
    public long minimumStored() {
        return 512;
    }

    @Override
    public boolean isMachine() {
        return true;
    }

    @Override
    public boolean allowedToWork() {
        return allowed;
    }

    @Override
    public void setAllowedToWork(boolean a) {
        if (allowed != a) {
            allowed = a;
            setChanged();
        }
    }

    @Override
    protected void work(ServerLevel level) {
        energy.maxAmperesIn = iAmp;
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) == allowed) level.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, !allowed), 3);
    }

    @Override
    public long injectEnergy(Direction from, long voltage, long amperes) {
        energy.maxAmperesIn = iAmp;
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

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> iAmp;
                case 1 -> oTier;
                case 2 -> oAmp;
                case 3 -> tier;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA;
        }
    };

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new AdjustableTransformerMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("oTier", oTier);
        out.putInt("oAmp", oAmp);
        out.putInt("iAmp", iAmp);
        out.putBoolean("allowed", allowed);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        oTier = in.getIntOr("oTier", 0);
        oAmp = in.getIntOr("oAmp", 0);
        iAmp = in.getIntOr("iAmp", 0);
        allowed = in.getBooleanOr("allowed", true);
    }
}
