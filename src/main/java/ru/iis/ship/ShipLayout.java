package ru.iis.ship;

/**
 * Форма материнского корабля (чистая геометрия, без Minecraft; D156, CONCEPT §5–§6, ответы владельца 13.11 и 16.11).
 * <p>
 * Упавший корпус 48×48 в плане и 23 в высоту — сплюснутое веретено с почти отвесными бортами, вытянутое по x. Внутри три яруса один над другим
 * (решение владельца: без отдельных измерений): верхний — «заражение», средний — «супермутанты и ядро», нижний — «нижние
 * отсеки» с выходом в пещеры под кораблём. Вдоль каждого яруса идёт главный коридор, по бокам — отсеки через каждые 8 блоков.
 * Ярусы связаны грузовыми лифтами: с первого на второй — в носу, со второго на третий — в корме, за залом ядра.
 * <p>
 * Локальные координаты: x 0..47 (нос → корма), z 0..47, y 0..22 (от днища). Всё детерминировано зерном корабля.
 */
public final class ShipLayout {
    private ShipLayout() {}

    public enum Kind {
        /** Мир не трогаем: клетка снаружи корпуса. */
        NONE, AIR, PLATING, DECK, BULKHEAD, LIGHT, GROWTH, LIFT, CHEST, SPAWNER, BOSS_CHEST
    }

    public static final int SIZE = 48, HEIGHT = 23;
    /** Полы ярусов снизу вверх: третий (нижние отсеки), второй (ядро), первый (заражение). */
    public static final int FLOOR_3 = 3, FLOOR_2 = 10, FLOOR_1 = 17;
    /** Лифт «первый ↔ второй ярус» и «второй ↔ третий»: клетка пола (x, z). */
    public static final int LIFT_A_X = 12, LIFT_B_X = 36, MID_Z = 24;
    /** Зал ядра на втором ярусе и место босса в нём. */
    public static final int HALL_X0 = 17, HALL_X1 = 31, HALL_Z0 = 15, HALL_Z1 = 33, BOSS_X = 24, BOSS_Z = 24;
    /** Главный сундук — за ядром, по пути к лифту на третий ярус. */
    public static final int BOSS_CHEST_X = 30;
    /** Выход в пещеры: дыра в днище третьего яруса. */
    public static final int EXIT_X0 = 40, EXIT_X1 = 42, EXIT_Z0 = 23, EXIT_Z1 = 25;

    private static final double CX = 23.5, CZ = 23.5, CY = 11, AX = 23.5, AZ = 15.5, AY = 12;

    public static boolean inside(int x, int y, int z) {
        if (y < 0 || y >= HEIGHT || x < 0 || x >= SIZE || z < 0 || z >= SIZE) return false;
        double dx = (x - CX) / AX, dz = (z - CZ) / AZ, dy = (y - CY) / AY;
        // по высоте — четвёртая степень: борта почти отвесные, под крышей верхнего яруса хватает места в рост
        return dx * dx + dz * dz + dy * dy * dy * dy <= 1.0;
    }

    private static boolean shell(int x, int y, int z) {
        return !inside(x - 1, y, z) || !inside(x + 1, y, z) || !inside(x, y - 1, z) || !inside(x, y + 1, z) || !inside(x, y, z - 1) || !inside(x, y, z + 1);
    }

    /** Номер яруса клетки (1 — верхний, 3 — нижний) по высоте. */
    public static int tier(int y) {
        return y >= FLOOR_1 ? 1 : y >= FLOOR_2 ? 2 : 3;
    }

    public static int floorOf(int tier) {
        return tier == 1 ? FLOOR_1 : tier == 2 ? FLOOR_2 : FLOOR_3;
    }

    static int hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) (h & 0x7FFFFFFF);
    }

    private static boolean hall(int x, int z) {
        return x >= HALL_X0 && x <= HALL_X1 && z >= HALL_Z0 && z <= HALL_Z1;
    }

    /** Центр отсека: здесь стоит сундук или рассадник. */
    private static boolean roomCentre(int x, int z) {
        return x % 8 == 4 && (z == 16 || z == 32);
    }

    public static Kind at(long seed, int x, int y, int z) {
        if (!inside(x, y, z)) return Kind.NONE;
        // пролом в носу на уровне первого яруса: вход с поверхности
        if (x <= 9 && z >= 21 && z <= 26 && y >= FLOOR_1 + 1 && y <= FLOOR_1 + 3) return Kind.AIR;
        // дыра в днище третьего яруса: выход в пещеры
        if (x >= EXIT_X0 && x <= EXIT_X1 && z >= EXIT_Z0 && z <= EXIT_Z1 && y <= FLOOR_3) return Kind.AIR;
        boolean floor = y == FLOOR_1 || y == FLOOR_2 || y == FLOOR_3;
        if (floor) {
            if (z == MID_Z && ((x == LIFT_A_X && y != FLOOR_3) || (x == LIFT_B_X && y != FLOOR_1))) return Kind.LIFT;
        }
        if (shell(x, y, z)) return hash(seed, x, y, z) % 19 == 0 ? Kind.GROWTH : Kind.PLATING;
        int tier = tier(y);
        if (floor) {
            if (y != FLOOR_3 && x % 8 == 4 && z == MID_Z) return Kind.LIGHT;   // светильник в потолке яруса ниже
            return tier == 1 && hash(seed, x, y, z) % 5 == 0 ? Kind.GROWTH : Kind.DECK;
        }
        if (y < FLOOR_3) return Kind.PLATING;   // под нижним полом — сплошное днище
        int rel = y - floorOf(tier);
        boolean inHall = tier == 2 && hall(x, z);
        if (inHall) {
            if (rel == 1 && x == BOSS_CHEST_X && z == MID_Z) return Kind.BOSS_CHEST;
            return Kind.AIR;
        }
        boolean wall = false, door = false;
        if (z == 21 || z == 27) {           // стены главного коридора, двери в отсеки
            wall = true;
            door = x % 8 == 4 && rel <= 2;
        } else if (x % 8 == 0 && (z < 21 || z > 27)) {   // перегородки между отсеками
            wall = true;
            door = (z == 17 || z == 31) && rel <= 2;
        }
        if (wall) {
            if (door) return Kind.AIR;
            int chance = tier == 1 ? 4 : tier == 2 ? 3 : 10;
            return hash(seed, x, y, z) % chance == 0 ? Kind.GROWTH : Kind.BULKHEAD;
        }
        if (rel == 1 && roomCentre(x, z) && inside(x, y + 1, z)) {
            // по кругу: сундук, рассадник, пусто — так в каждом ярусе гарантированно есть и добыча, и охрана
            // на втором ярусе отсеков мало (середину занимает зал ядра): там только сундук или рассадник
            int h = (x / 8 + (z == 16 ? 0 : 1) + tier + (int) Math.floorMod(seed, 3L)) % (tier == 2 ? 2 : 3);
            return h == 0 ? Kind.CHEST : h == 1 ? Kind.SPAWNER : Kind.AIR;
        }
        return Kind.AIR;
    }
}
