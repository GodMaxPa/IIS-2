package ru.iis.bio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.BioContent;
import ru.iis.bio.production.FluidHands;
import ru.iis.bio.production.Station;

/** Блок ручного места или органа Био (D70): ПКМ — интерфейс; ведро воды / мерный ковш — налить или набрать. */
public class StationBlock extends BaseEntityBlock {
    public final Station station;
    private final VoxelShape shape;

    public StationBlock(Station station, Properties properties) {
        super(properties);
        this.station = station;
        this.shape = switch (station) {
            case MORTAR -> Block.box(4, 0, 4, 12, 8, 12);
            case POT -> Block.box(1, 0, 1, 15, 12, 15);
            case MIXER -> Block.box(0, 0, 0, 16, 10, 16);
            case SUBSTRATE -> Block.box(0, 0, 0, 16, 4, 16);
            case BED -> Block.box(0, 0, 0, 16, 8, 16);
            default -> Block.box(0, 0, 0, 16, 14, 16);
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new StationBlock(station, p));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BioContent.STATION_BE.get()) return null;
        return (l, p, s, be) -> ((StationBlockEntity) be).serverTick();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof StationBlockEntity be && FluidHands.handle(level, player, hand, stack, be.logic.tanks)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StationBlockEntity be) {
            player.openMenu(be, buf -> {
                buf.writeVarInt(station.ordinal());
                buf.writeBlockPos(pos);
            });
        }
        return InteractionResult.SUCCESS;
    }
}
