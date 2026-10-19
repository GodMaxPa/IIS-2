package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.EnergyConnectable;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.machine.block.MachineBlock;

import java.util.function.BiConsumer;

/** Люк многоблочной машины: лицевая сторона в любом из шести направлений (к игроку при установке). */
public class HatchBlock extends Block implements EntityBlock, EnergyConnectable {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public final HatchType type;

    public HatchBlock(HatchType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, type == HatchType.MUFFLER ? Direction.UP : Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
        return type == HatchType.ENERGY && side == state.getValue(FACING);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // ключ поворачивает люк; у люка обслуживания ключ — ещё и ремонтный инструмент, там поворот с Shift
        if (ru.iis.machine.block.Wrench.is(stack) && (type != HatchType.MAINTENANCE || player.isShiftKeyDown())) {
            if (!level.isClientSide()) ru.iis.machine.block.Wrench.rotate(level, pos, state, FACING, hit.getDirection(), player, hand, stack);
            return InteractionResult.SUCCESS;
        }
        if (type != HatchType.MAINTENANCE) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MaintenanceHatchBlockEntity m) m.useTool(stack, player, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MaintenanceHatchBlockEntity m) m.report(player);
        else if (be instanceof MenuProvider p && type != HatchType.ENERGY) player.openMenu(p, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (level.getBlockEntity(pos) instanceof ElectricBlockEntity be) be.doEnergyExplosion();
        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (type) {
            case ENERGY -> new EnergyHatchBlockEntity(pos, state);
            case INPUT_BUS, OUTPUT_BUS -> new ItemBusBlockEntity(pos, state);
            case INPUT_HATCH, OUTPUT_HATCH -> new FluidHatchBlockEntity(pos, state);
            case MAINTENANCE -> new MaintenanceHatchBlockEntity(pos, state);
            case MUFFLER -> null;
        };
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof MachineBlock.TickingMachine m) m.serverTick();
        };
    }
}
