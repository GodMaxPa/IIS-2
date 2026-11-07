package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.machine.block.MachineBlock;

/** Машины на мускульной силе GT (GT_MetaTileEntity_PrimitiveMuscleMachine, D133): шахтокопатель и добытчик малых руд. */
public class MuscleBlock extends MachineBlock {
    public final boolean digger;

    public MuscleBlock(boolean digger, Properties properties) {
        super(properties);
        this.digger = digger;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MuscleBlockEntity(pos, state);
    }
}
