package ru.iis.equipment.vehicle;

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
import ru.iis.equipment.EquipmentContent;

/** Заправщик машин (D130): как загрузчик топлива у ракеты, только для транспорта. Топливо приходит по трубе или из капсулы, окна нет, ПКМ показывает запас. */
public class FuelPumpBlock extends BaseEntityBlock {
    public static final MapCodec<FuelPumpBlock> CODEC = simpleCodec(FuelPumpBlock::new);

    public FuelPumpBlock(Properties properties) {
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
        return new FuelPumpBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, EquipmentContent.FUEL_PUMP_BE.get(), FuelPumpBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuelPumpBlockEntity be) {
            Component fluid = be.tank.amount() > 0 ? be.tank.resource().getFluid().getFluidType().getDescription() : Component.translatable("message.iis.fuel_pump.empty");
            player.sendOverlayMessage(Component.translatable("message.iis.fuel_pump", fluid, be.tank.amount(), FuelPumpBlockEntity.CAPACITY));
        }
        return InteractionResult.SUCCESS;
    }
}
