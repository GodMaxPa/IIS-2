package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import ru.iis.rail.IisRail;
import ru.iis.rail.RailContent;
import ru.iis.rail.RailShapes;
import ru.iis.rail.TrackKind;

/**
 * Стрелка Railcraft ({@code TrackSwitch}). FACING — направление движения от остряков: вагонетка входит со стороны
 * FACING.getOpposite() и идёт прямо (выход FACING) или, если стрелка переведена, в ответвление — по часовой стрелке
 * от FACING, с MIRRORED — против. Из ответвления вагонетка всегда уходит на остряки, с прямого конца — прямо.
 * Переводят стрелку переводной рычаг и стрелочный привод рядом ({@link SwitchActuatorBlock}); монтировка зеркалит.
 */
public class SwitchTrackBlock extends BaseRailBlock implements IisRail {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");
    public static final BooleanProperty SWITCHED = BooleanProperty.create("switched");
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    private final TrackKind kind;
    private final MapCodec<SwitchTrackBlock> codec;

    public SwitchTrackBlock(TrackKind kind, Properties properties) {
        super(true, properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new SwitchTrackBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MIRRORED, false).setValue(SWITCHED, false)
                .setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, false));
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
        builder.add(FACING, MIRRORED, SWITCHED, SHAPE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, f).setValue(SHAPE, RailShapes.straight(f.getAxis()))
                .setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).isSource());
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

    /** Сторона ответвления. */
    public static Direction branch(BlockState state) {
        Direction f = state.getValue(FACING);
        return state.getValue(MIRRORED) ? f.getCounterClockWise() : f.getClockWise();
    }

    /** Поворот на ответвление: соединяет сторону остряков и ответвление. */
    public static RailShape divergingShape(BlockState state) {
        return RailShapes.connecting(state.getValue(FACING).getOpposite(), branch(state));
    }

    @Override
    public RailShape getRailDirection(BlockState state, BlockGetter level, BlockPos pos, @Nullable AbstractMinecart cart) {
        RailShape straight = RailShapes.straight(state.getValue(FACING).getAxis());
        if (cart == null) return state.getValue(SWITCHED) ? divergingShape(state) : straight;
        Direction f = state.getValue(FACING), b = branch(state);
        double dx = cart.getX() - (pos.getX() + 0.5), dz = cart.getZ() - (pos.getZ() + 0.5);
        double alongBranch = dx * b.getStepX() + dz * b.getStepZ();
        double alongFacing = dx * f.getStepX() + dz * f.getStepZ();
        if (alongBranch > 0.05 && alongBranch >= Math.abs(alongFacing)) return divergingShape(state);   // из ответвления
        if (alongFacing > 0.05) return straight;                                                           // с прямого конца
        return state.getValue(SWITCHED) ? divergingShape(state) : straight;
    }

    /** Монтировка зеркалит ответвление (Railcraft: ПКМ монтировкой по стрелке). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(RailContent.CROWBARS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) level.setBlock(pos, state.cycle(MIRRORED), 3);
        return InteractionResult.SUCCESS;
    }

    public static void setSwitched(Level level, BlockPos pos, boolean switched) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(SWITCHED) && s.getValue(SWITCHED) != switched) level.setBlock(pos, s.setValue(SWITCHED, switched), 3);
    }
}
