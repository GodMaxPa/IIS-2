package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BatteryBufferBlock extends LvMachineBlock {
    public final int slots;

    public BatteryBufferBlock(int slots, Properties properties) {
        super(properties);
        this.slots = slots;
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
