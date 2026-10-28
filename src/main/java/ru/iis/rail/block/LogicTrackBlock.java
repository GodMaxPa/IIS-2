package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import ru.iis.rail.IisRail;
import ru.iis.rail.RailContent;
import ru.iis.rail.RailShapes;
import ru.iis.rail.TrackKind;
import ru.iis.rail.cart.Locomotive;
import ru.iis.rail.cart.TrainLinks;

/**
 * Пути с действием Railcraft на прямом участке (FACING — направление пути):
 * упор ({@code TrackBufferStop}: вагонетку, едущую в сторону FACING, останавливает брус на конце),
 * блокирующий ({@code TrackNextGenLocking}, профиль «Lockdown»: держит вагонетку, красный сигнал отпускает — дальше
 * в том направлении, в котором она пришла),
 * сцепной ({@code TrackCoupler}: с сигналом сцепляет вагонетку со следующей, в режиме расцепки — рвёт её сцепки;
 * монтировка переключает режим),
 * локомотивный ({@code TrackLocomotive}: с сигналом переводит проходящий локомотив в режим пути — ход, холостой ход
 * или стоп; монтировка переключает режим).
 */
public class LogicTrackBlock extends BaseRailBlock implements IisRail {
    public enum Type { BUFFER_STOP, LOCKING, COUPLER, LOCOMOTIVE }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Сцепной путь: false — сцепка, true — расцепка. */
    public static final BooleanProperty DECOUPLER = BooleanProperty.create("decoupler");
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    /** Локомотивный путь: режим, в который переводится локомотив (Locomotive.Mode: ход, холостой, стоп). */
    public static final IntegerProperty LOCO_MODE = IntegerProperty.create("loco_mode", 0, 2);
    /** Ключи в данных вагонетки: направление прихода на блокирующий путь и задержка после отпуска. */
    public static final String LOCK_DIR = "iis_lock_dir", LOCK_COOLDOWN = "iis_lock_cd";
    /** Скорость, с которой блокирующий путь отпускает вагонетку. */
    public static final double RELEASE_SPEED = 0.4;

    private static final String[] MODE_KEYS = {"running", "idle", "shutdown"};

    private final Type type;
    private final MapCodec<LogicTrackBlock> codec;

    public LogicTrackBlock(Type type, Properties properties) {
        super(true, properties);
        this.type = type;
        this.codec = simpleCodec(p -> new LogicTrackBlock(type, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(DECOUPLER, false)
                .setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(LOCO_MODE, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseRailBlock> codec() {
        return codec;
    }

    public Type type() {
        return type;
    }

    @Override
    public TrackKind kind() {
        return TrackKind.STANDARD;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, DECOUPLER, SHAPE, LOCO_MODE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, f).setValue(SHAPE, RailShapes.straight(f.getAxis()))
                .setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()))
                .setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).isSource());
    }

    @Override
    protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean first) {
        return state;
    }

    @Override
    protected void updateState(BlockState state, Level level, BlockPos pos, Block block) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, powered), 3);
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
        return RailShapes.straight(state.getValue(FACING).getAxis());
    }

    /** Брус упора на конце FACING — настоящая преграда для вагонетки. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (type != Type.BUFFER_STOP) return Shapes.empty();
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0, 0, 0, 16, 10, 3);
            case SOUTH -> Block.box(0, 0, 13, 16, 10, 16);
            case WEST -> Block.box(0, 0, 0, 3, 10, 16);
            default -> Block.box(13, 0, 0, 16, 10, 16);
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(RailContent.CROWBARS) || type != Type.COUPLER && type != Type.LOCOMOTIVE) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (type == Type.LOCOMOTIVE) {
            if (!level.isClientSide()) {
                BlockState next = state.setValue(LOCO_MODE, (state.getValue(LOCO_MODE) + 1) % 3);
                level.setBlock(pos, next, 3);
                player.sendOverlayMessage(Component.translatable("message.iis.rail.loco_track",
                        Component.translatable("gui.iis.loco.mode." + MODE_KEYS[next.getValue(LOCO_MODE)])));
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            BlockState next = state.cycle(DECOUPLER);
            level.setBlock(pos, next, 3);
            player.sendOverlayMessage(Component.translatable(next.getValue(DECOUPLER) ? "message.iis.rail.decoupler" : "message.iis.rail.coupler"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onCartPass(BlockState state, ServerLevel level, BlockPos pos, AbstractMinecart cart) {
        Direction f = state.getValue(FACING);
        switch (type) {
            case BUFFER_STOP -> {
                Vec3 m = cart.getDeltaMovement();
                double toward = m.x * f.getStepX() + m.z * f.getStepZ();
                if (toward > 0) cart.setDeltaMovement(m.x * (1 - Math.abs(f.getStepX())), m.y, m.z * (1 - Math.abs(f.getStepZ())));
            }
            case LOCKING -> lockingPass(state, pos, cart, f);
            case COUPLER -> {
                if (!state.getValue(POWERED)) return;
                if (state.getValue(DECOUPLER)) TrainLinks.breakAll(level, cart);
                else TrainLinks.setAutoLink(cart, true);
            }
            case LOCOMOTIVE -> {
                if (state.getValue(POWERED) && cart instanceof Locomotive loco) loco.setMode(Locomotive.Mode.values()[state.getValue(LOCO_MODE)]);
            }
        }
    }

    private static void lockingPass(BlockState state, BlockPos pos, AbstractMinecart cart, Direction f) {
        var data = cart.getPersistentData();
        if (data.getIntOr(LOCK_COOLDOWN, 0) > 0) return;
        Vec3 m = cart.getDeltaMovement();
        if (!data.contains(LOCK_DIR)) {
            double along = m.x * f.getStepX() + m.z * f.getStepZ();
            data.putInt(LOCK_DIR, along < 0 ? -1 : 1);
        }
        if (state.getValue(POWERED)) {
            int dir = data.getIntOr(LOCK_DIR, 1);
            cart.setDeltaMovement(f.getStepX() * dir * RELEASE_SPEED, m.y, f.getStepZ() * dir * RELEASE_SPEED);
            data.remove(LOCK_DIR);
            data.putInt(LOCK_COOLDOWN, 20);
            return;
        }
        cart.setDeltaMovement(0, m.y, 0);
        cart.setPos(f.getAxis() == Direction.Axis.X ? pos.getX() + 0.5 : cart.getX(), cart.getY(), f.getAxis() == Direction.Axis.Z ? pos.getZ() + 0.5 : cart.getZ());
    }
}
