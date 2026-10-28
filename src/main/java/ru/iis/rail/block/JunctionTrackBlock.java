package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.rail.IisRail;
import ru.iis.rail.TrackKind;

/** Крестовина Railcraft ({@code TrackJunction}): вагонетка едет прямо по той оси, по которой движется. */
public class JunctionTrackBlock extends BaseRailBlock implements IisRail {
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    private final TrackKind kind;
    private final MapCodec<JunctionTrackBlock> codec;

    public JunctionTrackBlock(TrackKind kind, Properties properties) {
        super(true, properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new JunctionTrackBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, false));
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
        builder.add(SHAPE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).isSource());
    }

    /** Форма не пересчитывается по соседям: крестовина всегда соединяет все четыре стороны. */
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

    @Override
    public RailShape getRailDirection(BlockState state, BlockGetter level, BlockPos pos, @Nullable AbstractMinecart cart) {
        if (cart == null) return RailShape.NORTH_SOUTH;
        Vec3 m = cart.getDeltaMovement();
        if (Math.abs(m.x) != Math.abs(m.z)) return Math.abs(m.x) > Math.abs(m.z) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        // стоит на месте — по смещению от центра
        double dx = cart.getX() - (pos.getX() + 0.5), dz = cart.getZ() - (pos.getZ() + 0.5);
        return Math.abs(dx) > Math.abs(dz) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
    }
}
