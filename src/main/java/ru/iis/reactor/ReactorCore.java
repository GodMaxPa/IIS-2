package ru.iis.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Расчёт ядерного реактора раз в секунду, без Minecraft. Правила как в IC2 2.2 (исходники закрыты, D5: написано заново) и как в открытых GT_RadioactiveCellIC_Item,
 * GT_CoolantCellIC_Item, GT_NeutronReflector_Item: сетка до 9 × 6, сначала проход тепла, потом проход энергии; стержень шлёт импульсы себе и четырём соседям;
 * тепло стержня = T(импульсы) × 4 × множитель стержня, оно расходится по соседям, которые его держат, остальное идёт в корпус.
 */
public final class ReactorCore {
    public enum Kind { FUEL, VENT, SPREAD_VENT, SWITCH, COOLANT, CONDENSATOR, PLATING, REFLECTOR, IRIDIUM_REFLECTOR }

    /**
     * Деталь. capacity — запас прочности или тепла. Для стержня: cells, energy, heatFactor, mox, replacedBy. Для отвода: reactorVent, selfVent.
     * Для бокового отвода: sideVent. Для теплообменника: sideSwitch, reactorSwitch. Для обшивки: maxHeatAdd, explosionFactor.
     */
    public record Part(Kind kind, int capacity, int cells, float energy, float heatFactor, boolean mox, String replacedBy, int reactorVent, int selfVent, int sideVent,
                       int sideSwitch, int reactorSwitch, int maxHeatAdd, float explosionFactor) {
        static Part fuel(int capacity, int cells, float energy, float heatFactor, boolean mox, String depleted) {
            return new Part(Kind.FUEL, capacity, cells, energy, heatFactor, mox, depleted, 0, 0, 0, 0, 0, 0, 1);
        }

        static Part vent(int capacity, int reactor, int self) {
            return new Part(Kind.VENT, capacity, 0, 0, 0, false, null, reactor, self, 0, 0, 0, 0, 1);
        }

        static Part spread(int side) {
            return new Part(Kind.SPREAD_VENT, 0, 0, 0, 0, false, null, 0, 0, side, 0, 0, 0, 1);
        }

        static Part exchanger(int capacity, int side, int reactor) {
            return new Part(Kind.SWITCH, capacity, 0, 0, 0, false, null, 0, 0, 0, side, reactor, 0, 1);
        }

        static Part simple(Kind kind, int capacity) {
            return new Part(kind, capacity, 0, 0, 0, false, null, 0, 0, 0, 0, 0, 0, 1);
        }

        static Part plating(int add, float factor) {
            return new Part(Kind.PLATING, 0, 0, 0, 0, false, null, 0, 0, 0, 0, 0, add, factor);
        }

        boolean storesHeat() {
            return kind == Kind.VENT || kind == Kind.SWITCH || kind == Kind.COOLANT || kind == Kind.CONDENSATOR;
        }
    }

    /** Детали по id предмета. Числа стержней — GT_Loader_Item_Block_And_Fluid:119–152; прочее — IC2 2.2. */
    public static final Map<String, Part> PARTS = Map.ofEntries(
            Map.entry("rod_uranium_1", Part.fuel(20000, 1, 2F, 1F, false, "rod_depleted_uranium_1")),
            Map.entry("rod_uranium_2", Part.fuel(20000, 2, 2F, 1F, false, "rod_depleted_uranium_2")),
            Map.entry("rod_uranium_4", Part.fuel(20000, 4, 2F, 1F, false, "rod_depleted_uranium_4")),
            Map.entry("rod_mox_1", Part.fuel(10000, 1, 2F, 1F, true, "rod_depleted_mox_1")),
            Map.entry("rod_mox_2", Part.fuel(10000, 2, 2F, 1F, true, "rod_depleted_mox_2")),
            Map.entry("rod_mox_4", Part.fuel(10000, 4, 2F, 1F, true, "rod_depleted_mox_4")),
            Map.entry("rod_thorium_1", Part.fuel(50000, 1, 0.4F, 0.25F, false, "rod_depleted_thorium_1")),
            Map.entry("rod_thorium_2", Part.fuel(50000, 2, 0.4F, 0.25F, false, "rod_depleted_thorium_2")),
            Map.entry("rod_thorium_4", Part.fuel(50000, 4, 0.4F, 0.25F, false, "rod_depleted_thorium_4")),
            Map.entry("rod_naquadah_1", Part.fuel(100000, 1, 2F, 1F, true, "rod_depleted_naquadah_1")),
            Map.entry("rod_naquadah_2", Part.fuel(100000, 2, 2F, 1F, true, "rod_depleted_naquadah_2")),
            Map.entry("rod_naquadah_4", Part.fuel(100000, 4, 2F, 1F, true, "rod_depleted_naquadah_4")),
            Map.entry("reactor_vent", Part.vent(1000, 0, 6)),
            Map.entry("reactor_vent_core", Part.vent(1000, 5, 5)),
            Map.entry("reactor_vent_gold", Part.vent(1000, 36, 20)),
            Map.entry("reactor_vent_diamond", Part.vent(1000, 0, 12)),
            Map.entry("reactor_vent_spread", Part.spread(4)),
            Map.entry("reactor_heat_switch", Part.exchanger(2500, 12, 4)),
            Map.entry("reactor_heat_switch_core", Part.exchanger(5000, 0, 72)),
            Map.entry("reactor_heat_switch_spread", Part.exchanger(5000, 36, 0)),
            Map.entry("reactor_heat_switch_diamond", Part.exchanger(10000, 24, 8)),
            Map.entry("coolant_10k", Part.simple(Kind.COOLANT, 10000)),
            Map.entry("coolant_30k", Part.simple(Kind.COOLANT, 30000)),
            Map.entry("coolant_60k", Part.simple(Kind.COOLANT, 60000)),
            Map.entry("coolant_he_60k", Part.simple(Kind.COOLANT, 60000)),
            Map.entry("coolant_he_180k", Part.simple(Kind.COOLANT, 180000)),
            Map.entry("coolant_he_360k", Part.simple(Kind.COOLANT, 360000)),
            Map.entry("coolant_nak_60k", Part.simple(Kind.COOLANT, 60000)),
            Map.entry("coolant_nak_180k", Part.simple(Kind.COOLANT, 180000)),
            Map.entry("coolant_nak_360k", Part.simple(Kind.COOLANT, 360000)),
            Map.entry("reactor_condensator", Part.simple(Kind.CONDENSATOR, 20000)),
            Map.entry("reactor_condensator_lap", Part.simple(Kind.CONDENSATOR, 100000)),
            Map.entry("reactor_plating", Part.plating(1000, 0.95F)),
            Map.entry("reactor_plating_heat", Part.plating(2000, 0.99F)),
            Map.entry("reactor_plating_explosive", Part.plating(500, 0.90F)),
            Map.entry("reactor_reflector", Part.simple(Kind.REFLECTOR, 30000)),
            Map.entry("reactor_reflector_thick", Part.simple(Kind.REFLECTOR, 120000)),
            Map.entry("neutron_reflector", Part.simple(Kind.IRIDIUM_REFLECTOR, 0)));

    public static final int BASE_MAX_HEAT = 10000, WIDTH = 9, HEIGHT = 6;
    /** IC2: базовая сила взрыва и потолок reactorExplosionPowerLimit из IC2.ini ИИС 1. */
    public static final float BASE_EXPLOSION = 10, EXPLOSION_LIMIT = 45;
    /** IC2: EU/t = выход × 5 × balance.energy.generator.nuclear (1.0 в IC2.ini ИИС 1). */
    public static final float EU_PER_OUTPUT = 5;

    /** Ячейка сетки: деталь и её износ или тепло. После расчёта BE переносит это в предмет. */
    public static final class Cell {
        public final String id;
        public final Part part;
        public int damage;
        public boolean removed;
        public String replacedBy;

        public Cell(String id, Part part, int damage) {
            this.id = id;
            this.part = part;
            this.damage = damage;
        }
    }

    public final Cell[] cells = new Cell[WIDTH * HEIGHT];
    public int columns = 3, heat, maxHeat = BASE_MAX_HEAT;
    public float output, heatEffect = 1;
    public boolean active;

    private Cell at(int x, int y) {
        if (x < 0 || y < 0 || x >= columns || y >= HEIGHT) return null;
        Cell c = cells[x + y * WIDTH];
        return c == null || c.removed || c.replacedBy != null ? null : c;
    }

    /** Секунда работы. Возвращает EU/t на следующую секунду. */
    public float second() {
        output = 0;
        maxHeat = BASE_MAX_HEAT;
        heatEffect = 1;
        for (int pass = 0; pass < 2; pass++) {
            boolean heatRun = pass == 0;
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < columns; x++) {
                    Cell c = at(x, y);
                    if (c != null) process(c, x, y, heatRun);
                }
            }
        }
        return euPerTick();
    }

    public float euPerTick() {
        return output * EU_PER_OUTPUT;
    }

    private void process(Cell c, int x, int y, boolean heatRun) {
        switch (c.part.kind()) {
            case FUEL -> fuel(c, x, y, heatRun);
            case VENT -> {
                if (heatRun) vent(c, x, y);
            }
            case SPREAD_VENT -> {
                if (heatRun) spread(c, x, y);
            }
            case SWITCH -> {
                if (heatRun) exchange(c, x, y);
            }
            case PLATING -> {
                if (heatRun) {
                    maxHeat += c.part.maxHeatAdd();
                    heatEffect *= c.part.explosionFactor();
                }
            }
            default -> {}
        }
    }

    private void fuel(Cell c, int x, int y, boolean heatRun) {
        if (!active) return;
        Part p = c.part;
        for (int i = 0; i < p.cells(); i++) {
            int pulses = 1 + p.cells() / 2;
            if (!heatRun) {
                for (int k = 0; k < pulses; k++) addOutput(c);
                pulse(c, x - 1, y, false);
                pulse(c, x + 1, y, false);
                pulse(c, x, y - 1, false);
                pulse(c, x, y + 1, false);
            } else {
                pulses += pulse(c, x - 1, y, true) + pulse(c, x + 1, y, true) + pulse(c, x, y - 1, true) + pulse(c, x, y + 1, true);
                int rodHeat = Math.round(pulses * (pulses + 1) / 2 * 4 * p.heatFactor());
                List<int[]> acceptors = new ArrayList<>();
                for (int[] d : NEIGHBOURS) {
                    Cell n = at(x + d[0], y + d[1]);
                    if (n != null && canStore(n)) acceptors.add(new int[]{x + d[0], y + d[1]});
                }
                while (!acceptors.isEmpty() && rodHeat > 0) {
                    int share = rodHeat / acceptors.size();
                    rodHeat -= share;
                    int[] a = acceptors.removeFirst();
                    rodHeat += alter(at(a[0], a[1]), share);
                }
                if (rodHeat > 0) heat += rodHeat;
            }
        }
        if (c.damage >= p.capacity() - 1) c.replacedBy = p.replacedBy();
        else if (heatRun) c.damage++;
    }

    private static final int[][] NEIGHBOURS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private int pulse(Cell source, int x, int y, boolean heatRun) {
        Cell n = at(x, y);
        if (n == null) return 0;
        return switch (n.part.kind()) {
            case FUEL -> {
                if (!heatRun) addOutput(n);
                yield 1;
            }
            case IRIDIUM_REFLECTOR -> {
                if (!heatRun) addOutput(source);
                yield 1;
            }
            case REFLECTOR -> {
                if (!heatRun) addOutput(source);
                if (++n.damage >= n.part.capacity()) n.removed = true;
                yield 1;
            }
            default -> 0;
        };
    }

    /** GT acceptUraniumPulse: у MOX и наквады выход растёт с нагревом: 1.5 × нагрев / предел + 1. */
    private void addOutput(Cell rod) {
        float k = rod.part.mox() ? 1.5F * heat / maxHeat + 1F : 1F;
        output += k * rod.part.energy();
    }

    private boolean canStore(Cell c) {
        if (c.part.kind() == Kind.CONDENSATOR) return c.damage < c.part.capacity() - 1;
        return c.part.storesHeat();
    }

    /** Изменение тепла детали; возвращает то, что не поместилось. Перегрев уничтожает деталь: GT alterHeat. */
    private int alter(Cell c, int delta) {
        if (c.part.kind() == Kind.CONDENSATOR) {
            if (delta <= 0) return delta;
            int room = c.part.capacity() - 1 - c.damage;
            int taken = Math.min(room, delta);
            c.damage += taken;
            return delta - taken;
        }
        int t = c.damage + delta;
        if (t > c.part.capacity()) {
            c.removed = true;
            return c.part.capacity() - t + 1;
        }
        if (t < 0) {
            c.damage = 0;
            return t;
        }
        c.damage = t;
        return 0;
    }

    private void vent(Cell c, int x, int y) {
        Part p = c.part;
        if (p.reactorVent() > 0) {
            int drain = Math.min(heat, p.reactorVent());
            heat -= drain;
            int back = alter(c, drain);
            if (c.removed) {
                heat += Math.max(back, 0);
                return;
            }
            heat += back;
        }
        alter(c, -p.selfVent());
    }

    private void spread(Cell c, int x, int y) {
        for (int[] d : NEIGHBOURS) {
            Cell n = at(x + d[0], y + d[1]);
            if (n != null && canStore(n) && n.part.kind() != Kind.CONDENSATOR) alter(n, -c.part.sideVent());
        }
    }

    /** Теплообменник уравнивает доли нагрева с соседями и корпусом, но не быстрее своей скорости. */
    private void exchange(Cell c, int x, int y) {
        int mine = 0;
        Part p = c.part;
        if (p.sideSwitch() > 0) {
            for (int[] d : NEIGHBOURS) {
                Cell n = at(x + d[0], y + d[1]);
                if (n == null || !canStore(n) || n.part.kind() == Kind.CONDENSATOR) continue;
                double my = c.damage * 100.0 / p.capacity(), other = n.damage * 100.0 / n.part.capacity();
                int add = rate(n.part.capacity(), my, other, p.sideSwitch());
                mine -= add;
                mine += alter(n, add);
                if (n.removed) mine = Math.min(mine, 0);
            }
        }
        if (p.reactorSwitch() > 0) {
            double my = c.damage * 100.0 / p.capacity(), core = heat * 100.0 / maxHeat;
            int add = rate(maxHeat, my, core, p.reactorSwitch());
            mine -= add;
            heat += add;
            if (heat < 0) {
                mine += heat;
                heat = 0;
            }
        }
        alter(c, mine);
    }

    /** Сколько передать соседу: + от меня ему, − от него мне (ступени IC2: при малом нагреве передача слабее). */
    private static int rate(int otherCapacity, double my, double other, int speed) {
        double mid = other + my / 2;
        int add = (int) Math.round(otherCapacity / 100.0 * mid);
        if (add > speed) add = speed;
        if (mid < 1.0) add = speed / 2;
        if (mid < 0.75) add = speed / 4;
        if (mid < 0.25) add = 1;
        long a = Math.round(other * 10), b = Math.round(my * 10);
        if (a > b) return -add;
        return a == b ? 0 : add;
    }

    /** Сила взрыва при перегреве: IC2: 10 + вклад деталей, множители обшивок, не больше предела из конфига. */
    public float explosionPower() {
        float boom = BASE_EXPLOSION, mul = 1;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < columns; x++) {
                Cell c = at(x, y);
                if (c == null) continue;
                switch (c.part.kind()) {
                    case FUEL -> boom += 2 * c.part.cells();
                    case COOLANT -> boom += 1 + c.part.capacity() / 30000F;
                    case REFLECTOR, IRIDIUM_REFLECTOR -> boom -= 1;
                    case PLATING -> mul *= c.part.explosionFactor();
                    default -> {}
                }
            }
        }
        return Math.max(1, Math.min(EXPLOSION_LIMIT, boom * mul));
    }
}
