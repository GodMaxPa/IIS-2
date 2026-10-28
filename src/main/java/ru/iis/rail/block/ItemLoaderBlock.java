package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
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
import org.jetbrains.annotations.Nullable;

/**
 * Погрузчик и разгрузчик предметов Railcraft ({@code TileItemLoader}, {@code TileItemUnloader} и продвинутые
 * {@code ...Advanced}, D102). Обычный погрузчик грузит вагонетку под собой, обычный разгрузчик — над собой,
 * продвинутые — с передней стороны (FACING). По предмету за тик из буфера на 9 слотов и соседних инвентарей в
 * вагонетку (разгрузчик — наоборот). Режим «сразу»: когда переносить больше нечего и вагонетка стоит, блок подаёт
 * сигнал красного камня (отпустить её блокирующим путём). Фильтры и другие режимы окна Railcraft не перенесены.
 */
public class ItemLoaderBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public final boolean unloader, advanced;
    private final MapCodec<ItemLoaderBlock> codec;

    public ItemLoaderBlock(boolean unloader, boolean advanced, Properties p) {
        super(p);
        this.unloader = unloader;
        this.advanced = advanced;
        this.codec = simpleCodec(q -> new ItemLoaderBlock(unloader, advanced, q));
        registerDefaultState(stateDefinition.any().setValue(FACING, unloader ? Direction.UP : Direction.DOWN));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // продвинутый смотрит от игрока в сторону, куда тот целится (на путь)
        return advanced ? defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection()) : defaultBlockState();
    }

    /** Сторона, где стоит обслуживаемая вагонетка. */
    public Direction cartSide(BlockState state) {
        return advanced ? state.getValue(FACING) : unloader ? Direction.UP : Direction.DOWN;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ItemLoaderBlockEntity be) {
            player.openMenu(be);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ItemLoaderBlockEntity be && be.powered() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemLoaderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof ItemLoaderBlockEntity lb) lb.serverTick();
        };
    }
}
