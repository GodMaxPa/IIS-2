package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Контроллер примитивной кирпичной доменной печи. */
public class BbfBlock extends MachineBlock {
    public BbfBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BbfBlockEntity(pos, state);
    }
}
