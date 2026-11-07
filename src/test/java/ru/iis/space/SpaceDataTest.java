package ru.iis.space;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Космос, первый этап (D115): рецепты scripts/Galacticraft.zs и GT, жилы Луны; Марс и астероиды пока не генерируются (D17). */
class SpaceDataTest {
    private static final Path GEN = Path.of("src/generated/resources");
    private static final Path RECIPES = GEN.resolve("data/iis/recipe");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    private static String key(String rel, String letter) throws IOException {
        return recipe(rel).getAsJsonObject("key").get(letter).getAsString();
    }

    @Test
    void compressedPlatesFollowProcessingPlate() throws IOException {
        JsonObject r = recipe("processing/implosion/compressed_steel");
        assertEquals("#c:plates/steel", r.getAsJsonArray("inputs").get(0).getAsJsonObject().get("ingredient").getAsString());
        assertEquals(4, r.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(2, r.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void rocketPartsFollowTheScript() throws IOException {
        assertEquals("iis:compressed_bronze", key("space/heavy_ingot_t1", "C"));
        assertEquals("#iis:double_plates/titanium", key("space/heavy_ingot_t1", "T"));
        assertEquals("iis:heavy_plating", key("space/nose_cone", "H"));
        assertEquals("iis:sensor_ev", key("space/nose_cone", "E"));
        assertEquals("iis:titanium_firebox_casing", key("space/rocket_engine_t1", "F"));
        assertEquals("iis:pipe_huge_titanium", key("space/rocket_engine_t1", "P"));
        assertEquals("iis:schematic_3x3", key("space/nasa_workbench", "S"));
        assertEquals("iis:compressed_iron", key("space/launch_pad", "I"));
    }

    @Test
    void oxygenTankFillsInTheCanner() throws IOException {
        JsonObject r = recipe("processing/fluid_canner/oxygen_tank_light");
        assertEquals(20000, r.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(900, r.getAsJsonArray("inputs").get(0).getAsJsonObject().getAsJsonObject("ingredient").getAsJsonObject("components").get("minecraft:damage").getAsInt());
    }

    /** chem.py разбирает sRocketFuel и 1,1-диметилгидразин (GT_MachineRecipeLoader:3216). */
    @Test
    void rocketFuelHasARecipe() throws IOException {
        JsonObject r = recipe("processing/mixer/gt_3216");
        assertEquals("iis:rocket_fuel", r.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(2000, r.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("amount").getAsInt());
    }

    @Test
    void theMoonIsADimensionWithItsVeins() throws IOException {
        assertTrue(Files.exists(GEN.resolve("data/iis/dimension/moon.json")));
        String veins = Files.readString(Path.of("src/main/resources/iis/veins.json"));
        assertTrue(veins.contains("iis:moon"));
        assertTrue(Files.exists(GEN.resolve("assets/iis/items/ore_meteoric_iron.json")));
        assertTrue(Files.exists(GEN.resolve("data/iis/dimension/mars.json")));
        assertTrue(veins.contains("iis:mars"));
        assertTrue(Files.exists(GEN.resolve("assets/iis/items/ore_naquadah.json")), "naquadah lies on Mars");
        assertTrue(Files.exists(GEN.resolve("assets/iis/items/ore_desh.json")), "desh lies on Mars");
    }
}
