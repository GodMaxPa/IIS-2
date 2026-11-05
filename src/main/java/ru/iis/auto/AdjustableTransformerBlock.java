package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.LvMachineBlock;

/** Настраиваемый трансформатор GT5-II (GT_MetaTileEntity_Digital_Transformer, D108). */
public class AdjustableTransformerBlock extends LvMachineBlock {
    public final VoltageTier tier;

    public AdjustableTransformerBlock(VoltageTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdjustableTransformerBlockEntity(pos, state);
    }
}
