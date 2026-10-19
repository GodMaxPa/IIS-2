package ru.iis.lv;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Действия, отложенные до конца тика мира: взрывы машин. GT5 взрывал машину прямо внутри передачи энергии;
 * у нас взрыв ждёт конца тика, чтобы не ломать мир посреди обхода проводов. Итог тот же.
 */
public final class Deferred {
    private Deferred() {}

    private static final Map<ServerLevel, ArrayDeque<Runnable>> QUEUE = new WeakHashMap<>();

    public static void run(ServerLevel level, Runnable action) {
        QUEUE.computeIfAbsent(level, l -> new ArrayDeque<>()).add(action);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        ArrayDeque<Runnable> q = QUEUE.get(sl);
        if (q == null) return;
        // действия могут добавлять новые (цепочка взрывов) — они выполнятся в этом же цикле, но каждое один раз
        for (int guard = 0; !q.isEmpty() && guard < 10000; guard++) q.poll().run();
    }
}
