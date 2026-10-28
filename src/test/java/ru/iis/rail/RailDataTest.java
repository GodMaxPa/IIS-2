package ru.iis.rail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Рецепты железной дороги по GT5-II и скрипту RailCraft.zs ИИС 1 (D102), числа путей Railcraft. */
class RailDataTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe/rail");
    private static final Path OVERRIDES = Path.of("src/generated/resources/packs/iis_overrides/data/minecraft/recipe");

    private static JsonObject recipe(String id) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(id + ".json"))).getAsJsonObject();
    }

    private static int outCount(JsonObject r) {
        return r.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt();
    }

    @Test
    void tiesAndRailbedsAsInIis1() throws IOException {
        JsonObject tie = recipe("tie_wood");                    // RailCraft.zs:575
        assertEquals("iis:chemical_bath", tie.get("type").getAsString());
        assertEquals(500, tie.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(160, tie.get("duration").getAsInt());
        JsonObject bed = recipe("railbed_wood");                 // GT_MachineRecipeLoader:1448
        assertEquals(4, bed.get("circuit").getAsInt());
        assertEquals(4, bed.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void railsFromRodsInTheBender() throws IOException {
        assertEquals(4, outCount(recipe("rail_standard_iron")));                // :1196
        assertEquals(8, outCount(recipe("rail_standard_steel")));               // :1199
        assertEquals(16, outCount(recipe("rail_standard_titanium")));           // :1201
        assertEquals(24, outCount(recipe("rail_reinforced_tungsten_steel")));   // :1202
        assertEquals(30, recipe("rail_reinforced_tungsten_steel").get("eut").getAsInt());
        assertEquals(48, outCount(recipe("rebar_tungsten_steel")));             // :1210
    }

    @Test
    void tracksAsInTheScript() throws IOException {
        assertEquals(32, outCount(recipe("track_rail_from_bed")));              // Railcraft ModuleCore: 32 rails
        assertEquals("minecraft:rail", recipe("track_rail_from_bed").getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
        JsonObject reinf = recipe("track_reinforced");                          // RailCraft.zs:377
        assertEquals(16, outCount(reinf));
        assertEquals(576, reinf.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(1, reinf.get("circuit").getAsInt());
        assertEquals(2, recipe("track_switch").get("circuit").getAsInt());     // RailCraft.zs:354
        assertEquals(3, recipe("track_wye").get("circuit").getAsInt());
        assertEquals(8, outCount(recipe("track_electric_junction")));          // :402
    }

    @Test
    void vanillaRailCraftingIsReplaced() {
        for (String r : new String[]{"rail", "powered_rail", "detector_rail", "activator_rail"}) {
            assertTrue(Files.exists(OVERRIDES.resolve(r + ".json")), r);
        }
    }

    @Test
    void trackSpeedsOfRailcraft() {
        assertEquals(0.12, TrackKind.WOODEN.maxSpeed, 1e-9);         // SpeedControllerSlow
        assertEquals(0.499, TrackKind.REINFORCED.maxSpeed, 1e-9);    // SpeedControllerReinforced
        assertEquals(0.4, TrackKind.REINFORCED.cornerSpeed, 1e-9);
        assertEquals(0.8, TrackKind.HIGH_SPEED.maxSpeed, 1e-9);      // railcraft.cfg ИИС 1: max.speed=0.8
        assertEquals(0.45, TrackKind.HIGH_SPEED.cornerSpeed, 1e-9);  // SPEED_SLOPE
        assertTrue(TrackKind.HIGH_SPEED.highSpeedCapable());
        assertFalse(TrackKind.STANDARD.highSpeedCapable());
    }
}
