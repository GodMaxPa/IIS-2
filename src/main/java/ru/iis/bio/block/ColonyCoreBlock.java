package ru.iis.bio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.BioContent;

/** Ядро колонии: мозг и «сердце» организма. Очаг на личном челноке — это такое ядро в спящем состоянии. */
public class ColonyCoreBlock extends BaseEntityBlock {
    public static final MapCodec<ColonyCoreBlock> CODEC = simpleCodec(ColonyCoreBlock::new);
    public static final EnumProperty<CoreMood> MOOD = EnumProperty.create("mood", CoreMood.class);

    public ColonyCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOOD, CoreMood.DORMANT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOOD);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColonyCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BioContent.CORE_BE.get()) return null;
        return (l, p, s, be) -> ((ColonyCoreBlockEntity) be).serverTick();
    }

    /** Ведро воды или мерный ковш — налить в карман / набрать N0 колонии и продукты кармана. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ColonyCoreBlockEntity core && ru.iis.bio.production.FluidHands.handle(level, player, hand, stack, core.pocket.tanks)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** ПКМ: состояние организма в строке над панелью и интерфейс пищеварительного кармана. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ColonyCoreBlockEntity core) {
            player.sendOverlayMessage(core.status());
            if (!player.isShiftKeyDown()) {
                player.openMenu(core, buf -> {
                    buf.writeVarInt(ru.iis.bio.production.Station.POCKET.ordinal());
                    buf.writeBlockPos(pos);
                });
            }
        }
        return InteractionResult.SUCCESS;
    }
}
