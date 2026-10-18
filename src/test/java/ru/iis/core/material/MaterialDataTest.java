package ru.iis.core.material;

import org.junit.jupiter.api.Test;
import ru.iis.world.vein.VeinDefinition;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Проверка согласованности встроенных данных, которые генерирует tools/gen/gen_ores.py. */
class MaterialDataTest {

    @Test
    void materialsAreLoaded() {
        List<IISMaterial> mats = MaterialData.materials();
        assertTrue(mats.size() >= 80, "materials: " + mats.size());
        IISMaterial copper = mats.stream().filter(m -> m.id().equals("chalcopyrite")).findFirst().orElseThrow();
        assertEquals("Халькопирит", copper.ru());
        assertTrue(copper.hosts().contains("deepslate"));
    }

    @Test
    void everyVeinMaterialExistsWithHostForItsDimension() {
        Map<String, IISMaterial> byId = MaterialData.materials().stream().collect(Collectors.toMap(IISMaterial::id, m -> m));
        Map<String, Set<String>> hostsForDim = Map.of(
                "overworld", Set.of("stone", "deepslate"), "nether", Set.of("netherrack"), "end", Set.of("end_stone"));
        for (VeinDefinition v : MaterialData.veins()) {
            for (String layer : Arrays.asList(v.primary(), v.secondary(), v.between(), v.sporadic())) {
                if (layer == null) continue;
                IISMaterial m = byId.get(layer.substring(layer.indexOf(':') + 1));
                assertNotNull(m, v.name() + " uses unknown " + layer);
                for (String dim : v.dimensions()) {
                    assertTrue(m.hosts().containsAll(hostsForDim.get(dim)), v.name() + "/" + m.id() + " lacks host for " + dim);
                }
            }
        }
    }

    @Test
    void smallOresHaveBlocksForTheirDimensions() {
        Map<String, IISMaterial> byId = MaterialData.materials().stream().collect(Collectors.toMap(IISMaterial::id, m -> m));
        assertFalse(MaterialData.smallOres().isEmpty());
        for (MaterialData.SmallOre so : MaterialData.smallOres()) {
            IISMaterial m = byId.get(so.material().substring(so.material().indexOf(':') + 1));
            assertNotNull(m, so.name());
            if (so.dimensions().contains("overworld")) assertTrue(m.smallHosts().contains("stone"), so.name());
            if (so.dimensions().contains("nether")) assertTrue(m.smallHosts().contains("netherrack"), so.name());
            assertTrue(so.minY() >= -64 && so.maxY() <= 319 && so.minY() < so.maxY(), so.name());
        }
    }

    @Test
    void veinHeightsAreStretchedIntoModernWorld() {
        VeinDefinition copper = MaterialData.veins().stream().filter(v -> v.name().equals("copper")).findFirst().orElseThrow();
        assertEquals(-48, copper.minY()); // 10 * 1.6 - 64
        assertEquals(-16, copper.maxY()); // 30 * 1.6 - 64
        for (VeinDefinition v : MaterialData.veins()) {
            assertTrue(v.minY() >= -64 && v.maxY() <= 320, v.name());
        }
    }
}
