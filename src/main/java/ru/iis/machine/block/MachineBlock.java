package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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

/**
 * Блок одноблочной машины GT: лицевая сторона (FACING, к игроку при установке) и признак работы (ACTIVE).
 * Логика в блок-сущности; блок только открывает GUI и тикает её на сервере.
 */
public abstract class MachineBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    protected MachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Гаечный ключ поворачивает машину (лицевая сторона — на кликнутую грань). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ru.iis.cover.CoverHost host) {
            InteractionResult r = ru.iis.cover.Covers.use(host, stack, player, hand, hit, state.getValue(FACING));
            if (r != null) return r;
            // GT: киянка включает/выключает машину
            if (host.isMachine() && ru.iis.multi.block.MaintenanceHatchBlockEntity.problemFor(stack) == ru.iis.multi.logic.Maintenance.Problem.SOFT_HAMMER) {
                if (!level.isClientSide()) {
                    host.setAllowedToWork(!host.allowedToWork());
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(host.allowedToWork() ? "message.iis.machine.enabled" : "message.iis.machine.disabled"));
                    if (!player.getAbilities().instabuild) stack.hurtAndBreak(20, player, hand);
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (!Wrench.is(stack) || player.isShiftKeyDown()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) Wrench.rotate(level, pos, state, FACING, ru.iis.machine.block.Wrench.wrenchingSide(pos, hit), player, hand, stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ru.iis.cover.CoverHost host) {
            InteractionResult r = ru.iis.cover.Covers.use(host, ItemStack.EMPTY, player, InteractionHand.MAIN_HAND, hit, state.getValue(FACING));
            if (r != null) return r;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // --- красный сигнал покрытий (детекторы) -----------------------------------------------------------------------
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ru.iis.cover.CoverHost h ? h.covers().redstoneOut(direction.getOpposite()) : 0;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) PipeBlock.connectNeighbourPipes(level, pos);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof TickingMachine t) t.serverTick();
        };
    }

    /** Блок-сущность, которую тикает сервер. */
    public interface TickingMachine {
        void serverTick();
    }
}
