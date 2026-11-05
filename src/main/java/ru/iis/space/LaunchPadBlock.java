package ru.iis.space;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Взлётная площадка: плита высотой 3/16, ракете нужен квадрат 3×3. */
public class LaunchPadBlock extends Block {
    public static final double HEIGHT = 3.0 / 16;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
