package ru.iis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Независимость путей (D36, D41, CONCEPT §2.2): Кибер и Био не зависят друг от друга ни в коде, ни в рецептах.
 * Общие пакеты — ядро, жидкости, мир, инструменты; знать оба пути могут только команды, стенд, игровые тесты,
 * совместимость и будущий {@code ru.iis.duo}.
 */
class PathIndependenceTest {
    private static final Path SRC = Path.of("src/main/java/ru/iis");
    private static final Pattern CYBER = Pattern.compile("ru\\.iis\\.(machine|energy|lv|multi|cover|pollution)\\b");
    private static final Pattern BIO = Pattern.compile("ru\\.iis\\.bio\\b");
    private static final Pattern DUO = Pattern.compile("ru\\.iis\\.duo\\b");
    private static final Pattern IIS_ID = Pattern.compile("\"(iis:[a-z0-9_/]+)\"");
    /** Пакеты Кибера и общие пакеты, которые не должны знать о Био и Дуо. */
    private static final List<String> NOT_BIO = List.of("machine", "energy", "lv", "multi", "cover", "pollution", "core", "fluid", "world", "tool");
    /** Папки рецептов Кибера, чьи результаты — машины, корпуса, детали и провода: Био не может их требовать. */
    private static final List<String> CYBER_CRAFTS = List.of("lv", "machine", "multi", "cable", "wire");

    private static List<String> violations(Path dir, Pattern forbidden) throws IOException {
        List<String> bad = new ArrayList<>();
        if (!Files.isDirectory(dir)) return bad;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = forbidden.matcher(Files.readString(f));
                if (m.find()) bad.add(SRC.relativize(f) + " -> " + m.group());
            }
        }
        return bad;
    }

    @Test
    void bioCodeDoesNotUseCyber() throws IOException {
        assertEquals(List.of(), violations(SRC.resolve("bio"), CYBER));
    }

    @Test
    void cyberAndSharedCodeDoNotUseBioOrDuo() throws IOException {
        List<String> bad = new ArrayList<>();
        for (String p : NOT_BIO) {
            bad.addAll(violations(SRC.resolve(p), BIO));
            bad.addAll(violations(SRC.resolve(p), DUO));
        }
        bad.addAll(violations(SRC.resolve("bio"), DUO));
        assertEquals(List.of(), bad);
    }

    private static Set<String> ids(Path file) throws IOException {
        Set<String> out = new TreeSet<>();
        Matcher m = IIS_ID.matcher(Files.readString(file));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static List<Path> recipeFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".json")).toList();
        }
    }

    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    /** Всё, что производят рецепты Био: таблица мест (iis/bio_recipes.json) и крафты из папки recipe/bio. */
    private static Set<String> bioProducts() throws IOException {
        Set<String> out = new TreeSet<>();
        JsonArray table = JsonParser.parseString(Files.readString(Path.of("src/main/resources/iis/bio_recipes.json"))).getAsJsonArray();
        for (JsonElement e : table) {
            JsonObject r = e.getAsJsonObject();
            for (String key : List.of("out_items", "out_fluids")) {
                if (!r.has(key)) continue;
                for (JsonElement o : r.getAsJsonArray(key)) {
                    JsonObject oo = o.getAsJsonObject();
                    String id = oo.has("item") ? oo.get("item").getAsString() : oo.has("fluid") ? oo.get("fluid").getAsString() : null;
                    if (id != null && !id.contains(":")) id = "iis:" + id;   // жидкости Био записаны без пространства имён
                    // D148: пыли и дроблёная руда — общие материалы мира (Био тоже перерабатывает руду), это не «продукт Био»
                    if (id != null && (id.startsWith("iis:dust_") || id.startsWith("iis:tiny_dust_") || id.startsWith("iis:crushed_"))) continue;
                    if (id != null && id.startsWith("iis:")) out.add(id);
                }
            }
        }
        for (Path f : recipeFiles(RECIPES.resolve("bio"))) {
            JsonObject r = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (r.has("result") && r.getAsJsonObject("result").has("id")) out.add(r.getAsJsonObject("result").get("id").getAsString());
        }
        return out;
    }

    @Test
    void cyberRecipesDoNotNeedBioProducts() throws IOException {
        Set<String> bio = bioProducts();
        assertFalse(bio.isEmpty(), "bio products must be found");
        List<String> bad = new ArrayList<>();
        for (Path f : recipeFiles(RECIPES)) {
            if (RECIPES.relativize(f).startsWith("bio")) continue;
            for (String id : ids(f)) if (bio.contains(id)) bad.add(RECIPES.relativize(f) + " uses " + id);
        }
        assertEquals(List.of(), bad);
    }

    @Test
    void bioRecipesDoNotNeedCyberMachinesOrParts() throws IOException {
        Set<String> cyber = new TreeSet<>();
        for (String dir : CYBER_CRAFTS) {
            for (Path f : recipeFiles(RECIPES.resolve(dir))) {
                JsonObject r = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if (r.has("result") && r.getAsJsonObject("result").has("id")) cyber.add(r.getAsJsonObject("result").get("id").getAsString());
            }
        }
        assertFalse(cyber.isEmpty(), "cyber machines and parts must be found");
        List<String> bad = new ArrayList<>();
        List<Path> bioFiles = new ArrayList<>(recipeFiles(RECIPES.resolve("bio")));
        bioFiles.add(Path.of("src/main/resources/iis/bio_recipes.json"));
        for (Path f : bioFiles) {
            for (String id : ids(f)) if (cyber.contains(id)) bad.add(f.getFileName() + " uses " + id);
        }
        assertEquals(List.of(), bad);
    }
}
