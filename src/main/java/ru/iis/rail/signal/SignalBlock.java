package ru.iis.rail.signal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Сигналы Railcraft (D102): блок-сигнал ({@code TileSignalBlockSignal}) — спаривается геодезистом с другим
 * блок-сигналом, показывает красный, пока на пути между ними есть вагонетка, иначе зелёный; коробка-контроллер
 * ({@code TileBoxController}) — без сигнала зелёный, с сигналом красный; коробка-приёмник ({@code TileBoxReceiver}) —
 * настраивается тюнером на контроллер или блок-сигнал и подаёт сигнал красного камня, пока показание совпадает с выбранным
 * (по умолчанию зелёный; ПКМ меняет).
 */
public class SignalBlock extends BaseEntityBlock {
    public enum Type { BLOCK_SIGNAL, CONTROLLER_BOX, RECEIVER_BOX }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<SignalAspect.Lamp> LAMP = EnumProperty.create("lamp", SignalAspect.Lamp.class);
    private static final VoxelShape POST = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape BOX = Block.box(2, 0, 2, 14, 10, 14);

    public final Type type;
    private final MapCodec<SignalBlock> codec;

    public SignalBlock(Type type, Properties p) {
        super(p);
        this.type = type;
        this.codec = simpleCodec(q -> new SignalBlock(type, q));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LAMP, SignalAspect.Lamp.OFF));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAMP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return type == Type.BLOCK_SIGNAL ? POST : BOX;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return type == Type.RECEIVER_BOX;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return type == Type.RECEIVER_BOX && level.getBlockEntity(pos) instanceof SignalBlockEntity be && be.powered() ? 15 : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (type != Type.RECEIVER_BOX) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SignalBlockEntity be) {
            SignalAspect a = be.cycleTrigger();
            player.sendOverlayMessage(Component.translatable("message.iis.signal.trigger", Component.translatable("signal.iis.aspect." + a.getSerializedName())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof SignalBlockEntity sb) sb.serverTick();
        };
    }
}
