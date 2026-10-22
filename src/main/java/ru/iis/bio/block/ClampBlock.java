package ru.iis.bio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ручной пережим ULV (§20.4): неживой зажим на сосуде; ПКМ — следующая ступень, Shift+ПКМ — показать сеть. Добавочное
 * сопротивление по ступеням: 0, 0,0025, 0,01, 0,04 атм·т/мБ, затем полностью закрыт. Давление не повышает.
 */
public class ClampBlock extends VesselBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("clamp", 0, 4);
    private static final double[] ADDED = {0, 0.0025, 0.01, 0.04};

    public ClampBlock(Spec spec, Properties properties) {
        super(spec, properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new ClampBlock(spec, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    public double resistance(BlockState state) {
        int l = state.getValue(LEVEL);
        return l >= 4 ? Double.POSITIVE_INFINITY : spec.r() + ADDED[l];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return super.useWithoutItem(state, level, pos, player, hit);
        if (!level.isClientSide()) {
            int l = (state.getValue(LEVEL) + 1) % 5;
            level.setBlock(pos, state.setValue(LEVEL, l), 3);
            level.playSound(null, pos, SoundEvents.CHERRY_WOOD_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 0.7f + 0.15f * l);
            player.sendOverlayMessage(Component.translatable("message.iis.bio.clamp." + l));
        }
        return InteractionResult.SUCCESS;
    }
}
