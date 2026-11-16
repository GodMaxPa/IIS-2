package ru.iis.ship;

import org.junit.jupiter.api.Test;
import ru.iis.ship.ShipLayout.Kind;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Геометрия материнского корабля (D156): корпус замкнут, ярусы проходимы и связаны лифтами, в каждом есть добыча и охрана. */
class ShipLayoutTest {
    private static final long[] SEEDS = {0L, 1L, 2L, 123456789L, -987654321L};

    private static boolean open(Kind k) {
        return k == Kind.AIR || k == Kind.CHEST || k == Kind.SPAWNER || k == Kind.BOSS_CHEST;
    }

    @Test
    void everyTierHasLootAndGuards() {
        for (long seed : SEEDS) {
            Map<Integer, int[]> count = new java.util.HashMap<>();
            int bossChests = 0;
            for (int x = 0; x < ShipLayout.SIZE; x++) for (int y = 0; y < ShipLayout.HEIGHT; y++) for (int z = 0; z < ShipLayout.SIZE; z++) {
                Kind k = ShipLayout.at(seed, x, y, z);
                int[] c = count.computeIfAbsent(ShipLayout.tier(y), t -> new int[2]);
                if (k == Kind.CHEST) c[0]++;
                if (k == Kind.SPAWNER) c[1]++;
                if (k == Kind.BOSS_CHEST) bossChests++;
            }
            for (int tier = 1; tier <= 3; tier++) {
                assertTrue(count.get(tier)[0] >= 2, "seed " + seed + " tier " + tier + " chests " + count.get(tier)[0]);
                assertTrue(count.get(tier)[1] >= 2, "seed " + seed + " tier " + tier + " spawners " + count.get(tier)[1]);
            }
            assertEquals(1, bossChests, "one main chest behind the core");
        }
    }

    @Test
    void liftsComeInPairs() {
        assertEquals(Kind.LIFT, ShipLayout.at(0, ShipLayout.LIFT_A_X, ShipLayout.FLOOR_1, ShipLayout.MID_Z));
        assertEquals(Kind.LIFT, ShipLayout.at(0, ShipLayout.LIFT_A_X, ShipLayout.FLOOR_2, ShipLayout.MID_Z));
        assertEquals(Kind.LIFT, ShipLayout.at(0, ShipLayout.LIFT_B_X, ShipLayout.FLOOR_2, ShipLayout.MID_Z));
        assertEquals(Kind.LIFT, ShipLayout.at(0, ShipLayout.LIFT_B_X, ShipLayout.FLOOR_3, ShipLayout.MID_Z));
        // над каждой площадкой можно встать
        for (int[] p : new int[][]{{ShipLayout.LIFT_A_X, ShipLayout.FLOOR_1}, {ShipLayout.LIFT_A_X, ShipLayout.FLOOR_2}, {ShipLayout.LIFT_B_X, ShipLayout.FLOOR_2},
                {ShipLayout.LIFT_B_X, ShipLayout.FLOOR_3}}) {
            assertEquals(Kind.AIR, ShipLayout.at(0, p[0], p[1] + 1, ShipLayout.MID_Z));
            assertEquals(Kind.AIR, ShipLayout.at(0, p[0], p[1] + 2, ShipLayout.MID_Z));
        }
        assertTrue(ShipLayout.FLOOR_1 - ShipLayout.FLOOR_2 <= LiftBlock.RANGE && ShipLayout.FLOOR_2 - ShipLayout.FLOOR_3 <= LiftBlock.RANGE);
    }

    /** Пешком (без прыжков через стены) с лифта яруса достижимы все его сундуки, рассадники и вторая площадка. */
    @Test
    void tiersAreWalkable() {
        for (long seed : SEEDS) {
            for (int tier = 1; tier <= 3; tier++) {
                int y = ShipLayout.floorOf(tier) + 1;
                int startX = tier == 3 ? ShipLayout.LIFT_B_X : ShipLayout.LIFT_A_X;
                Set<Long> seen = new HashSet<>();
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{startX, ShipLayout.MID_Z});
                seen.add(startX * 100L + ShipLayout.MID_Z);
                while (!queue.isEmpty()) {
                    int[] c = queue.poll();
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = c[0] + d[0], nz = c[1] + d[1];
                        if (!open(ShipLayout.at(seed, nx, y, nz)) || !open(ShipLayout.at(seed, nx, y + 1, nz))) continue;
                        if (seen.add(nx * 100L + nz)) queue.add(new int[]{nx, nz});
                    }
                }
                for (int x = 0; x < ShipLayout.SIZE; x++) for (int z = 0; z < ShipLayout.SIZE; z++) {
                    Kind k = ShipLayout.at(seed, x, y, z);
                    if (k == Kind.CHEST || k == Kind.SPAWNER || k == Kind.BOSS_CHEST) {
                        assertTrue(seen.contains(x * 100L + z), "seed " + seed + " tier " + tier + ": " + k + " at " + x + "," + z + " is walled off");
                    }
                }
                if (tier == 2) assertTrue(seen.contains(ShipLayout.LIFT_B_X * 100L + ShipLayout.MID_Z), "the way from lift A to lift B on the core tier");
                if (tier == 1) {
                    assertTrue(seen.contains(8 * 100L + ShipLayout.MID_Z), "the breach in the nose leads to the first tier");
                    // пролом выходит наружу: крайняя клетка носа открыта, за ней — мир
                    int nose = 0;
                    while (ShipLayout.at(seed, nose, y, ShipLayout.MID_Z) == Kind.NONE) nose++;
                    assertEquals(Kind.AIR, ShipLayout.at(seed, nose, y, ShipLayout.MID_Z), "the nose is open at the breach");
                    assertTrue(nose <= 9 && seen.contains((nose + 1) * 100L + ShipLayout.MID_Z) || open(ShipLayout.at(seed, nose + 1, y, ShipLayout.MID_Z)), "the breach is a way in");
                }
                if (tier == 3) assertTrue(seen.contains((ShipLayout.EXIT_X0 - 1) * 100L + ShipLayout.MID_Z), "the exit hole is reachable on the third tier");
            }
        }
    }

    /** Корпус замкнут: снаружи внутрь ведут только пролом в носу и дыра в днище. */
    @Test
    void hullIsClosed() {
        long seed = 7L;
        Map<Kind, Integer> kinds = new EnumMap<>(Kind.class);
        int leaks = 0;
        for (int x = 0; x < ShipLayout.SIZE; x++) for (int y = 0; y < ShipLayout.HEIGHT; y++) for (int z = 0; z < ShipLayout.SIZE; z++) {
            Kind k = ShipLayout.at(seed, x, y, z);
            kinds.merge(k, 1, Integer::sum);
            if (!open(k)) continue;
            boolean breach = x <= 9 && z >= 21 && z <= 26 && y > ShipLayout.FLOOR_1;
            boolean exit = x >= ShipLayout.EXIT_X0 && x <= ShipLayout.EXIT_X1 && z >= ShipLayout.EXIT_Z0 && z <= ShipLayout.EXIT_Z1 && y <= ShipLayout.FLOOR_3;
            if (breach || exit) continue;
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                if (ShipLayout.at(seed, x + d[0], y + d[1], z + d[2]) == Kind.NONE) leaks++;
            }
        }
        assertEquals(0, leaks, "open cells touching the outside");
        assertTrue(kinds.get(Kind.PLATING) > 3000 && kinds.get(Kind.AIR) > 3000, kinds.toString());
    }
}
