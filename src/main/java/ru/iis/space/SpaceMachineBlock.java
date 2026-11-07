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

/**
 * Машины третьего этапа космоса (D134): кислородный сборщик и распределитель пузыря, загрузчик и разгрузчик груза ракеты.
 * Отличие от GC: без питания и окна, как загрузчик топлива (D115); щелчок показывает состояние.
 */
public class SpaceMachineBlock extends BaseEntityBlock {
    public enum Kind { COLLECTOR, DISTRIBUTOR, LOADER, UNLOADER }

    public final Kind kind;

    public SpaceMachineBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new SpaceMachineBlock(kind, p));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpaceMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SpaceContent.SPACE_MACHINE_BE.get(), SpaceMachineBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpaceMachineBlockEntity be) {
            if (kind == Kind.COLLECTOR) player.sendOverlayMessage(Component.translatable("message.iis.oxygen_collector", be.tank.amount(), be.tank.capacity(), be.rate()));
            else if (kind == Kind.DISTRIBUTOR) player.sendOverlayMessage(Component.translatable("message.iis.oxygen_distributor", be.tank.amount(), be.tank.capacity(), be.bubble()));
        }
        return InteractionResult.SUCCESS;
    }
}
