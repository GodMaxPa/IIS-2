package ru.iis.equipment.vehicle;

/**
 * Транспорт по эпохам (D128). Числа — предложение: скорость в блоках за тик (0.1 — шаг игрока, 0.4 — вагонетка), запас и расход в EU,
 * литр топлива даёт столько EU, сколько в генераторе GT. Места — в пикселях модели от центра (сиденье, на котором сидит игрок).
 */
public enum VehicleKind {
    //        id           эпоха  движение     шир высота ск.   разгон поворот проч запас       расход хол. груз камера шаг  сваливание
    STEAM_CAR("steam_car", "steam", Move.GROUND, 1.9F, 2.4F, 0.24, 0.004, 3.0F, 50, 1_500_000L, 12, 2, 1, 7F, 1.1F, 0, Fuel.SOLID, new double[][]{{-4, 17, 4}, {4, 17, 4}}),
    QUAD("quad", "lv", Move.GROUND, 1.6F, 1.3F, 0.38, 0.010, 4.5F, 40, 1_000_000L, 16, 2, 0, 5F, 1.1F, 0, Fuel.LIQUID, new double[][]{{0, 15, -8}}),
    MOTORBIKE("motorbike", "mv", Move.GROUND, 1.1F, 1.4F, 0.62, 0.016, 5.5F, 30, 1_000_000L, 16, 2, 0, 5F, 1.1F, 0, Fuel.LIQUID, new double[][]{{0, 15, -6}}),
    CAR("car", "mv", Move.GROUND, 2.1F, 1.8F, 0.60, 0.012, 3.8F, 70, 4_000_000L, 32, 4, 1, 7.5F, 1.1F, 0, Fuel.LIQUID, new double[][]{{7.8, 8, 0}, {-7.8, 8, 0}, {7, 8, -15}, {-7, 8, -15}}),
    TRUCK("truck", "hv", Move.GROUND, 2.6F, 2.9F, 0.44, 0.0075, 2.6F, 120, 8_000_000L, 64, 8, 6, 10F, 1.1F, 0, Fuel.LIQUID, new double[][]{{8.5, 21.5, 35}, {-8.5, 21.5, 35}}),
    BOAT("boat", "mv", Move.WATER, 2.2F, 1.2F, 0.55, 0.010, 3.6F, 60, 4_000_000L, 32, 4, 1, 8F, 0F, 0, Fuel.LIQUID, new double[][]{{6.5, 17.5, 0}, {-6.5, 17.5, 0}, {5, 17.5, -15}, {-5, 17.5, -15}}),
    PLANE("plane", "hv", Move.PLANE, 3.0F, 2.4F, 1.40, 0.012, 3.2F, 60, 8_000_000L, 128, 8, 0, 12F, 0.6F, 0.55, Fuel.LIQUID, new double[][]{{0, 20.5, -2}}),
    HELICOPTER("helicopter", "ev", Move.HELICOPTER, 3.0F, 3.0F, 0.85, 0.020, 4.0F, 90, 12_000_000L, 256, 32, 1, 12F, 0.6F, 0, Fuel.LIQUID, new double[][]{{7.5, 13.5, 19}, {-7.5, 13.5, 19}}),
    TANK("tank", "ev", Move.TRACKED, 4.2F, 2.3F, 0.26, 0.004, 3.0F, 400, 16_000_000L, 128, 16, 2, 11F, 1.1F, 0, Fuel.LIQUID, new double[][]{{-7, 25, -4}}),
    JET("jet", "iv", Move.PLANE, 3.2F, 2.4F, 3.00, 0.024, 3.0F, 80, 32_000_000L, 512, 32, 0, 14F, 0.6F, 1.0, Fuel.LIQUID, new double[][]{{0, 16, 28}});

    public enum Move { GROUND, TRACKED, WATER, PLANE, HELICOPTER }

    /** SOLID — горючее печи, LIQUID — жидкое топливо из таблицы генераторов GT. */
    public enum Fuel { SOLID, LIQUID }

    public final String id, era;
    public final Move move;
    public final float width, height;
    public final double maxSpeed, accel;
    public final float turn;
    public final int health;
    public final long tank;
    public final int burn, idle, cargoRows;
    public final float camera, step;
    public final double stall;
    public final Fuel fuel;
    public final double[][] seats;

    VehicleKind(String id, String era, Move move, float width, float height, double maxSpeed, double accel, float turn, int health, long tank, int burn, int idle, int cargoRows,
                float camera, float step, double stall, Fuel fuel, double[][] seats) {
        this.id = id;
        this.era = era;
        this.move = move;
        this.width = width;
        this.height = height;
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.turn = turn;
        this.health = health;
        this.tank = tank;
        this.burn = burn;
        this.idle = idle;
        this.cargoRows = cargoRows;
        this.camera = camera;
        this.step = step;
        this.stall = stall;
        this.fuel = fuel;
        this.seats = seats;
    }

    public boolean flies() {
        return move == Move.PLANE || move == Move.HELICOPTER;
    }

    public int cargoSlots() {
        return cargoRows * 9;
    }

    /** Секунд хода на полном баке при работе на полную. */
    public double rangeSeconds() {
        return tank / (double) burn / 20;
    }

    /** Скорость в км/ч для приборов: блок — метр. */
    public static int kmh(double blocksPerTick) {
        return (int) Math.round(Math.abs(blocksPerTick) * 20 * 3.6);
    }
}
