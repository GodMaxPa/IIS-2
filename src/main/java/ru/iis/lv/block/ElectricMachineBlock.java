package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.lv.LvMachineType;

public class ElectricMachineBlock extends LvMachineBlock {
    public final LvMachineType type;

    public ElectricMachineBlock(LvMachineType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return side != front(state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricMachineBlockEntity(pos, state);
    }
}
