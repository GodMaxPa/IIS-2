package ru.iis.equipment.vehicle;

/**
 * Езда и полёт без Minecraft (D128). Шаг считает тот, кто управляет: клиент водителя, а без водителя сервер.
 * Земля: газ и тормоз, поворот зависит от скорости (гусеницы разворачиваются на месте). Вода: то же, но только на плаву.
 * Самолёт: W и S двигают рычаг газа, нос следует за взглядом, ниже скорости сваливания машина проседает. Вертолёт: висит на винте, после раскрутки.
 */
public final class VehiclePhysics {
    private VehiclePhysics() {}

    static final double GRAVITY = 0.06, TERMINAL = -1.6, ROLL_DRAG = 0.985, AIR_DRAG = 0.995, BRAKE = 0.03;

    /** Что нажато у водителя и куда он смотрит. */
    public record Input(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down, float lookYaw, float lookPitch, boolean fuel, boolean occupied) {
        public static final Input NONE = new Input(false, false, false, false, false, false, 0, 0, false, false);
    }

    public static final class State {
        /** Вдоль корпуса, вниз-вверх и вбок (только вертолёт), блоков за тик. */
        public double speed, vy, strafe;
        public float yaw, pitch;
        /** Рычаг газа самолёта и обороты винта, 0..1. */
        public double throttle, rotor;
        public boolean onGround, inWater, horizontalCollision;
        /** На сколько корпус погружён, блоков. */
        public double depth;
        /** Сколько реально прошла машина за прошлый тик; меньше нуля — неизвестно. */
        public double actual = -1;
        /** За прошлый тик заехала на уступ. */
        public boolean climbed;
        /** Тиков в воздухе: через кочку сцепление пропадает не сразу. */
        public int airTicks;
        /** Грузовой комплект и прочее: множители скорости и разгона. */
        public double speedFactor = 1, accelFactor = 1;
    }

    /** Шаг: обновляет состояние и возвращает смещение x, y, z за тик. */
    public static double[] step(VehicleKind kind, State s, Input in) {
        return switch (kind.move) {
            case GROUND, TRACKED -> ground(kind, s, in);
            case WATER -> water(kind, s, in);
            case PLANE -> plane(kind, s, in);
            case HELICOPTER -> helicopter(kind, s, in);
        };
    }

    private static double[] motion(State s, double horizontalFromPitch) {
        double yaw = Math.toRadians(s.yaw);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double f = s.speed * horizontalFromPitch;
        return new double[]{-sin * f + cos * s.strafe, s.vy, cos * f + sin * s.strafe};
    }

    private static double throttleForce(VehicleKind k, State s, Input in) {
        if (!in.fuel) return 0;
        double a = 0;
        if (in.forward) a += s.speed < 0 ? BRAKE : k.accel * s.accelFactor;
        if (in.back) a -= s.speed > 0 ? BRAKE : k.accel * s.accelFactor * 0.6;
        return a;
    }

    private static void steer(VehicleKind k, State s, Input in, boolean pivot) {
        float dir = (in.left ? -1 : 0) + (in.right ? 1 : 0);
        if (dir == 0) return;
        double grip = pivot ? 1 : Math.min(1, Math.abs(s.speed) / (k.maxSpeed * 0.25));
        // на заднем ходу руль наоборот, как у машины
        double sign = s.speed < -0.005 && !pivot ? -1 : 1;
        s.yaw += (float) (dir * k.turn * grip * sign);
    }

    /** Упёрлась: скорость не больше той, что вышла на деле. Скольжение вдоль стены теряет только часть, лобовой удар всю. */
    private static void limitToActual(State s, double accel) {
        if (s.actual < 0 || !s.horizontalCollision) return;
        double most = s.actual + accel * 4;
        if (Math.abs(s.speed) > most) s.speed = Math.copySign(most, s.speed);
    }

    private static double[] ground(VehicleKind k, State s, Input in) {
        s.airTicks = s.onGround ? 0 : s.airTicks + 1;
        boolean grip = s.onGround || s.airTicks < 6;
        if (grip) {
            s.speed += throttleForce(k, s, in);
            if (in.up) s.speed = approach(s.speed, 0, BRAKE * 1.5);
            s.speed = s.speed * ROLL_DRAG;
            steer(k, s, in, k.move == VehicleKind.Move.TRACKED);
        } else {
            s.speed *= AIR_DRAG;
        }
        s.vy = s.onGround ? -0.08 : Math.max(TERMINAL, s.vy - GRAVITY);
        if (s.inWater && s.depth > 0.6) s.speed *= 0.8;
        if (s.climbed) s.speed *= k.climb;
        limitToActual(s, k.accel);
        double top = k.maxSpeed * s.speedFactor;
        s.speed = clamp(s.speed, -top * 0.35, top);
        if (Math.abs(s.speed) < 0.0005) s.speed = 0;
        return motion(s, 1);
    }

    private static double[] water(VehicleKind k, State s, Input in) {
        boolean afloat = s.inWater;
        if (afloat) {
            s.speed += throttleForce(k, s, in);
            s.speed *= 0.97;
            steer(k, s, in, false);
            // корпус садится на 0.4 блока: выталкивание с гашением
            s.vy = clamp((s.vy + (s.depth - 0.4) * 0.1) * 0.75, -0.3, 0.3);
        } else {
            s.speed *= s.onGround ? 0.7 : AIR_DRAG;
            s.vy = s.onGround ? -0.04 : Math.max(TERMINAL, s.vy - GRAVITY);
        }
        limitToActual(s, k.accel);
        double top = k.maxSpeed * s.speedFactor;
        s.speed = clamp(s.speed, -top * 0.3, top);
        return motion(s, 1);
    }

    private static double[] plane(VehicleKind k, State s, Input in) {
        if (in.fuel && in.forward) s.throttle = Math.min(1, s.throttle + 0.02);
        if (in.back || !in.fuel) s.throttle = Math.max(0, s.throttle - 0.02);
        double target = s.throttle * k.maxSpeed * s.speedFactor;
        double accel = k.accel * s.accelFactor;
        if (s.speed < target) s.speed = Math.min(target, s.speed + accel);
        else s.speed = Math.max(target, s.speed - accel * 0.5);
        // тормоз колёс
        if (s.onGround && in.back) s.speed = approach(s.speed, 0, BRAKE);
        double lift = k.stall <= 0 ? 1 : Math.min(1, s.speed / k.stall);
        float rate = (float) (k.turn * Math.min(1, s.speed / Math.max(0.05, k.stall)));
        if (in.occupied) {
            s.yaw += clampF(wrap(in.lookYaw - s.yaw), -rate, rate);
            if (in.left) s.yaw -= rate * 0.5F;
            if (in.right) s.yaw += rate * 0.5F;
            float wanted = clampF(in.lookPitch, -45, 45);
            s.pitch += clampF(wanted - s.pitch, -rate * 0.8F, rate * 0.8F);
        }
        if (s.onGround && (lift < 1 || s.pitch >= 0)) {
            // на земле нос не опустить, а хвост не поднять без скорости
            s.pitch = lift < 1 ? 0F : approachF(s.pitch, 0, 2F);
            s.vy = -0.04;
            return motion(s, 1);
        }
        double pitch = Math.toRadians(s.pitch);
        double climb = -Math.sin(pitch) * s.speed;
        // не хватает скорости — проседает; без мотора планирует
        double sink = (1 - lift) * 0.5;
        s.vy = approach(s.vy, climb - sink, 0.08);
        s.vy = Math.max(TERMINAL, s.vy);
        limitToActual(s, k.accel);
        return motion(s, Math.cos(pitch));
    }

    private static double[] helicopter(VehicleKind k, State s, Input in) {
        boolean engine = in.fuel && in.occupied;
        s.rotor = engine ? Math.min(1, s.rotor + 0.008) : Math.max(0, s.rotor - 0.004);
        double power = s.rotor < 0.6 ? 0 : (s.rotor - 0.6) / 0.4;
        double climb = 0;
        if (engine) {
            if (in.up) climb = 0.35 * power;
            if (in.down) climb = -0.3;
        }
        if (power > 0.05 && engine) {
            s.vy = approach(s.vy, climb, 0.03);
        } else if (s.onGround) {
            s.vy = -0.04;
        } else {
            // самовращение: без мотора садится, а не падает камнем
            s.vy = approach(s.vy, s.rotor > 0.2 ? -0.28 : TERMINAL, 0.02);
        }
        boolean flying = !s.onGround && power > 0.05;
        double fw = 0, sd = 0;
        if (flying && engine) {
            fw = (in.forward ? 1 : 0) - (in.back ? 0.5 : 0);
            sd = (in.right ? 1 : 0) - (in.left ? 1 : 0);
            s.yaw += clampF(wrap(in.lookYaw - s.yaw), -k.turn, k.turn);
        }
        double accel = k.accel * s.accelFactor;
        s.speed = approach(s.speed, fw * k.maxSpeed * s.speedFactor, accel);
        s.strafe = approach(s.strafe, -sd * k.maxSpeed * s.speedFactor * 0.5, accel);
        s.speed *= 0.995;
        if (s.onGround) {
            s.speed *= 0.8;
            s.strafe *= 0.8;
        }
        if (s.horizontalCollision) {
            s.speed *= 0.4;
            s.strafe *= 0.4;
        }
        // нос наклоняется при движении: это видно на модели
        s.pitch = approachF(s.pitch, (float) (s.speed / k.maxSpeed * 14), 1F);
        return motion(s, 1);
    }

    /** Удар: быстро ехал и почти встал за четыре тика. Выше шести блоков за тик — телепорт или смена измерения, а не езда. */
    public static boolean crashed(double before, double after) {
        return before > 0.5 && before < 6 && after < before * 0.3;
    }

    static double approach(double v, double target, double step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    static float approachF(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static float clampF(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    static float wrap(float deg) {
        float d = deg % 360F;
        if (d >= 180F) d -= 360F;
        if (d < -180F) d += 360F;
        return d;
    }
}
