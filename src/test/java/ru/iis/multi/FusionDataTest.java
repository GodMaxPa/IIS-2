package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.multi.block.FusionShape;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Термояд I (D116): форма из GT_MetaTileEntity_FusionComputer#checkMachine, рецепты GT_MachineRecipeLoader:1064-1088, 2552-2565. */
class FusionDataTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    @Test
    void shapeHasNoOverlaps() {
        assertEquals(32, FusionShape.COILS.length, "квест ИИС 1: 32 катушки");
        assertEquals(16, FusionShape.ENERGY.length);
        assertEquals(16, FusionShape.OUTPUT.length);
        assertEquals(16, FusionShape.INPUT.length);
        Set<String> seen = new HashSet<>();
        for (int[][] t : new int[][][]{FusionShape.COILS, FusionShape.CASINGS, FusionShape.ENERGY, FusionShape.OUTPUT, FusionShape.INPUT}) {
            for (int[] c : t) assertTrue(seen.add(c[0] + "," + c[1] + "," + c[2]), "cell twice: " + c[0] + "," + c[1] + "," + c[2]);
        }
    }

    @Test
    void europiumAndDuraniumCanBeMade() throws IOException {
        JsonObject eu = recipe("processing/fusion/europium_from_neodymium");
        assertEquals(150000000, eu.get("temp").getAsInt());
        assertEquals(24576, eu.get("eut").getAsInt());
        assertEquals(64, eu.get("duration").getAsInt());
        assertEquals("iis:molten_europium", eu.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(48, eu.getAsJsonArray("fluid_inputs").get(1).getAsJsonObject().get("amount").getAsInt());
        assertEquals(140000000, recipe("processing/fusion/duranium_from_gallium").get("temp").getAsInt());
    }

    @Test
    void everyFusionRecipeFitsMarkOne() throws IOException {
        try (var files = Files.list(RECIPES.resolve("processing/fusion"))) {
            for (Path p : files.toList()) {
                int start = JsonParser.parseString(Files.readString(p)).getAsJsonObject().get("temp").getAsInt();
                assertTrue(start <= 160003000, p + " needs Mark II");
            }
        }
    }

    @Test
    void controllerFollowsTheAssemblyLine() throws IOException {
        JsonObject c = recipe("processing/assembly_line/fusion_computer_luv");
        assertEquals("iis:fusion_coil", c.getAsJsonArray("inputs").get(0).getAsJsonObject().get("ingredient").getAsString());
        assertEquals("#c:circuits/ultimate", c.getAsJsonArray("inputs").get(1).getAsJsonObject().get("ingredient").getAsString());
        assertEquals(10, c.getAsJsonArray("inputs").size());
        assertEquals(30000, c.get("eut").getAsInt());
        assertEquals(2880, c.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals("iis:neutron_reflector", recipe("multi/fusion_coil").getAsJsonObject("key").get("T").getAsString());
    }

    @Test
    void plutoniumGivesItsIsotope() throws IOException {
        JsonObject c = recipe("processing/centrifuge/plutonium");
        assertEquals("iis:tiny_dust_plutonium241", c.getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(2000, c.getAsJsonArray("outputs").get(0).getAsJsonObject().get("chance").getAsInt());
    }
}
