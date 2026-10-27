package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.lv.LvMachineType;
import ru.iis.multi.block.EbfBlockEntity;
import ru.iis.multi.logic.LargeBoilerLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Вольфрамосталь, нихром, большой ротор, вольфрамостальной котёл, схема Elite, сборщик EV (D98) по GT5-II. */
class TungstensteelDataTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject json(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    @Test
    void alloys() throws IOException {
        // GT_MachineRecipeLoader:1029 вольфрам + сталь -> 2 горячих слитка вольфрамостали, 3000 K, 480 EU/t
        JsonObject ts = json("processing/blast/tungstensteel_ingots");
        assertEquals(3000, ts.get("temp").getAsInt());
        assertEquals(480, ts.get("eut").getAsInt());
        assertEquals(2, ts.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertTrue(ts.get("temp").getAsInt() > EbfBlockEntity.KANTHAL_HEAT && ts.get("temp").getAsInt() <= EbfBlockEntity.NICHROME_HEAT);
        // :1033 4 никеля + хром -> 5 нихрома, 2700 K (кантала хватает)
        JsonObject ni = json("processing/blast/nichrome_ingots");
        assertEquals(2700, ni.get("temp").getAsInt());
        assertEquals(5, ni.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("#iis:wires/2x/nichrome", json("multi/coil_nichrome").getAsJsonObject("key").get("P").getAsString());
        // вольфрам по ИИС (GT_MachineRecipeLoader2:37-52): кислота в химреакторе EV, оксид, восстановление водородом в ЭДП
        JsonObject acid = json("processing/chemical_reactor/tungstic_acid_tungstate");
        assertEquals(1920, acid.get("eut").getAsInt());
        assertTrue(acid.get("eut").getAsInt() > LvMachineType.CHEMICAL_REACTOR_HV.voltage());
        assertEquals(7, acid.getAsJsonArray("outputs").get(1).getAsJsonObject().get("count").getAsInt());
        JsonObject w = json("processing/blast/tungsten_trioxide");
        assertEquals(3000, w.get("temp").getAsInt());
        assertEquals(12000, w.get("duration").getAsInt());
        assertEquals("iis:hydrogen", w.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("ingredient").getAsString());
        assertEquals("iis:pipe_large_plastic", json("lv/chemical_reactor_ev").getAsJsonObject("key").get("G").getAsString());
    }

    @Test
    void rotorBoilerCircuits() throws IOException {
        JsonObject rotor = json("processing/assembler/turbine_large_steel");   // ProcessingToolHead:157
        assertEquals(12, rotor.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(1600, rotor.get("eut").getAsInt());
        assertEquals(640, rotor.get("duration").getAsInt());
        assertTrue(rotor.get("eut").getAsInt() <= LvMachineType.ASSEMBLER_EV.voltage());
        assertTrue(rotor.get("eut").getAsInt() > LvMachineType.ASSEMBLER_HV.voltage());
        JsonObject boiler = json("multi/large_boiler_tungstensteel").getAsJsonObject("key");   // GT_Loader_MetaTileEntities:1160
        assertEquals("#c:circuits/elite", boiler.get("C").getAsString());
        assertEquals("#iis:cables/1x/aluminium", boiler.get("W").getAsString());
        assertEquals(1000, LargeBoilerLogic.Tier.TUNGSTENSTEEL.eut);
        JsonObject elite = json("processing/circuit_assembler/circuit_elite");   // :572
        assertEquals(480, elite.get("eut").getAsInt());
        assertEquals(288, elite.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(6, elite.getAsJsonArray("inputs").size());
        assertEquals(2, json("processing/assembler/capacitor").getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
        JsonObject asm = json("lv/assembler_ev").getAsJsonObject("key");
        assertEquals("iis:hull_ev", asm.get("M").getAsString());
        assertEquals("iis:robot_arm_ev", asm.get("A").getAsString());
        assertEquals("#c:rods/neodymium_magnetic", json("lv/electric_motor_ev").getAsJsonObject("key").get("I").getAsString());
        assertEquals(256, json("processing/polarizer/rod_neodymium").get("eut").getAsInt());
    }
}
