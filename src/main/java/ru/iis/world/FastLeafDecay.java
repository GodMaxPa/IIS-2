package ru.iis.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Быстрое опадание листвы (мод FastLeafDecay сборки ИИС 1.19.1, `config/fastleafdecay.cfg`: 4–11 тиков, D87).
 * Когда рядом с природной листвой что-то пропало или листва пересчитала расстояние до бревна, она проверяется
 * через 4–11 тиков; оторванная от дерева (расстояние 7) опадает сразу, как от случайного тика ванили, — и будит соседей.
 */
public final class FastLeafDecay {
    private FastLeafDecay() {}

    public static final int MIN_DELAY = 4, MAX_DELAY = 11;
    /** Предел очереди на мир: защита от бесконечного леса под поршнями. */
    private static final int MAX_QUEUED = 20000;

    private record Check(long due, BlockPos pos) {}

    private static final class Queue {
        final PriorityQueue<Check> due = new PriorityQueue<>((a, b) -> Long.compare(a.due, b.due));
        final Set<BlockPos> queued = new HashSet<>();
    }

    private static final Map<ServerLevel, Queue> QUEUES = new WeakHashMap<>();

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !ru.iis.IISConfig.fastLeafDecay()) return;
        BlockState state = e.getState();
        // пропало бревно (на его месте воздух) или листва сменила расстояние
        if (!state.isAir() && !(state.getBlock() instanceof LeavesBlock)) return;
        BlockPos pos = e.getPos();
        schedule(level, pos);
        for (Direction d : Direction.values()) schedule(level, pos.relative(d));
    }

    private static void schedule(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof LeavesBlock) || s.getValue(LeavesBlock.PERSISTENT)) return;
        Queue q = QUEUES.computeIfAbsent(level, l -> new Queue());
        if (q.queued.size() >= MAX_QUEUED || !q.queued.add(pos.immutable())) return;
        long delay = MIN_DELAY + level.getRandom().nextInt(MAX_DELAY - MIN_DELAY + 1);
        q.due.add(new Check(level.getGameTime() + delay, pos.immutable()));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Queue q = QUEUES.get(level);
        if (q == null) return;
        long now = level.getGameTime();
        while (!q.due.isEmpty() && q.due.peek().due <= now) {
            BlockPos p = q.due.poll().pos;
            q.queued.remove(p);
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            // случайный тик листвы ванили: опадает (с дропом), только если оторвана от дерева и не поставлена игроком
            if (s.getBlock() instanceof LeavesBlock) s.randomTick(level, p, level.getRandom());
        }
    }
}
