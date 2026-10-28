package ru.iis.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import ru.iis.rail.block.ElectricFeederBlockEntity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Сеть электрических путей (D102, упрощение Railcraft ChargeHandler): связные электрические пути образуют сеть,
 * фидер рядом с любым её путём (по граням) питает её. Локомотив на электрическом пути берёт из найденного фидера
 * до {@link #DRAW_PER_TICK} EU за тик. Найденный фидер запоминается на секунду, чтобы не обходить сеть каждый тик.
 */
public final class ElectricRail {
    private ElectricRail() {}

    public static final long DRAW_PER_TICK = 64;
    /** Сколько путей обходить в поисках фидера. */
    static final int MAX_TRACKS = 512;
    static final String FEEDER = "iis_feeder", FEEDER_AGE = "iis_feeder_age";

    public static boolean isElectric(BlockState s) {
        return s.getBlock() instanceof IisRail r && r.kind() == TrackKind.ELECTRIC;
    }

    /** Взять до {@code want} EU у фидера сети, на пути которой стоит вагонетка; возвращает взятое. */
    public static long draw(ServerLevel level, AbstractMinecart cart, long want) {
        if (want <= 0) return 0;
        BlockPos rail = cart.getCurrentBlockPosOrRailBelow();
        if (!isElectric(level.getBlockState(rail))) return 0;
        var data = cart.getPersistentData();
        ElectricFeederBlockEntity feeder = null;
        if (data.contains(FEEDER) && level.getGameTime() - data.getLongOr(FEEDER_AGE, 0) < 20) {
            BlockPos p = BlockPos.of(data.getLongOr(FEEDER, 0));
            if (level.getBlockEntity(p) instanceof ElectricFeederBlockEntity f) feeder = f;
        }
        if (feeder == null) {
            feeder = findFeeder(level, rail);
            if (feeder == null) {
                data.remove(FEEDER);
                return 0;
            }
            data.putLong(FEEDER, feeder.getBlockPos().asLong());
            data.putLong(FEEDER_AGE, level.getGameTime());
        }
        return feeder.take(Math.min(want, DRAW_PER_TICK));
    }

    /** Обход связных электрических путей (по горизонтали и на склонах ±1) до первого фидера рядом. */
    public static @Nullable ElectricFeederBlockEntity findFeeder(ServerLevel level, BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() <= MAX_TRACKS) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(p.relative(d)) instanceof ElectricFeederBlockEntity f && f.energy.stored > 0) return f;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = p.relative(d).above(dy);
                    if (seen.contains(n) || !level.isLoaded(n) || !isElectric(level.getBlockState(n))) continue;
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return null;
    }
}
