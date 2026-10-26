package ru.iis.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Доски по GT5 ProcessingLog:65, :84 с WoodNeedsSawForCrafting=true сборки ИИС 1.19.1 (D87): руками 2, пилой 4. */
class WoodPlanksTest {
    private static final Path GEN = Path.of("src/generated/resources");

    private static JsonObject json(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(GEN.resolve(rel))).getAsJsonObject();
    }

    private static int count(JsonObject r) {
        JsonObject res = r.getAsJsonObject("result");
        return res.has("count") ? res.get("count").getAsInt() : 1;
    }

    @Test
    void logByHandGivesHalfWithSawFull() throws IOException {
        for (String w : new String[]{"oak", "pale_oak", "crimson"}) {
            JsonObject hand = json("packs/iis_overrides/data/minecraft/recipe/" + w + "_planks.json");
            assertEquals(2, count(hand), w);
            JsonObject saw = json("data/iis/recipe/wood/" + w + "_planks_saw.json");
            assertEquals(4, count(saw), w);
            assertEquals("#iis:tools/saw", saw.getAsJsonObject("key").get("s").getAsString(), w);
        }
        assertEquals("#minecraft:crimson_stems", json("packs/iis_overrides/data/minecraft/recipe/crimson_planks.json")
                .getAsJsonArray("ingredients").get(0).getAsString());
        // каучуковое бревно: IC2 даёт 3 доски — руками 3 / 2 = 1, пилой 3
        assertEquals(1, count(json("data/iis/recipe/lv/rubber_log_planks.json")));
        assertEquals(3, count(json("data/iis/recipe/lv/rubber_log_planks_saw.json")));
    }
}
