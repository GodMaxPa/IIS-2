package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.multi.logic.LargeBoilerLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Титан, обычный ротор, титановый котёл, схема Data (D97) по GT5-II. */
class TitaniumDataTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject json(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    @Test
    void titaniumChain() throws IOException {
        // GT_MachineRecipeLoader:1179 рутил + 2 угля + 4000 л хлора -> 1000 л TiCl4 (500 т, 480 EU/t)
        JsonObject ticl4 = json("processing/chemical_reactor/gt_1179");
        assertEquals("iis:titaniumtetrachloride", ticl4.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(480, ticl4.get("eut").getAsInt());
        // :1043 ЭДП: 2 магния + 1000 л TiCl4 -> горячий титан + 2 MgCl2, 2140 K — нужен кантал (2700 K)
        JsonObject blast = json("processing/blast/titanium_tetrachloride");
        assertEquals(2140, blast.get("temp").getAsInt());
        assertEquals(800, blast.get("duration").getAsInt());
        assertEquals("iis:hot_ingot_titanium", blast.getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(2, blast.getAsJsonArray("outputs").get(1).getAsJsonObject().get("count").getAsInt());
        assertTrue(blast.get("temp").getAsInt() > ru.iis.multi.block.EbfBlockEntity.CUPRONICKEL_HEAT);
        assertTrue(blast.get("temp").getAsInt() <= ru.iis.multi.block.EbfBlockEntity.KANTHAL_HEAT);
        assertTrue(Files.exists(RECIPES.resolve("processing/electrolyzer/decompose_bauxite.json")));   // рутил
        assertTrue(Files.exists(RECIPES.resolve("processing/electrolyzer/gt_3352.json")));             // хлор из соли
    }

    @Test
    void normalRotorAndBoiler() throws IOException {
        JsonObject rotor = json("processing/assembler/turbine_normal_steel");   // ProcessingToolHead:156
        assertEquals(8, rotor.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("#iis:long_rods/titanium", rotor.getAsJsonArray("inputs").get(1).getAsJsonObject().get("ingredient").getAsString());
        assertEquals(400, rotor.get("eut").getAsInt());
        assertEquals(320, rotor.get("duration").getAsInt());
        assertFalse(Files.exists(RECIPES.resolve("processing/assembler/turbine_huge_steel.json")));    // америций — позже (большой — D98)
        JsonObject boiler = json("multi/large_boiler_titanium").getAsJsonObject("key");               // GT_Loader_MetaTileEntities:1159
        assertEquals("#c:circuits/data", boiler.get("C").getAsString());
        assertEquals("iis:titanium_firebox_casing", boiler.get("M").getAsString());
        assertEquals(800, LargeBoilerLogic.Tier.TITANIUM.eut);
    }

    @Test
    void dataCircuit() throws IOException {
        JsonObject data = json("processing/circuit_assembler/circuit_data");   // :571
        assertEquals(90, data.get("eut").getAsInt());
        assertEquals(144, data.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(5, data.getAsJsonArray("inputs").size());
        JsonObject board = json("processing/chemical_reactor/circuit_board_plastic");   // :446
        assertEquals(125, board.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
    }
}
