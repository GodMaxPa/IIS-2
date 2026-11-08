package ru.iis.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Грядка IC2 (BlockCrop, D139): стоит на пашне. CROP: 0 — пустые палочки, 1 — двойные (для скрещивания), 2+ — культура из {@link CropCard#ALL};
 * SIZE — стадия. Свойства культуры и запасы — в {@link CropBlockEntity}.
 */
public class CropBlock extends Block implements EntityBlock {
    public static final IntegerProperty CROP = IntegerProperty.create("crop", 0, CropCard.ALL.size() + 1);
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 7);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    public CropBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CROP, 0).setValue(SIZE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CROP, SIZE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof FarmlandBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CropBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, p, s, be) -> {
            if (be instanceof CropBlockEntity c) c.serverTick();
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CropBlockEntity be)) return InteractionResult.PASS;
        boolean client = level.isClientSide();
        CropCard card = be.card();
        if (stack.is(CropContent.CROP_STICKS.get()) && card == null && !be.cross()) {
            if (!client) {
                be.makeCross();
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.SUCCESS;
        }
        CropSeedItem.Seed seed = CropSeedItem.seed(stack);
        if (seed != null && card == null && !be.cross()) {
            if (!client && be.plant(seed)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.SUCCESS;
        }
        if (card == null && !be.cross() && CropBlockEntity.baseSeedFor(stack) != null) {
            if (!client) {
                ItemStack copy = player.getAbilities().instabuild ? stack.copy() : stack;
                be.plantBase(copy);
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(CropContent.FERTILIZER.get())) {
            if (!client && be.applyFertilizer(true) && !player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(CropContent.WEED_EX.get())) {
            if (!client && be.applyWeedEx(true)) stack.hurtAndBreak(1, player, hand);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(CropContent.WEEDING_TROWEL.get())) {
            if (!client) {
                ItemStack w = be.weedOut();
                if (w != null) be.drop(List.of(w));
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(CropContent.CROP_LENS.get())) {
            if (!client) be.describe().forEach(player::sendSystemMessage);
            return InteractionResult.SUCCESS;
        }
        return useWithoutItem(state, level, pos, player, hit);
    }

    /** CropCard#rightclick: спелая культура отдаёт урожай. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CropBlockEntity be)) return InteractionResult.PASS;
        CropCard c = be.card();
        if (c == null || !c.canBeHarvested(be.size())) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl) be.drop(be.harvest(sl.getRandom()));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(CropContent.CROP_STICKS.get());
    }
}
