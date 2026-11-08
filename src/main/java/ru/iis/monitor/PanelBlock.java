package ru.iis.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Информационная панель IC2 Nuclear Control и её расширитель (D141). FACING — сторона экрана. */
public class PanelBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final boolean advanced, extender;

    public PanelBlock(boolean advanced, boolean extender, Properties properties) {
        super(properties);
        this.advanced = advanced;
        this.extender = extender;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return extender ? null : new PanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || extender) return null;
        return (l, p, s, be) -> {
            if (be instanceof PanelBlockEntity panel) panel.serverTick();
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PanelBlockEntity be)) return InteractionResult.PASS;
        net.minecraft.world.item.DyeColor dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null && be.hasColor()) {
            if (!level.isClientSide()) be.setColor(dye.getTextColor());
            return InteractionResult.SUCCESS;
        }
        if (be.accepts(stack)) {
            if (!level.isClientSide() && be.insert(stack.copyWithCount(1)) && !player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        return useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PanelBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(be);
        return InteractionResult.SUCCESS;
    }
}
