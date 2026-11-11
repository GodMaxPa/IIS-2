package ru.iis.rail.block;

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
import org.jspecify.annotations.Nullable;

/** Обшивка водонапорной башни Railcraft (D146); ПКМ показывает запас воды собранного куба. */
public class WaterTankBlock extends BaseEntityBlock {
    public static final MapCodec<WaterTankBlock> CODEC = simpleCodec(WaterTankBlock::new);

    public WaterTankBlock(Properties p) {
        super(p);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WaterTankBlockEntity be) {
            WaterTankBlockEntity m = be.master();
            player.sendOverlayMessage(m == null ? Component.translatable("message.iis.water_tank.incomplete")
                    : Component.translatable("message.iis.water_tank", m.tank().amount() / 1000, WaterTankBlockEntity.CAPACITY / 1000));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof WaterTankBlockEntity w) w.serverTick();
        };
    }
}
