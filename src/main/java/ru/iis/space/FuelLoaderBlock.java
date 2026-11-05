package ru.iis.space;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Загрузчик топлива. Отличие от GC: без питания и окна, топливо приходит по трубе, щелчок показывает запас. */
public class FuelLoaderBlock extends BaseEntityBlock {
    public static final MapCodec<FuelLoaderBlock> CODEC = simpleCodec(FuelLoaderBlock::new);

    public FuelLoaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelLoaderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SpaceContent.FUEL_LOADER_BE.get(), FuelLoaderBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuelLoaderBlockEntity be) {
            player.sendOverlayMessage(Component.translatable("message.iis.fuel_loader", be.tank.amount(), FuelLoaderBlockEntity.CAPACITY));
        }
        return InteractionResult.SUCCESS;
    }
}
