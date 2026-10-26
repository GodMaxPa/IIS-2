package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvMachineType;
import ru.iis.machine.Overclock;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.logic.FreezerLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Эпоха MV (D81): тиры машин, термоцентрифуга, вакуумный морозильник, горячие слитки по GT5-II. */
class MvTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe/processing");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    @Test
    void thermalCentrifugeTiers() {
        assertEquals("thermal_centrifuge_lv", LvMachineType.THERMAL_CENTRIFUGE.blockId());
        assertEquals("thermal_centrifuge_mv", LvMachineType.THERMAL_CENTRIFUGE_MV.blockId());
        assertEquals(32, LvMachineType.THERMAL_CENTRIFUGE.voltage());
        assertEquals(128, LvMachineType.THERMAL_CENTRIFUGE_MV.voltage());
        assertEquals("macerator_lv", LvMachineType.MACERATOR.blockId());
    }

    @Test
    void thermalCentrifugeRecipeIs500TicksAt48() throws IOException {
        JsonObject r = recipe("thermal_centrifuge/crushed_chalcopyrite");
        assertEquals(500, r.get("duration").getAsInt());
        assertEquals(48, r.get("eut").getAsInt());
        assertEquals(3, r.getAsJsonArray("outputs").size());
        assertEquals(2, recipe("thermal_centrifuge/purified_chalcopyrite").getAsJsonArray("outputs").size());
        // 48 EU/t на MV: ×4 = 192 > 128 — без разгона; на LV машине рецепт недоступен (48 > 32)
        Overclock.Result oc = Overclock.basicMachine(48, 500, VoltageTier.MV, 1);
        assertEquals(48, oc.euPerTick());
        assertEquals(500, oc.durationTicks());
        assertTrue(48 > LvMachineType.THERMAL_CENTRIFUGE.voltage());
    }

    @Test
    void freezerOverclockAndStructure() {
        // 120 EU/t: при MV (уже 2 люка LV = 64 В: GT_Utility#getTier округляет вверх) разгона нет, при HV ×4 и ÷2
        assertEquals(new BlastLogic.Run(120, 576), FreezerLogic.overclock(120, 576, 2));
        assertEquals(new BlastLogic.Run(480, 288), FreezerLogic.overclock(120, 576, 3));
        assertEquals(2, BlastLogic.machineTier(2 * 32));
        assertEquals(1, BlastLogic.machineTier(32));
        assertTrue(FreezerLogic.enoughCasings(16));
        assertFalse(FreezerLogic.enoughCasings(15));
        assertEquals(576, FreezerLogic.hotIngotTicks(192));
        assertEquals(1, FreezerLogic.hotIngotTicks(0));
    }

    @Test
    void hotIngotsComeFromHotBlastRecipes() throws IOException {
        JsonObject ir = recipe("blast/dust_iridium");
        assertEquals("iis:hot_ingot_iridium", ir.getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(2719, ir.get("temp").getAsInt());
        JsonObject fr = recipe("vacuum_freezer/hot_ingot_iridium");
        assertEquals("iis:ingot_iridium", fr.getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(120, fr.get("eut").getAsInt());
        // ниже 1750 K горячих слитков нет: сталь (1000 K) выходит обычным слитком
        assertFalse(Files.exists(Path.of("src/generated/resources/assets/iis/models/item/hot_ingot_steel.json")));
        assertEquals(50, recipe("vacuum_freezer/ice_capsule").get("duration").getAsInt());
    }
}
