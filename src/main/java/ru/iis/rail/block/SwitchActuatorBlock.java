package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Переводной рычаг (ПКМ) и стрелочный привод (красный сигнал) Railcraft ({@code SignalBlock 2, 4}): переводят соседние
 * стрелки и тройники.
 */
public class SwitchActuatorBlock extends Block {
    public static final BooleanProperty THROWN = BooleanProperty.create("thrown");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);
    private final boolean motor;
    private final MapCodec<SwitchActuatorBlock> codec;

    public SwitchActuatorBlock(boolean motor, Properties properties) {
        super(properties);
        this.motor = motor;
        this.codec = simpleCodec(p -> new SwitchActuatorBlock(motor, p));
        registerDefaultState(stateDefinition.any().setValue(THROWN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    public boolean isMotor() {
        return motor;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(THROWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (motor) return InteractionResult.PASS;
        if (!level.isClientSide()) throwSwitch(level, pos, state, !state.getValue(THROWN));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!motor || level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(THROWN)) throwSwitch(level, pos, state, powered);
    }

    static void throwSwitch(Level level, BlockPos pos, BlockState state, boolean thrown) {
        level.setBlock(pos, state.setValue(THROWN, thrown), 3);
        for (Direction d : Direction.Plane.HORIZONTAL) SwitchTrackBlock.setSwitched(level, pos.relative(d), thrown);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, thrown ? 0.6f : 0.5f);
    }

    /** Стрелка, поставленная рядом, сразу встаёт по положению рычага или привода. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel && motor) neighborChanged(state, level, pos, this, null, movedByPiston);
    }
}
