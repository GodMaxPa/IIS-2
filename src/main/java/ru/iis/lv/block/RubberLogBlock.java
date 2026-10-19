package ru.iis.lv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * Каучуковое дерево (IC2 BlockRubWood, D43). Ствол с отверстием смолы на одной стороне (FACING):
 * «мокрое» отверстие даёт смолу краником и становится «сухим», сухое со временем снова наполняется (1/7 за случайный тик).
 * Поставленное игроком бревно отверстия не имеет.
 */
public class RubberLogBlock extends Block {
    public enum Resin implements StringRepresentable {
        NONE("none"), WET("wet"), DRY("dry");

        private final String name;

        Resin(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Resin> RESIN = EnumProperty.create("resin", Resin.class);

    public RubberLogBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RESIN, Resin.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RESIN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(RESIN) == Resin.DRY;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RESIN) == Resin.DRY && random.nextInt(7) == 0) level.setBlock(pos, state.setValue(RESIN, Resin.WET), 3);
    }
}
