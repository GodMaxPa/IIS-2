package ru.iis.bio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.BioContent;

/** Пасть-кормушка: орган, через который колония получает сырьё. Работает только рядом с ядром или тканью колонии. */
public class FeedingMawBlock extends BaseEntityBlock {
    public static final MapCodec<FeedingMawBlock> CODEC = simpleCodec(FeedingMawBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Связана ли пасть с живой колонией (иначе она тусклая и не принимает пищу). */
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    /** Жуёт: открыта и анимирована после кормления. */
    public static final BooleanProperty CHEWING = BooleanProperty.create("chewing");

    public FeedingMawBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONNECTED, false).setValue(CHEWING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED, CHEWING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeedingMawBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BioContent.MAW_BE.get()) return null;
        return (l, p, s, be) -> ((FeedingMawBlockEntity) be).serverTick();
    }

    /** ПКМ съедобным предметом: пасть передаёт его колонии. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // вода и ковш — в карман колонии, к которой подключена пасть
        if (stack.is(net.minecraft.world.item.Items.WATER_BUCKET) || stack.getItem() instanceof ru.iis.bio.item.LadleItem) {
            ColonyCoreBlockEntity core = ColonyCoreBlockEntity.find(level, pos);
            if (core != null && ru.iis.bio.production.FluidHands.handle(level, player, hand, stack, core.pocket.tanks)) return InteractionResult.SUCCESS;
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!FeedingMawBlockEntity.edible(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FeedingMawBlockEntity maw) maw.feedFrom(player, stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable(state.getValue(CONNECTED) ? "message.iis.bio.maw.ready" : "message.iis.bio.maw.disconnected"));
        }
        return InteractionResult.SUCCESS;
    }
}
