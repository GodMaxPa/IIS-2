package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
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
import ru.iis.energy.EnergyConnectable;
import ru.iis.machine.block.PipeBlock;

import java.util.function.BiConsumer;

/**
 * Трансформатор: лицевая сторона в любом из шести направлений (к игроку при установке).
 * Режим переключается гаечным ключом с Shift (в GT5 — мягким молотом; отступление, см. PORTING_NOTES).
 */
public class TransformerBlock extends Block implements EntityBlock, EnergyConnectable {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty STEP_UP = BooleanProperty.create("step_up");

    public TransformerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STEP_UP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STEP_UP);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public boolean connectsEnergy(BlockState state, Direction side) {
        return true;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ru.iis.cover.CoverHost host) {
            InteractionResult r = ru.iis.cover.Covers.use(host, stack, player, hand, hit, state.getValue(FACING));
            if (r != null) return r;
        }
        if (!stack.is(PipeBlock.WRENCH)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide()) ru.iis.machine.block.Wrench.rotate(level, pos, state, FACING, ru.iis.machine.block.Wrench.wrenchingSide(pos, hit), player, hand, stack);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            boolean up = !state.getValue(STEP_UP);
            level.setBlock(pos, state.setValue(STEP_UP, up), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f);
            player.sendOverlayMessage(Component.translatable(up ? "message.iis.transformer.up" : "message.iis.transformer.down"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (level.getBlockEntity(pos) instanceof ElectricBlockEntity be) be.doEnergyExplosion();
        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransformerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof TransformerBlockEntity t) t.serverTick();
        };
    }
}
