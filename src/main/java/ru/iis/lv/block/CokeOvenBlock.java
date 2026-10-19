package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.block.MachineBlock;

/** Контроллер коксовой печи: центр одной из стенок куба 3×3×3 из кирпичей коксовой печи, лицом наружу. */
public class CokeOvenBlock extends MachineBlock {
    public CokeOvenBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CokeOvenBlockEntity(pos, state);
    }
}
