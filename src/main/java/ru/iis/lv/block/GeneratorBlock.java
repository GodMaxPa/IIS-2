package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.lv.GeneratorType;

public class GeneratorBlock extends LvMachineBlock {
    public final GeneratorType type;

    public GeneratorBlock(GeneratorType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return side == front(state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneratorBlockEntity(pos, state);
    }
}
