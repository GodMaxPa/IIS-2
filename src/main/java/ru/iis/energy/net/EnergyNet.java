package ru.iis.energy.net;

import java.util.HashSet;
import java.util.Set;

/**
 * Передача EU от источника по проводам до потребителей (GT5: IEnergyConnected.Util#emitEnergyToNetwork,
 * GT_MetaPipeEntity_Cable#transferElectricity). Без зависимостей от Minecraft: мир описан через {@link World}.
 *
 * Обход в глубину, как в GT5: каждый провод снижает напряжение на свою потерю, считает амперы за тик и
 * передаёт дальше во все соединённые стороны, кроме той, откуда пришло. Каждый узел посещается за обход один раз.
 *
 * Отличие от GT5 (решение владельца D42): перегруженный провод не горит. Если ток превышает предел провода,
 * он пропускает только свой предел; если напряжение выше предела провода — не проводит вовсе. Оба случая
 * отмечаются в проводе для подсказок и приборов.
 */
public final class EnergyNet {
    private EnergyNet() {}

    /** Стороны как Direction.get3DDataValue(): вниз, вверх, север, юг, запад, восток. */
    public static final int[][] OFFSETS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    public static int opposite(int side) {
        return side ^ 1;
    }

    public record Pos(int x, int y, int z) {
        public Pos relative(int side) {
            int[] o = OFFSETS[side];
            return new Pos(x + o[0], y + o[1], z + o[2]);
        }
    }

    public interface Node {}

    /** Потребитель или накопитель: принимает пакеты, возвращает число принятых ампер. */
    public interface Sink extends Node {
        long inject(int side, long voltage, long amperage);
    }

    /** Провод. Счётчики за тик хранит сам провод (сбрасывает при смене тика). */
    public interface Cable extends Node {
        boolean connected(int side);

        long loss();

        long maxAmperage();

        long maxVoltage();

        long transferredThisTick();

        void recordTransfer(long amperes, long voltage);

        /** Отметить перегрузку: по току (overVoltage = false) или по напряжению. */
        void flagOverload(boolean overVoltage);
    }

    public interface World {
        Node at(Pos pos);
    }

    /**
     * Излучить энергию из источника в позиции origin через стороны sides.
     * @return сколько ампер принято
     */
    public static long emit(World world, Pos origin, int[] sides, long voltage, long amperage) {
        long used = 0;
        Set<Pos> visited = new HashSet<>();
        visited.add(origin);
        for (int side : sides) {
            if (used >= amperage) break;
            Pos p = origin.relative(side);
            if (!visited.add(p)) continue;
            Node n = world.at(p);
            int from = opposite(side);
            if (n instanceof Cable c) {
                used += transfer(world, c, p, from, voltage, amperage - used, visited);
            } else if (n instanceof Sink s) {
                used += s.inject(from, voltage, amperage - used);
            }
        }
        return used;
    }

    static long transfer(World world, Cable cable, Pos pos, int fromSide, long voltage, long amperage, Set<Pos> visited) {
        if (!cable.connected(fromSide)) return 0;
        voltage -= cable.loss();
        if (voltage <= 0) return 0;
        if (voltage > cable.maxVoltage()) {
            cable.flagOverload(true);
            return 0;
        }
        long room = cable.maxAmperage() - cable.transferredThisTick();
        if (room <= 0) {
            cable.flagOverload(false);
            return 0;
        }
        long allowed = Math.min(amperage, room);
        if (allowed < amperage) cable.flagOverload(false);
        long used = 0;
        for (int side = 0; side < 6 && used < allowed; side++) {
            if (side == fromSide || !cable.connected(side)) continue;
            Pos p = pos.relative(side);
            if (!visited.add(p)) continue;
            Node n = world.at(p);
            int from = opposite(side);
            if (n instanceof Cable c) {
                used += transfer(world, c, p, from, voltage, allowed - used, visited);
            } else if (n instanceof Sink s) {
                used += s.inject(from, voltage, allowed - used);
            }
        }
        cable.recordTransfer(used, voltage);
        return used;
    }
}
