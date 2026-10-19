package ru.iis.energy.logic;

import java.util.HashSet;
import java.util.Set;

/**
 * Передача энергии GT5-II без «сети»: каждый пакет проходит по проводам поиском в глубину
 * (IEnergyConnected.Util#emitEnergyToNetwork + GT_MetaPipeEntity_Cable#transferElectricity).
 *
 * Правила оригинала:
 *  - на каждом блоке провода напряжение пакета падает на потерю провода; дошедшее до нуля — пропадает;
 *  - провод считает ток за тик; если напряжение выше предела или ток выше ампеража — провод греется
 *    (+100 за такой пакет, −1 за тик) и «съедает» весь пакет; перегретый провод сгорает;
 *  - потребитель получает уже уменьшенное напряжение; если оно выше его уровня — взрыв (решает потребитель).
 *
 * Грани: 0..5, противоположная грань — {@link #opposite(int)}.
 */
public final class EnergyNet {
    private EnergyNet() {}

    public interface Graph<N> {
        /** Сосед по грани или null. */
        N neighbour(N node, int face);

        /** Провод? Тогда spec/state/connected имеют смысл. */
        boolean isCable(N node);

        CableSpec spec(N cable);

        CableState state(N cable);

        boolean connected(N cable, int face);

        /** Подать пакет потребителю через его грань; вернуть принятые амперы (0, если это не потребитель). */
        long insert(N sink, int face, long voltage, long amperes);
    }

    public static int opposite(int face) {
        return face ^ 1;
    }

    /** Выпустить пакет из источника через указанные грани. Возвращает использованные амперы. */
    public static <N> long emit(Graph<N> g, N emitter, int[] faces, long voltage, long amperes, long tick) {
        long used = 0;
        Set<N> visited = new HashSet<>();
        visited.add(emitter);
        for (int face : faces) {
            if (used >= amperes) break;
            N n = g.neighbour(emitter, face);
            if (n == null) continue;
            int back = opposite(face);
            if (g.isCable(n)) {
                if (visited.add(n)) used += transfer(g, n, back, voltage, amperes - used, visited, tick);
            } else {
                used += g.insert(n, back, voltage, amperes - used);
            }
        }
        return used;
    }

    static <N> long transfer(Graph<N> g, N cable, int fromFace, long voltage, long amperes, Set<N> visited, long tick) {
        if (!g.connected(cable, fromFace)) return 0;
        CableSpec spec = g.spec(cable);
        CableState st = g.state(cable);
        st.touch(tick);
        long used = 0;
        voltage -= spec.lossPerBlock();
        if (voltage > 0) {
            for (int face = 0; face < 6 && amperes > used; face++) {
                if (face == fromFace || !g.connected(cable, face)) continue;
                N n = g.neighbour(cable, face);
                if (n == null || !visited.add(n)) continue;
                int back = opposite(face);
                if (g.isCable(n)) used += transfer(g, n, back, voltage, amperes - used, visited, tick);
                else used += g.insert(n, back, voltage, amperes - used);
            }
        }
        st.transferredAmps += used;
        st.voltageLast20 = Math.max(st.voltageLast20, voltage);
        st.amperageLast20 = Math.max(st.amperageLast20, st.transferredAmps);
        if (voltage > spec.voltage() || st.transferredAmps > spec.amperage()) {
            if (st.overheat > CableState.HEATING_LIMIT) st.burnt = true;
            else st.overheat += 100;
            return amperes;
        }
        return used;
    }

    /** GT_Utility#getTier: номер наименьшего уровня, вмещающего напряжение (8→0, 32→1, 33→2). */
    public static int tier(long voltage) {
        long v = 8;
        int t = 0;
        while (t < 15 && voltage > v) {
            v *= 4;
            t++;
        }
        return t;
    }

    /**
     * Особенность GT5 (BaseMetaTileEntity, вывод энергии): каждый выпущенный ампер стоит источнику
     * напряжение + 2^уровень EU (LV: 32 + 2 = 34 EU за ампер).
     */
    public static long emissionCost(long voltage) {
        return Math.max(voltage, voltage + (1L << tier(voltage)));
    }

    /** GT_Utility#applyElectricityDamage: урон = уровень напряжения × амперы × 4. */
    public static float shockDamage(long voltage, long amperes) {
        return tier(voltage) * amperes * 4f;
    }
}
