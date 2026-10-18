package ru.iis.world.vein;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VeinTest {

    static VeinDefinition copperVein() {
        // Медная жила ИИС: y 10-30, вес 80, плотность 4, размер 24 (до переноса высот)
        return new VeinDefinition("copper", 10, 30, 80, 4, 24,
                "iis:chalcopyrite", "iis:iron", "iis:pyrite", "iis:copper", Set.of("overworld"));
    }

    @Test
    void heightMappingMatchesOwnerDecision() {
        assertEquals(-56, HeightMapping.stretch(5));
        assertEquals(0, HeightMapping.stretch(40));
        assertEquals(64, HeightMapping.stretch(80));
        assertEquals(144, HeightMapping.stretch(130));
        VeinDefinition v = HeightMapping.stretch(copperVein());
        assertEquals(-48, v.minY());
        assertEquals(-16, v.maxY());
    }

    @Test
    void definitionEnforcesMinimumHeightRange() {
        VeinDefinition v = new VeinDefinition("x", 10, 11, 1, 1, 1, "a", null, null, null, Set.of());
        assertEquals(16, v.maxY());
    }

    @Test
    void gridCentersAreEveryThreeChunks() {
        assertEquals(1, VeinGrid.centerChunk(0));
        assertEquals(1, VeinGrid.centerChunk(1));
        assertEquals(1, VeinGrid.centerChunk(2));
        assertEquals(4, VeinGrid.centerChunk(3));
        assertEquals(4, VeinGrid.centerChunk(5));
        assertEquals(-2, VeinGrid.centerChunk(-1));
        assertEquals(-2, VeinGrid.centerChunk(-3));
        assertEquals(-5, VeinGrid.centerChunk(-4));
        // каждый центр — центр своего квадрата
        for (int c = -50; c <= 50; c++) {
            int center = VeinGrid.centerChunk(c);
            assertTrue(Math.abs(center - c) <= 1, "chunk " + c + " -> " + center);
            assertEquals(center, VeinGrid.centerChunk(center));
        }
    }

    @Test
    void randomIsDeterministicPerSquare() {
        assertEquals(VeinGrid.randomFor(42, 1, 4).nextLong(), VeinGrid.randomFor(42, 1, 4).nextLong());
        assertNotEquals(VeinGrid.randomFor(42, 1, 4).nextLong(), VeinGrid.randomFor(42, 4, 1).nextLong());
        assertNotEquals(VeinGrid.randomFor(42, 1, 4).nextLong(), VeinGrid.randomFor(43, 1, 4).nextLong());
    }

    @Test
    void pickFollowsWeights() {
        VeinDefinition a = new VeinDefinition("a", 0, 10, 300, 1, 1, "a", null, null, null, Set.of());
        VeinDefinition b = new VeinDefinition("b", 0, 10, 100, 1, 1, "b", null, null, null, Set.of());
        VeinDefinition zero = new VeinDefinition("z", 0, 10, 0, 1, 1, "z", null, null, null, Set.of());
        Random r = new Random(1);
        Map<String, Integer> counts = new HashMap<>();
        int n = 40_000;
        for (int i = 0; i < n; i++) counts.merge(VeinGrid.pick(List.of(zero, a, b), r).name(), 1, Integer::sum);
        assertNull(counts.get("z"));
        double shareA = counts.get("a") / (double) n;
        assertEquals(0.75, shareA, 0.01);
        assertNull(VeinGrid.pick(List.of(), r));
        assertNull(VeinGrid.pick(List.of(zero), r));
    }

    @Test
    void shapeStaysWithinLayersAndBounds() {
        VeinDefinition v = HeightMapping.stretch(copperVein());
        List<int[]> placed = new ArrayList<>();
        Map<VeinShape.Layer, Integer> perLayer = new EnumMap<>(VeinShape.Layer.class);
        int originX = 160, originZ = -320;
        VeinShape.generate(v, originX, originZ, new Random(7), (x, y, z, layer, mat) -> {
            placed.add(new int[]{x, y, z});
            perLayer.merge(layer, 1, Integer::sum);
            switch (layer) {
                case PRIMARY -> assertEquals("iis:chalcopyrite", mat);
                case SECONDARY -> assertEquals("iis:iron", mat);
                case BETWEEN -> assertEquals("iis:pyrite", mat);
                case SPORADIC -> assertEquals("iis:copper", mat);
            }
        });
        assertFalse(placed.isEmpty());
        int minY = placed.stream().mapToInt(p -> p[1]).min().orElseThrow();
        int maxY = placed.stream().mapToInt(p -> p[1]).max().orElseThrow();
        assertTrue(maxY - minY <= 7, "vein thickness " + (maxY - minY));
        assertTrue(minY >= v.minY() - 1 && maxY <= v.maxY() + 1, "y range " + minY + ".." + maxY);
        for (int[] p : placed) {
            assertTrue(p[0] >= originX - v.size() && p[0] <= originX + 16 + v.size());
            assertTrue(p[2] >= originZ - v.size() && p[2] <= originZ + 16 + v.size());
        }
        // основная и вторичная руда — главные слои жилы
        assertTrue(perLayer.get(VeinShape.Layer.PRIMARY) > perLayer.get(VeinShape.Layer.SPORADIC));
        assertTrue(perLayer.get(VeinShape.Layer.SECONDARY) > perLayer.get(VeinShape.Layer.BETWEEN));
    }

    @Test
    void shapeIsDeterministic() {
        VeinDefinition v = copperVein();
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        VeinShape.generate(v, 0, 0, new Random(99), (x, y, z, l, m) -> a.add(x + "," + y + "," + z + l));
        VeinShape.generate(v, 0, 0, new Random(99), (x, y, z, l, m) -> b.add(x + "," + y + "," + z + l));
        assertEquals(a, b);
    }

    @Test
    void missingLayersAreSkipped() {
        VeinDefinition onlyPrimary = new VeinDefinition("p", 0, 20, 1, 3, 8, "iis:tin", null, null, null, Set.of());
        VeinShape.generate(onlyPrimary, 0, 0, new Random(3),
                (x, y, z, layer, mat) -> assertEquals(VeinShape.Layer.PRIMARY, layer));
    }
}
