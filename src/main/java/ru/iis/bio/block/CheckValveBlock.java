package ru.iis.bio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Обратный клапан LV (§20.4, §22.7): пропускает только по направлению FACING (куда смотрел игрок при установке), открывается от 0,002 атм. */
public class CheckValveBlock extends VesselBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public CheckValveBlock(Spec spec, Properties properties) {
        super(spec, properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new CheckValveBlock(spec, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    protected BlockState placementBase(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection());
    }

    @Override
    protected boolean sideAllowed(BlockState state, Direction side) {
        return side.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public boolean entry(BlockState state, Direction side) {
        return side == state.getValue(FACING).getOpposite() && super.entry(state, side);
    }

    @Override
    public boolean exit(BlockState state, Direction side) {
        return side == state.getValue(FACING) && super.exit(state, side);
    }

    @Override
    public double threshold(BlockState state) {
        return 0.002;
    }
}
