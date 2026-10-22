package ru.iis.bio.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Живая ткань колонии: тонкий слой на гранях существующих блоков (как лишайник), не заменяет землю и камень.
 * Растёт только от ядра (ColonyCoreBlockEntity), без ядра в пределах территории отмирает.
 */
public class BioTissueBlock extends MultifaceBlock {
    public static final MapCodec<BioTissueBlock> CODEC = simpleCodec(BioTissueBlock::new);
    public static final EnumProperty<Vitality> VITALITY = EnumProperty.create("vitality", Vitality.class);

    public BioTissueBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MultifaceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VITALITY);
    }

    /**
     * Соскоб ножом Био (D70, решение Р8): одна клетка ткани колонии превращается в неразвитую культуру этой линии.
     * Колония теряет клетку и тревожится (так же, как при поломке).
     */
    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
                                                               BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                               net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.is(ru.iis.bio.item.BioItems.KNIFE.get())) return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel sl) {
            ColonyCoreBlockEntity core = ColonyCoreBlockEntity.owner(sl, pos);
            if (core == null) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.iis.bio.scrape.dead"));
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            net.minecraft.core.Direction face = null;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) if (hasFace(state, d)) face = d;
            if (face == null) return net.minecraft.world.InteractionResult.SUCCESS;
            BlockState ns = state.setValue(getFaceProperty(face), false);
            boolean any = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) any |= hasFace(ns, d);
            if (any) sl.setBlock(pos, ns, 3);
            else sl.removeBlock(pos, false);
            net.minecraft.world.item.ItemStack culture = new net.minecraft.world.item.ItemStack(ru.iis.bio.item.BioItems.PRIMITIVE_CULTURE.get());
            culture.set(ru.iis.bio.item.BioItems.LINEAGE.get(), core.lineage());
            if (!player.getInventory().add(culture)) player.drop(culture, false);
            stack.hurtAndBreak(1, player, hand);
            sl.playSound(null, pos, ru.iis.bio.BioSounds.TEAR, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.iis.bio.scrape.done"));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** Осиротевшая ткань (ядро убрано, пока чанк был выгружен) умирает. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk c = level.getChunkSource().getChunkNow((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
                if (c == null) return; // соседний чанк не загружен — не знаем, оставляем жить
                for (var be : c.getBlockEntities().values()) {
                    if (be instanceof ColonyCoreBlockEntity core && core.claims(pos)) return;
                }
            }
        level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.01);
        level.removeBlock(pos, false);
    }
}
