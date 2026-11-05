package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BatteryBufferBlock extends LvMachineBlock {
    public final int slots;
    /** GT mTier: напряжение выхода и какие батарейки можно заряжать (не выше уровня буфера). */
    public final ru.iis.energy.VoltageTier tier;

    /** GT_MetaTileEntity_Charger (D108): запас и токи больше, заряд инструмента — до 15 × V за тик. */
    public final boolean charger;

    public BatteryBufferBlock(int slots, ru.iis.energy.VoltageTier tier, Properties properties) {
        this(slots, tier, false, properties);
    }

    public BatteryBufferBlock(int slots, ru.iis.energy.VoltageTier tier, boolean charger, Properties properties) {
        super(properties);
        this.slots = slots;
        this.tier = tier;
        this.charger = charger;
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBufferBlockEntity(pos, state);
    }
}
