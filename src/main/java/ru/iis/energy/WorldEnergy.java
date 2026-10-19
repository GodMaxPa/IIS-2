package ru.iis.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.energy.logic.CableSpec;
import ru.iis.energy.logic.CableState;
import ru.iis.energy.logic.EnergyNet;
import ru.iis.lv.block.CableBlock;
import ru.iis.lv.block.CableBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Связка логики {@link EnergyNet} с миром: узлы — позиции блоков, провода — {@link CableBlock},
 * потребители — блок-сущности {@link EuNode}. Перегретые провода после обхода сгорают (GT5 setToFire).
 */
public final class WorldEnergy {
    private WorldEnergy() {}

    private static final Direction[] BY_FACE = new Direction[6];
    static {
        for (Direction d : Direction.values()) BY_FACE[d.get3DDataValue()] = d;
    }

    private static final class Graph implements EnergyNet.Graph<BlockPos> {
        final ServerLevel level;
        final List<BlockPos> touched = new ArrayList<>();

        Graph(ServerLevel level) {
            this.level = level;
        }

        @Override
        public BlockPos neighbour(BlockPos node, int face) {
            BlockPos n = node.relative(BY_FACE[face]);
            return level.isLoaded(n) ? n : null;
        }

        @Override
        public boolean isCable(BlockPos node) {
            return level.getBlockState(node).getBlock() instanceof CableBlock;
        }

        @Override
        public CableSpec spec(BlockPos cable) {
            return ((CableBlock) level.getBlockState(cable).getBlock()).spec.logic();
        }

        @Override
        public CableState state(BlockPos cable) {
            touched.add(cable);
            return level.getBlockEntity(cable) instanceof CableBlockEntity be ? be.state : new CableState();
        }

        @Override
        public boolean connected(BlockPos cable, int face) {
            BlockState s = level.getBlockState(cable);
            return s.getBlock() instanceof CableBlock && s.getValue(CableBlock.PROPS.get(BY_FACE[face]));
        }

        @Override
        public long insert(BlockPos sink, int face, long voltage, long amperes) {
            return level.getBlockEntity(sink) instanceof EuNode node ? node.injectEnergy(BY_FACE[face], voltage, amperes) : 0;
        }
    }

    /** Выпустить пакет из блока через стороны. Возвращает использованные амперы. */
    public static long emit(ServerLevel level, BlockPos from, List<Direction> sides, long voltage, long amperes) {
        if (amperes <= 0 || voltage <= 0 || sides.isEmpty()) return 0;
        Graph g = new Graph(level);
        int[] faces = sides.stream().mapToInt(Direction::get3DDataValue).toArray();
        long used = EnergyNet.emit(g, from, faces, voltage, amperes, level.getGameTime());
        for (BlockPos p : g.touched) {
            if (level.getBlockEntity(p) instanceof CableBlockEntity be && be.state.burnt) {
                if (ru.iis.IISConfig.on(ru.iis.IISConfig.CABLE_BURNING)) burn(level, p);
                else be.state.burnt = false;
            }
        }
        return used;
    }

    /** GT5 BaseMetaPipeEntity#setToFire: провод сгорает и на его месте вспыхивает огонь. */
    public static void burn(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof CableBlock)) return;
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.6f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (fire.canSurvive(level, pos)) level.setBlockAndUpdate(pos, fire);
        else if (Blocks.FIRE.defaultBlockState().canSurvive(level, pos)) level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
    }
}
