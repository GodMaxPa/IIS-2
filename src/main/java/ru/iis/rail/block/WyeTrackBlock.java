package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;
import ru.iis.rail.IisRail;
import ru.iis.rail.RailShapes;
import ru.iis.rail.TrackKind;

/**
 * Тройник Railcraft ({@code TrackWye}): ствол со стороны FACING.getOpposite(), ветви влево и вправо. Со ствола вагонетка
 * уходит налево (по часовой от FACING — направо, если тройник переведён), с ветвей — на ствол.
 */
public class WyeTrackBlock extends BaseRailBlock implements IisRail {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE;
    private final TrackKind kind;
    private final MapCodec<WyeTrackBlock> codec;

    public WyeTrackBlock(TrackKind kind, Properties properties) {
        super(false, properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new WyeTrackBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SwitchTrackBlock.SWITCHED, false)
                .setValue(SHAPE, RailShape.SOUTH_WEST).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseRailBlock> codec() {
        return codec;
    }

    @Override
    public TrackKind kind() {
        return kind;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SwitchTrackBlock.SWITCHED, SHAPE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection();
        BlockState s = defaultBlockState().setValue(FACING, f).setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).isSource());
        return s.setValue(SHAPE, route(s, null));
    }

    @Override
    protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean first) {
        return state;
    }

    @Override
    public boolean isFlexibleRail(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canMakeSlopes(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    static RailShape route(BlockState state, @Nullable Direction from) {
        Direction f = state.getValue(FACING), stem = f.getOpposite(), right = f.getClockWise(), left = f.getCounterClockWise();
        if (from == left) return RailShapes.connecting(left, stem);
        if (from == right) return RailShapes.connecting(right, stem);
        return RailShapes.connecting(stem, state.getValue(SwitchTrackBlock.SWITCHED) ? right : left);
    }

    @Override
    public RailShape getRailDirection(BlockState state, BlockGetter level, BlockPos pos, @Nullable AbstractMinecart cart) {
        if (cart == null) return route(state, null);
        Direction f = state.getValue(FACING), right = f.getClockWise();
        double dx = cart.getX() - (pos.getX() + 0.5), dz = cart.getZ() - (pos.getZ() + 0.5);
        double side = dx * right.getStepX() + dz * right.getStepZ();
        double ahead = dx * f.getStepX() + dz * f.getStepZ();
        if (Math.abs(side) > 0.05 && Math.abs(side) >= ahead) return route(state, side > 0 ? right : right.getOpposite());
        return route(state, null);
    }
}
