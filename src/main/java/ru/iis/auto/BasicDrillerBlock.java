package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.LvMachineBlock;

/** Автобур и помпа GT5-II (D108). */
public class BasicDrillerBlock extends LvMachineBlock {
    public final boolean pump;
    public final VoltageTier tier;

    public BasicDrillerBlock(boolean pump, VoltageTier tier, Properties properties) {
        super(properties);
        this.pump = pump;
        this.tier = tier;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return side != state.getValue(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BasicDrillerBlockEntity(pos, state);
    }
}
