package ru.iis.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.lv.block.LvMachineBlock;

/** Crop-Matron и сборщик урожая IC2 (D139): LV, энергия с любой стороны. */
public class CropMachineBlock extends LvMachineBlock {
    public final CropMachineBlockEntity.Kind kind;

    public CropMachineBlock(CropMachineBlockEntity.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CropMachineBlockEntity(pos, state);
    }
}
