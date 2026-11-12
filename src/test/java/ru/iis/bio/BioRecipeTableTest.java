package ru.iis.bio;

import org.junit.jupiter.api.Test;
import ru.iis.bio.production.BioRecipe;
import ru.iis.bio.production.Station;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Таблица рецептов Б-1 (iis/bio_recipes.json): целостность и достижимость без замкнутых зависимостей (спецификация v0.7 §22.12).
 * Старт — ванильный мир без машин Кибера + ядро с карманом + соскоб ткани колонии (решение Р8: на челноке нет посева и запасов).
 */
class BioRecipeTableTest {
    private static final Set<String> FLUIDS = Set.of("water", "n0", "primary_fraction", "runoff", "hydrolysate", "suspension", "binder", "g0",
            "extract", "emulsion", "concentrate", "w0", "n1", "e1", "g1",
            // черновик Б-2 (D148)
            "digestive_acid", "acid_runoff", "enzyme_solution", "g2");

    @Test
    void tableIsWellFormed() {
        List<BioRecipe> all = BioRecipe.all();
        assertFalse(all.isEmpty(), "bio_recipes.json is missing or empty");
        Set<String> ids = new HashSet<>();
        for (BioRecipe r : all) {
            assertTrue(ids.add(r.id()), "duplicate id " + r.id());
            Station st = Station.byId(r.station());
            assertTrue(r.seconds() > 0, r.id());
            assertTrue(r.mode() >= 0 && r.mode() < st.modes, r.id() + " mode");
            assertTrue(r.items().size() <= st.inputs, r.id() + " has more item inputs than slots");
            int outCap = st.outputs;
            assertTrue(r.outItems().size() <= Math.max(outCap, 1) + 3, r.id() + " outputs");
            for (BioRecipe.FluidIn f : r.fluids()) assertTrue(FLUIDS.contains(f.fluid()), r.id() + " fluid " + f.fluid());
            for (BioRecipe.FluidOut f : r.outFluids()) assertTrue(FLUIDS.contains(f.fluid()) && !f.fluid().equals("water"), r.id() + " out " + f.fluid());
            if (r.upkeep() > 0) assertTrue(st.colony, r.id() + ": upkeep only for living places");
            if (!r.tool().isEmpty()) assertTrue(st.toolSlot, r.id() + ": tool on a place without a tool slot");
            for (BioRecipe.In in : r.items()) assertTrue(in.count() > 0, r.id());
        }
    }

    /** Нет рецепта, который выдаёт больше той же жидкости, чем съедает (бесплатного размножения среды). */
    @Test
    void noFluidMultiplication() {
        for (BioRecipe r : BioRecipe.all()) {
            for (BioRecipe.FluidOut o : r.outFluids()) {
                for (BioRecipe.FluidIn i : r.fluids()) {
                    if (i.fluid().equals(o.fluid())) assertTrue(o.amount() <= i.amount(), r.id() + " multiplies " + o.fluid());
                }
            }
        }
    }

    @Test
    void wholeSliceIsReachableWithoutCycles() {
        Set<String> have = new HashSet<>();
        // ванильное и бесплатное по правилам среза
        have.addAll(Set.of("water", "food", "#minecraft:planks", "#iis:bio/raw_meat", "#iis:bio/vegetables", "minecraft:wheat", "minecraft:bone",
                "minecraft:rotten_flesh", "minecraft:stick", "iis:bio_knife",
                // туши — убийство ножом (Butchery), неразвитая культура — соскоб ткани колонии челнока
                "iis:carcass_small", "iis:carcass_medium", "iis:carcass_large", "iis:carcass_rotten", "iis:primitive_culture",
                // места, которые крафтятся на верстаке из ванили; карман есть у ядра
                "station:butcher", "station:mortar", "station:pot", "station:mixer", "station:pocket",
                // Б-2 (D148): отходная яма — верстак; продукты мутантных линий собираются с живых животных (Mutants), туша — ножом
                "station:sump", "minecraft:egg", "#minecraft:wool", "iis:carcass_fleshy", "iis:binding_secretion", "iis:long_fiber", "iis:dense_shell"));
        // крафт и места из продуктов среза
        List<Map.Entry<String, String>> unlocks = List.of(Map.entry("iis:plant_fiber", "iis:woven_mandrel"), Map.entry("iis:hand_substrate", "station:substrate"),
                Map.entry("iis:growth_bed", "station:bed"), Map.entry("iis:plant_fiber", "station:press"), Map.entry("iis:plant_fiber", "iis:filter_pad"),
                Map.entry("iis:filter_pad", "station:filter"), Map.entry("iis:forming_nest", "station:nest"),
                Map.entry("iis:nutrient_gland", "station:gland"), Map.entry("iis:membrane_filter", "station:membrane_filter"),
                Map.entry("iis:loosener", "station:loosener"), Map.entry("iis:compactor", "station:compactor"), Map.entry("iis:ore_stomach", "station:ore_stomach"),
                Map.entry("iis:crossbreeder", "station:crossbreeder"), Map.entry("iis:incubator", "station:incubator"), Map.entry("iis:isolator", "station:isolator"),
                Map.entry("iis:accelerator", "station:accelerator"), Map.entry("iis:bioreactor", "station:bioreactor"), Map.entry("iis:separator", "station:separator"));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BioRecipe r : BioRecipe.all()) {
                if (!have.contains("station:" + r.station())) continue;
                if (!r.tool().isEmpty() && !have.contains(r.tool())) continue;
                boolean ok = true;
                for (BioRecipe.In in : r.items()) {
                    String k = in.key();
                    if (k.equals("#iis:bio/seeds")) ok &= have.contains("iis:primitive_culture") || have.contains("iis:base_seed");
                    else if (k.startsWith("#iis:ores/")) ok &= true;   // руда — из мира
                    else if (k.startsWith("iis:crushed_")) ok &= have.contains("station:loosener");   // дроблёная руда — из разрыхлителя
                    else ok &= have.contains(k);
                }
                for (BioRecipe.FluidIn f : r.fluids()) ok &= have.contains(f.fluid());
                if (!ok) continue;
                for (BioRecipe.Out o : r.outItems()) changed |= have.add(o.item());
                for (BioRecipe.FluidOut o : r.outFluids()) changed |= have.add(o.fluid());
            }
            for (var e : unlocks) if (have.contains(e.getKey())) changed |= have.add(e.getValue());
        }
        for (String must : List.of("n0", "primary_fraction", "hydrolysate", "binder", "suspension", "g0", "iis:soft_tissue", "iis:living_membrane",
                "iis:base_seed", "iis:growth_bed", "iis:feeding_maw", "station:bed",
                "extract", "w0", "emulsion", "concentrate", "n1", "e1", "g1", "iis:tissue_microchannel", "iis:capillary_bundle",
                "iis:narrow_fibrous_vessel", "iis:working_fibrous_vessel", "iis:muscle_node", "iis:forming_nest", "iis:muscle_pump",
                "iis:nutrient_gland", "iis:membrane_filter", "iis:living_tank", "iis:check_valve", "iis:manual_clamp", "iis:valve_petal",
                // Б-2 (D148): чистое Био доходит до HV и до пыли металла без машин Кибера
                "iis:reinforced_shell", "iis:ore_stomach", "iis:incubator", "iis:regulated_shell", "iis:accelerator", "iis:bioreactor", "iis:separator",
                "iis:living_store", "iis:peristaltic_channel", "iis:dust_copper", "iis:mineral_binder", "g2", "enzyme_solution")) {
            assertTrue(have.contains(must), "unreachable: " + must);
        }
        for (BioRecipe r : BioRecipe.all()) {
            for (BioRecipe.Out o : r.outItems()) assertTrue(have.contains(o.item()), "recipe " + r.id() + " can never run (" + o.item() + ")");
        }
    }
}
