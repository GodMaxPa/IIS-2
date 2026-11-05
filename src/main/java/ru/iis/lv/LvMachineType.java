package ru.iis.lv;

import ru.iis.energy.VoltageTier;
import ru.iis.machine.recipe.GtRecipeMap;

/**
 * Одноблочные машины LV (GT_Loader_MetaTileEntities, GT_MetaTileEntity_BasicMachine_GT_Recipe): карта рецептов,
 * число входов/выходов, бак, GUI. recipeMap == null — электропечь на ванильной плавке (4 EU/t, 128 тиков).
 */
public enum LvMachineType {
    MACERATOR("macerator", GtRecipeMap.MACERATOR, 1, 1, 0, "macerator1"),
    ELECTRIC_FURNACE("electric_furnace", null, 1, 1, 0, "e_furnace"),
    ALLOY_SMELTER("alloy_smelter", GtRecipeMap.ALLOY_SMELTER, 6, 1, 0, "alloysmelter"),
    COMPRESSOR("compressor", GtRecipeMap.COMPRESSOR, 1, 1, 0, "compressor"),
    EXTRACTOR("extractor", GtRecipeMap.EXTRACTOR, 1, 1, 0, "extractor"),
    FORGE_HAMMER("forge_hammer", GtRecipeMap.FORGE_HAMMER, 1, 1, 0, "hammer"),
    WIREMILL("wiremill", GtRecipeMap.WIREMILL, 1, 1, 0, "wiremill"),
    BENDER("bender", GtRecipeMap.BENDER, 2, 1, 0, "bender"),
    LATHE("lathe", GtRecipeMap.LATHE, 1, 2, 0, "lathe"),
    ASSEMBLER("assembler", GtRecipeMap.ASSEMBLER, 6, 1, 16000, "assembler"),
    MIXER("mixer", GtRecipeMap.MIXER, 6, 1, 8000, "mixer"),
    POLARIZER("polarizer", GtRecipeMap.POLARIZER, 1, 1, 0, "polarizer"),
    ORE_WASHER("ore_washer", GtRecipeMap.ORE_WASHER, 2, 3, 16000, "orewasher"),
    // GT_Loader_MetaTileEntities: Machine_LV_Centrifuge (2, 6, 64000), Sifter (1, 9), ElectromagneticSeparator (1, 3),
    // Autoclave (1, 1, 8000), Cutter (1, 2, 8000)
    CENTRIFUGE("centrifuge", GtRecipeMap.CENTRIFUGE, 2, 6, 64000, "centrifuge"),
    SIFTER("sifter", GtRecipeMap.SIFTER, 1, 9, 0, "sifter"),
    ELECTROMAGNETIC_SEPARATOR("electromagnetic_separator", GtRecipeMap.ELECTROMAGNETIC_SEPARATOR, 1, 3, 0, "electromagneticseparator"),
    AUTOCLAVE("autoclave", GtRecipeMap.AUTOCLAVE, 1, 1, 8000, "autoclave"),
    CUTTER("cutter", GtRecipeMap.CUTTER, 1, 2, 8000, "cutter"),
    CANNER("canner", GtRecipeMap.CANNER, 2, 2, 0, "canner"),
    // LV III (D76): GT_Loader_MetaTileEntities:775 Electrolyzer (2, 6, 64000), :830 ChemicalReactor (3, 3, 16000),
    // :958 FluidExtractor (1, 1, 16000), :967 FluidSolidifier (1, 1, 16000)
    ELECTROLYZER("electrolyzer", GtRecipeMap.ELECTROLYZER, 2, 6, 64000, "electrolyzer"),
    CHEMICAL_REACTOR("chemical_reactor", GtRecipeMap.CHEMICAL_REACTOR, 3, 3, 16000, "chemicalreactor"),
    FLUID_EXTRACTOR("fluid_extractor", GtRecipeMap.FLUID_EXTRACTOR, 1, 1, 16000, "fluidextractor"),
    FLUID_SOLIDIFIER("fluid_solidifier", GtRecipeMap.FLUID_SOLIDIFIER, 1, 1, 16000, "fluidsolidifier"),
    // LV IV: GT_Loader_MetaTileEntities:976 Distillery (1, 1, 1000), :985 ChemicalBath (1, 3, 8000)
    DISTILLERY("distillery", GtRecipeMap.DISTILLERY, 1, 1, 1000, "distillery"),
    CHEMICAL_BATH("chemical_bath", GtRecipeMap.CHEMICAL_BATH, 1, 3, 8000, "chemicalbath"),
    // MV (D81): GT_Loader_MetaTileEntities:784-785 ThermalCentrifuge LV/MV (1, 3); рецепты 48 EU/t — только с MV
    THERMAL_CENTRIFUGE("thermal_centrifuge", GtRecipeMap.THERMAL_CENTRIFUGE, 1, 3, 0, "thermalcentrifuge"),
    // D83: машины, которых у ИИС 2 не было в LV; GT_Loader_MetaTileEntities:685-686 Extruder (2, 1)
    EXTRUDER("extruder", GtRecipeMap.EXTRUDER, 2, 1, 0, "extruder"),
    // :1039 Press (2, 1), :1057 FluidHeater (1, 0, 8000), :1102 E_Oven (печь на ванильной плавке, как электропечь)
    FORMING_PRESS("forming_press", GtRecipeMap.FORMING_PRESS, 2, 1, 0, "press"),
    FLUID_HEATER("fluid_heater", GtRecipeMap.FLUID_HEATER, 1, 0, 8000, "fluidheater"),
    ELECTRIC_OVEN("electric_oven", null, 1, 1, 0, "e_oven"),
    // :1084-1085 ArcFurnace (1, 6, 16000)
    ARC_FURNACE("arc_furnace", GtRecipeMap.ARC_FURNACE, 1, 6, 16000, "arcfurnace"),
    // :1030 LaserEngraver (2, 1)
    LASER_ENGRAVER("laser_engraver", GtRecipeMap.LASER_ENGRAVER, 2, 1, 0, "laserengraver"),
    // D92: :931 PotionBrewer (1, 0, бак 750), :1374 CircuitAssembler (6, 1, 16000)
    BREWERY("brewery", GtRecipeMap.BREWERY, 1, 0, 750, "potionbrewer"),
    CIRCUIT_ASSEMBLER("circuit_assembler", GtRecipeMap.CIRCUIT_ASSEMBLER, 6, 1, 16000, "circuitassembler"),
    // D101: :839 FluidCanner (1, 1, бак 16000; MV 32000, HV 48000, EV 64000)
    FLUID_CANNER("fluid_canner", GtRecipeMap.FLUID_CANNER, 1, 1, 16000, "fluidcanner"),
    THERMAL_CENTRIFUGE_MV("thermal_centrifuge", GtRecipeMap.THERMAL_CENTRIFUGE, 1, 3, 0, "thermalcentrifuge", VoltageTier.MV),
    // MV (D82): GT_Loader_MetaTileEntities Machine_MV_* — те же входы/выходы/баки, что у LV, кроме смесителя (бак 16000)
    MACERATOR_MV("macerator", GtRecipeMap.MACERATOR, 1, 1, 0, "macerator1", VoltageTier.MV),
    ELECTRIC_FURNACE_MV("electric_furnace", null, 1, 1, 0, "e_furnace", VoltageTier.MV),
    ALLOY_SMELTER_MV("alloy_smelter", GtRecipeMap.ALLOY_SMELTER, 6, 1, 0, "alloysmelter", VoltageTier.MV),
    COMPRESSOR_MV("compressor", GtRecipeMap.COMPRESSOR, 1, 1, 0, "compressor", VoltageTier.MV),
    EXTRACTOR_MV("extractor", GtRecipeMap.EXTRACTOR, 1, 1, 0, "extractor", VoltageTier.MV),
    FORGE_HAMMER_MV("forge_hammer", GtRecipeMap.FORGE_HAMMER, 1, 1, 0, "hammer", VoltageTier.MV),
    WIREMILL_MV("wiremill", GtRecipeMap.WIREMILL, 1, 1, 0, "wiremill", VoltageTier.MV),
    BENDER_MV("bender", GtRecipeMap.BENDER, 2, 1, 0, "bender", VoltageTier.MV),
    LATHE_MV("lathe", GtRecipeMap.LATHE, 1, 2, 0, "lathe", VoltageTier.MV),
    ASSEMBLER_MV("assembler", GtRecipeMap.ASSEMBLER, 6, 1, 16000, "assembler", VoltageTier.MV),
    MIXER_MV("mixer", GtRecipeMap.MIXER, 6, 1, 16000, "mixer", VoltageTier.MV),
    POLARIZER_MV("polarizer", GtRecipeMap.POLARIZER, 1, 1, 0, "polarizer", VoltageTier.MV),
    ORE_WASHER_MV("ore_washer", GtRecipeMap.ORE_WASHER, 2, 3, 16000, "orewasher", VoltageTier.MV),
    CENTRIFUGE_MV("centrifuge", GtRecipeMap.CENTRIFUGE, 2, 6, 64000, "centrifuge", VoltageTier.MV),
    SIFTER_MV("sifter", GtRecipeMap.SIFTER, 1, 9, 0, "sifter", VoltageTier.MV),
    ELECTROMAGNETIC_SEPARATOR_MV("electromagnetic_separator", GtRecipeMap.ELECTROMAGNETIC_SEPARATOR, 1, 3, 0, "electromagneticseparator", VoltageTier.MV),
    AUTOCLAVE_MV("autoclave", GtRecipeMap.AUTOCLAVE, 1, 1, 8000, "autoclave", VoltageTier.MV),
    CUTTER_MV("cutter", GtRecipeMap.CUTTER, 1, 2, 8000, "cutter", VoltageTier.MV),
    CANNER_MV("canner", GtRecipeMap.CANNER, 2, 2, 0, "canner", VoltageTier.MV),
    ELECTROLYZER_MV("electrolyzer", GtRecipeMap.ELECTROLYZER, 2, 6, 64000, "electrolyzer", VoltageTier.MV),
    CHEMICAL_REACTOR_MV("chemical_reactor", GtRecipeMap.CHEMICAL_REACTOR, 3, 3, 16000, "chemicalreactor", VoltageTier.MV),
    FLUID_EXTRACTOR_MV("fluid_extractor", GtRecipeMap.FLUID_EXTRACTOR, 1, 1, 16000, "fluidextractor", VoltageTier.MV),
    FLUID_SOLIDIFIER_MV("fluid_solidifier", GtRecipeMap.FLUID_SOLIDIFIER, 1, 1, 16000, "fluidsolidifier", VoltageTier.MV),
    DISTILLERY_MV("distillery", GtRecipeMap.DISTILLERY, 1, 1, 1000, "distillery", VoltageTier.MV),
    CHEMICAL_BATH_MV("chemical_bath", GtRecipeMap.CHEMICAL_BATH, 1, 3, 8000, "chemicalbath", VoltageTier.MV),
    EXTRUDER_MV("extruder", GtRecipeMap.EXTRUDER, 2, 1, 0, "extruder", VoltageTier.MV),
    FORMING_PRESS_MV("forming_press", GtRecipeMap.FORMING_PRESS, 2, 1, 0, "press", VoltageTier.MV),
    FLUID_HEATER_MV("fluid_heater", GtRecipeMap.FLUID_HEATER, 1, 0, 8000, "fluidheater", VoltageTier.MV),
    ELECTRIC_OVEN_MV("electric_oven", null, 1, 1, 0, "e_oven", VoltageTier.MV),
    ARC_FURNACE_MV("arc_furnace", GtRecipeMap.ARC_FURNACE, 1, 6, 16000, "arcfurnace", VoltageTier.MV),
    LASER_ENGRAVER_MV("laser_engraver", GtRecipeMap.LASER_ENGRAVER, 2, 1, 0, "laserengraver", VoltageTier.MV),
    BREWERY_MV("brewery", GtRecipeMap.BREWERY, 1, 0, 750, "potionbrewer", VoltageTier.MV),
    CIRCUIT_ASSEMBLER_MV("circuit_assembler", GtRecipeMap.CIRCUIT_ASSEMBLER, 6, 1, 16000, "circuitassembler", VoltageTier.MV),
    FLUID_CANNER_MV("fluid_canner", GtRecipeMap.FLUID_CANNER, 1, 1, 32000, "fluidcanner", VoltageTier.MV),
    // HV (D93): Machine_HV_* — как MV, кроме измельчителя (2 выхода — побочные продукты) и смесителя (бак 24000)
    THERMAL_CENTRIFUGE_HV("thermal_centrifuge", GtRecipeMap.THERMAL_CENTRIFUGE, 1, 3, 0, "thermalcentrifuge", VoltageTier.HV),
    MACERATOR_HV("macerator", GtRecipeMap.MACERATOR, 1, 2, 0, "macerator1", VoltageTier.HV),
    ELECTRIC_FURNACE_HV("electric_furnace", null, 1, 1, 0, "e_furnace", VoltageTier.HV),
    ALLOY_SMELTER_HV("alloy_smelter", GtRecipeMap.ALLOY_SMELTER, 6, 1, 0, "alloysmelter", VoltageTier.HV),
    COMPRESSOR_HV("compressor", GtRecipeMap.COMPRESSOR, 1, 1, 0, "compressor", VoltageTier.HV),
    EXTRACTOR_HV("extractor", GtRecipeMap.EXTRACTOR, 1, 1, 0, "extractor", VoltageTier.HV),
    FORGE_HAMMER_HV("forge_hammer", GtRecipeMap.FORGE_HAMMER, 1, 1, 0, "hammer", VoltageTier.HV),
    WIREMILL_HV("wiremill", GtRecipeMap.WIREMILL, 1, 1, 0, "wiremill", VoltageTier.HV),
    BENDER_HV("bender", GtRecipeMap.BENDER, 2, 1, 0, "bender", VoltageTier.HV),
    LATHE_HV("lathe", GtRecipeMap.LATHE, 1, 2, 0, "lathe", VoltageTier.HV),
    ASSEMBLER_HV("assembler", GtRecipeMap.ASSEMBLER, 6, 1, 16000, "assembler", VoltageTier.HV),
    MIXER_HV("mixer", GtRecipeMap.MIXER, 6, 1, 24000, "mixer", VoltageTier.HV),
    POLARIZER_HV("polarizer", GtRecipeMap.POLARIZER, 1, 1, 0, "polarizer", VoltageTier.HV),
    ORE_WASHER_HV("ore_washer", GtRecipeMap.ORE_WASHER, 2, 3, 16000, "orewasher", VoltageTier.HV),
    CENTRIFUGE_HV("centrifuge", GtRecipeMap.CENTRIFUGE, 2, 6, 64000, "centrifuge", VoltageTier.HV),
    SIFTER_HV("sifter", GtRecipeMap.SIFTER, 1, 9, 0, "sifter", VoltageTier.HV),
    ELECTROMAGNETIC_SEPARATOR_HV("electromagnetic_separator", GtRecipeMap.ELECTROMAGNETIC_SEPARATOR, 1, 3, 0, "electromagneticseparator", VoltageTier.HV),
    AUTOCLAVE_HV("autoclave", GtRecipeMap.AUTOCLAVE, 1, 1, 8000, "autoclave", VoltageTier.HV),
    CUTTER_HV("cutter", GtRecipeMap.CUTTER, 1, 2, 8000, "cutter", VoltageTier.HV),
    CANNER_HV("canner", GtRecipeMap.CANNER, 2, 2, 0, "canner", VoltageTier.HV),
    ELECTROLYZER_HV("electrolyzer", GtRecipeMap.ELECTROLYZER, 2, 6, 64000, "electrolyzer", VoltageTier.HV),
    CHEMICAL_REACTOR_HV("chemical_reactor", GtRecipeMap.CHEMICAL_REACTOR, 3, 3, 16000, "chemicalreactor", VoltageTier.HV),
    FLUID_EXTRACTOR_HV("fluid_extractor", GtRecipeMap.FLUID_EXTRACTOR, 1, 1, 16000, "fluidextractor", VoltageTier.HV),
    FLUID_SOLIDIFIER_HV("fluid_solidifier", GtRecipeMap.FLUID_SOLIDIFIER, 1, 1, 16000, "fluidsolidifier", VoltageTier.HV),
    DISTILLERY_HV("distillery", GtRecipeMap.DISTILLERY, 1, 1, 1000, "distillery", VoltageTier.HV),
    CHEMICAL_BATH_HV("chemical_bath", GtRecipeMap.CHEMICAL_BATH, 1, 3, 8000, "chemicalbath", VoltageTier.HV),
    EXTRUDER_HV("extruder", GtRecipeMap.EXTRUDER, 2, 1, 0, "extruder", VoltageTier.HV),
    FORMING_PRESS_HV("forming_press", GtRecipeMap.FORMING_PRESS, 2, 1, 0, "press", VoltageTier.HV),
    FLUID_HEATER_HV("fluid_heater", GtRecipeMap.FLUID_HEATER, 1, 0, 8000, "fluidheater", VoltageTier.HV),
    ELECTRIC_OVEN_HV("electric_oven", null, 1, 1, 0, "e_oven", VoltageTier.HV),
    ARC_FURNACE_HV("arc_furnace", GtRecipeMap.ARC_FURNACE, 1, 6, 16000, "arcfurnace", VoltageTier.HV),
    LASER_ENGRAVER_HV("laser_engraver", GtRecipeMap.LASER_ENGRAVER, 2, 1, 0, "laserengraver", VoltageTier.HV),
    BREWERY_HV("brewery", GtRecipeMap.BREWERY, 1, 0, 750, "potionbrewer", VoltageTier.HV),
    CIRCUIT_ASSEMBLER_HV("circuit_assembler", GtRecipeMap.CIRCUIT_ASSEMBLER, 6, 1, 16000, "circuitassembler", VoltageTier.HV),
    FLUID_CANNER_HV("fluid_canner", GtRecipeMap.FLUID_CANNER, 1, 1, 48000, "fluidcanner", VoltageTier.HV),
    // EV (D98): пока только сборщик (большой ротор, 1600 EU/t) и химреактор (переработка вольфрама, 1920 EU/t)
    ASSEMBLER_EV("assembler", GtRecipeMap.ASSEMBLER, 6, 1, 16000, "assembler", VoltageTier.EV),
    CHEMICAL_REACTOR_EV("chemical_reactor", GtRecipeMap.CHEMICAL_REACTOR, 3, 3, 16000, "chemicalreactor", VoltageTier.EV),
    // EV (D100): остальные Machine_EV_* — как HV, кроме измельчителя (3 выхода) и смесителя (бак 32000)
    THERMAL_CENTRIFUGE_EV("thermal_centrifuge", GtRecipeMap.THERMAL_CENTRIFUGE, 1, 3, 0, "thermalcentrifuge", VoltageTier.EV),
    MACERATOR_EV("macerator", GtRecipeMap.MACERATOR, 1, 3, 0, "macerator1", VoltageTier.EV),
    ELECTRIC_FURNACE_EV("electric_furnace", null, 1, 1, 0, "e_furnace", VoltageTier.EV),
    ALLOY_SMELTER_EV("alloy_smelter", GtRecipeMap.ALLOY_SMELTER, 6, 1, 0, "alloysmelter", VoltageTier.EV),
    COMPRESSOR_EV("compressor", GtRecipeMap.COMPRESSOR, 1, 1, 0, "compressor", VoltageTier.EV),
    EXTRACTOR_EV("extractor", GtRecipeMap.EXTRACTOR, 1, 1, 0, "extractor", VoltageTier.EV),
    FORGE_HAMMER_EV("forge_hammer", GtRecipeMap.FORGE_HAMMER, 1, 1, 0, "hammer", VoltageTier.EV),
    WIREMILL_EV("wiremill", GtRecipeMap.WIREMILL, 1, 1, 0, "wiremill", VoltageTier.EV),
    BENDER_EV("bender", GtRecipeMap.BENDER, 2, 1, 0, "bender", VoltageTier.EV),
    LATHE_EV("lathe", GtRecipeMap.LATHE, 1, 2, 0, "lathe", VoltageTier.EV),
    MIXER_EV("mixer", GtRecipeMap.MIXER, 6, 1, 32000, "mixer", VoltageTier.EV),
    POLARIZER_EV("polarizer", GtRecipeMap.POLARIZER, 1, 1, 0, "polarizer", VoltageTier.EV),
    ORE_WASHER_EV("ore_washer", GtRecipeMap.ORE_WASHER, 2, 3, 16000, "orewasher", VoltageTier.EV),
    CENTRIFUGE_EV("centrifuge", GtRecipeMap.CENTRIFUGE, 2, 6, 64000, "centrifuge", VoltageTier.EV),
    SIFTER_EV("sifter", GtRecipeMap.SIFTER, 1, 9, 0, "sifter", VoltageTier.EV),
    ELECTROMAGNETIC_SEPARATOR_EV("electromagnetic_separator", GtRecipeMap.ELECTROMAGNETIC_SEPARATOR, 1, 3, 0, "electromagneticseparator", VoltageTier.EV),
    AUTOCLAVE_EV("autoclave", GtRecipeMap.AUTOCLAVE, 1, 1, 8000, "autoclave", VoltageTier.EV),
    CUTTER_EV("cutter", GtRecipeMap.CUTTER, 1, 2, 8000, "cutter", VoltageTier.EV),
    CANNER_EV("canner", GtRecipeMap.CANNER, 2, 2, 0, "canner", VoltageTier.EV),
    ELECTROLYZER_EV("electrolyzer", GtRecipeMap.ELECTROLYZER, 2, 6, 64000, "electrolyzer", VoltageTier.EV),
    FLUID_EXTRACTOR_EV("fluid_extractor", GtRecipeMap.FLUID_EXTRACTOR, 1, 1, 16000, "fluidextractor", VoltageTier.EV),
    FLUID_SOLIDIFIER_EV("fluid_solidifier", GtRecipeMap.FLUID_SOLIDIFIER, 1, 1, 16000, "fluidsolidifier", VoltageTier.EV),
    DISTILLERY_EV("distillery", GtRecipeMap.DISTILLERY, 1, 1, 1000, "distillery", VoltageTier.EV),
    CHEMICAL_BATH_EV("chemical_bath", GtRecipeMap.CHEMICAL_BATH, 1, 3, 8000, "chemicalbath", VoltageTier.EV),
    EXTRUDER_EV("extruder", GtRecipeMap.EXTRUDER, 2, 1, 0, "extruder", VoltageTier.EV),
    FORMING_PRESS_EV("forming_press", GtRecipeMap.FORMING_PRESS, 2, 1, 0, "press", VoltageTier.EV),
    FLUID_HEATER_EV("fluid_heater", GtRecipeMap.FLUID_HEATER, 1, 0, 8000, "fluidheater", VoltageTier.EV),
    ELECTRIC_OVEN_EV("electric_oven", null, 1, 1, 0, "e_oven", VoltageTier.EV),
    ARC_FURNACE_EV("arc_furnace", GtRecipeMap.ARC_FURNACE, 1, 6, 16000, "arcfurnace", VoltageTier.EV),
    LASER_ENGRAVER_EV("laser_engraver", GtRecipeMap.LASER_ENGRAVER, 2, 1, 0, "laserengraver", VoltageTier.EV),
    BREWERY_EV("brewery", GtRecipeMap.BREWERY, 1, 0, 750, "potionbrewer", VoltageTier.EV),
    CIRCUIT_ASSEMBLER_EV("circuit_assembler", GtRecipeMap.CIRCUIT_ASSEMBLER, 6, 1, 16000, "circuitassembler", VoltageTier.EV),
    FLUID_CANNER_EV("fluid_canner", GtRecipeMap.FLUID_CANNER, 1, 1, 64000, "fluidcanner", VoltageTier.EV),
    // IV (D103): Machine_IV_* — как EV, кроме измельчителя (4 выхода), смесителя (40000) и наливщика (80000); сборщик схем IV ждёт схемы Master (LuV)
    CHEMICAL_REACTOR_IV("chemical_reactor", GtRecipeMap.CHEMICAL_REACTOR, 3, 3, 16000, "chemicalreactor", VoltageTier.IV),
    ASSEMBLER_IV("assembler", GtRecipeMap.ASSEMBLER, 6, 1, 16000, "assembler", VoltageTier.IV),
    THERMAL_CENTRIFUGE_IV("thermal_centrifuge", GtRecipeMap.THERMAL_CENTRIFUGE, 1, 3, 0, "thermalcentrifuge", VoltageTier.IV),
    MACERATOR_IV("macerator", GtRecipeMap.MACERATOR, 1, 4, 0, "macerator1", VoltageTier.IV),
    ELECTRIC_FURNACE_IV("electric_furnace", null, 1, 1, 0, "e_furnace", VoltageTier.IV),
    ALLOY_SMELTER_IV("alloy_smelter", GtRecipeMap.ALLOY_SMELTER, 6, 1, 0, "alloysmelter", VoltageTier.IV),
    COMPRESSOR_IV("compressor", GtRecipeMap.COMPRESSOR, 1, 1, 0, "compressor", VoltageTier.IV),
    EXTRACTOR_IV("extractor", GtRecipeMap.EXTRACTOR, 1, 1, 0, "extractor", VoltageTier.IV),
    FORGE_HAMMER_IV("forge_hammer", GtRecipeMap.FORGE_HAMMER, 1, 1, 0, "hammer", VoltageTier.IV),
    WIREMILL_IV("wiremill", GtRecipeMap.WIREMILL, 1, 1, 0, "wiremill", VoltageTier.IV),
    BENDER_IV("bender", GtRecipeMap.BENDER, 2, 1, 0, "bender", VoltageTier.IV),
    LATHE_IV("lathe", GtRecipeMap.LATHE, 1, 2, 0, "lathe", VoltageTier.IV),
    MIXER_IV("mixer", GtRecipeMap.MIXER, 6, 1, 40000, "mixer", VoltageTier.IV),
    POLARIZER_IV("polarizer", GtRecipeMap.POLARIZER, 1, 1, 0, "polarizer", VoltageTier.IV),
    ORE_WASHER_IV("ore_washer", GtRecipeMap.ORE_WASHER, 2, 3, 16000, "orewasher", VoltageTier.IV),
    CENTRIFUGE_IV("centrifuge", GtRecipeMap.CENTRIFUGE, 2, 6, 64000, "centrifuge", VoltageTier.IV),
    SIFTER_IV("sifter", GtRecipeMap.SIFTER, 1, 9, 0, "sifter", VoltageTier.IV),
    ELECTROMAGNETIC_SEPARATOR_IV("electromagnetic_separator", GtRecipeMap.ELECTROMAGNETIC_SEPARATOR, 1, 3, 0, "electromagneticseparator", VoltageTier.IV),
    AUTOCLAVE_IV("autoclave", GtRecipeMap.AUTOCLAVE, 1, 1, 8000, "autoclave", VoltageTier.IV),
    CUTTER_IV("cutter", GtRecipeMap.CUTTER, 1, 2, 8000, "cutter", VoltageTier.IV),
    CANNER_IV("canner", GtRecipeMap.CANNER, 2, 2, 0, "canner", VoltageTier.IV),
    ELECTROLYZER_IV("electrolyzer", GtRecipeMap.ELECTROLYZER, 2, 6, 64000, "electrolyzer", VoltageTier.IV),
    FLUID_EXTRACTOR_IV("fluid_extractor", GtRecipeMap.FLUID_EXTRACTOR, 1, 1, 16000, "fluidextractor", VoltageTier.IV),
    FLUID_SOLIDIFIER_IV("fluid_solidifier", GtRecipeMap.FLUID_SOLIDIFIER, 1, 1, 16000, "fluidsolidifier", VoltageTier.IV),
    DISTILLERY_IV("distillery", GtRecipeMap.DISTILLERY, 1, 1, 1000, "distillery", VoltageTier.IV),
    CHEMICAL_BATH_IV("chemical_bath", GtRecipeMap.CHEMICAL_BATH, 1, 3, 8000, "chemicalbath", VoltageTier.IV),
    EXTRUDER_IV("extruder", GtRecipeMap.EXTRUDER, 2, 1, 0, "extruder", VoltageTier.IV),
    FORMING_PRESS_IV("forming_press", GtRecipeMap.FORMING_PRESS, 2, 1, 0, "press", VoltageTier.IV),
    FLUID_HEATER_IV("fluid_heater", GtRecipeMap.FLUID_HEATER, 1, 0, 8000, "fluidheater", VoltageTier.IV),
    ELECTRIC_OVEN_IV("electric_oven", null, 1, 1, 0, "e_oven", VoltageTier.IV),
    ARC_FURNACE_IV("arc_furnace", GtRecipeMap.ARC_FURNACE, 1, 6, 16000, "arcfurnace", VoltageTier.IV),
    LASER_ENGRAVER_IV("laser_engraver", GtRecipeMap.LASER_ENGRAVER, 2, 1, 0, "laserengraver", VoltageTier.IV),
    BREWERY_IV("brewery", GtRecipeMap.BREWERY, 1, 0, 750, "potionbrewer", VoltageTier.IV),
    FLUID_CANNER_IV("fluid_canner", GtRecipeMap.FLUID_CANNER, 1, 1, 80000, "fluidcanner", VoltageTier.IV),
    // D103: разрушитель камня (GT_MetaTileEntity_RockBreaker: красная пыль и программная схема), упаковщик и распаковщик LV–IV
    ROCK_BREAKER("rock_breaker", GtRecipeMap.ROCK_BREAKER, 2, 1, 0, "rockbreaker"),
    PACKAGER("packager", GtRecipeMap.PACKAGER, 2, 1, 0, "packager"),
    UNPACKAGER("unpackager", GtRecipeMap.UNPACKAGER, 1, 2, 0, "unpackager"),
    ROCK_BREAKER_MV("rock_breaker", GtRecipeMap.ROCK_BREAKER, 2, 1, 0, "rockbreaker", VoltageTier.MV),
    PACKAGER_MV("packager", GtRecipeMap.PACKAGER, 2, 1, 0, "packager", VoltageTier.MV),
    UNPACKAGER_MV("unpackager", GtRecipeMap.UNPACKAGER, 1, 2, 0, "unpackager", VoltageTier.MV),
    ROCK_BREAKER_HV("rock_breaker", GtRecipeMap.ROCK_BREAKER, 2, 1, 0, "rockbreaker", VoltageTier.HV),
    PACKAGER_HV("packager", GtRecipeMap.PACKAGER, 2, 1, 0, "packager", VoltageTier.HV),
    UNPACKAGER_HV("unpackager", GtRecipeMap.UNPACKAGER, 1, 2, 0, "unpackager", VoltageTier.HV),
    ROCK_BREAKER_EV("rock_breaker", GtRecipeMap.ROCK_BREAKER, 2, 1, 0, "rockbreaker", VoltageTier.EV),
    PACKAGER_EV("packager", GtRecipeMap.PACKAGER, 2, 1, 0, "packager", VoltageTier.EV),
    UNPACKAGER_EV("unpackager", GtRecipeMap.UNPACKAGER, 1, 2, 0, "unpackager", VoltageTier.EV),
    ROCK_BREAKER_IV("rock_breaker", GtRecipeMap.ROCK_BREAKER, 2, 1, 0, "rockbreaker", VoltageTier.IV),
    PACKAGER_IV("packager", GtRecipeMap.PACKAGER, 2, 1, 0, "packager", VoltageTier.IV),
    UNPACKAGER_IV("unpackager", GtRecipeMap.UNPACKAGER, 1, 2, 0, "unpackager", VoltageTier.IV),
    // D107: GT_Loader_MetaTileEntities:1518-1522 Machine_*_Filter (схема во входе, 6 выходов; бак 16000, 24000, 32000, 40000, 48000)
    FILTER("filter", GtRecipeMap.FILTER, 1, 6, 16000, "filter"),
    FILTER_MV("filter", GtRecipeMap.FILTER, 1, 6, 24000, "filter", VoltageTier.MV),
    FILTER_HV("filter", GtRecipeMap.FILTER, 1, 6, 32000, "filter", VoltageTier.HV),
    FILTER_EV("filter", GtRecipeMap.FILTER, 1, 6, 40000, "filter", VoltageTier.EV),
    FILTER_IV("filter", GtRecipeMap.FILTER, 1, 6, 48000, "filter", VoltageTier.IV),
    // D109: :712-716 Machine_*_Microwave (1, 1; печь на ванильной плавке: 32 тика, 4 EU/t, металл взрывается)
    MICROWAVE("microwave", null, 1, 1, 0, "e_furnace"),
    MICROWAVE_MV("microwave", null, 1, 1, 0, "e_furnace", VoltageTier.MV),
    MICROWAVE_HV("microwave", null, 1, 1, 0, "e_furnace", VoltageTier.HV),
    MICROWAVE_EV("microwave", null, 1, 1, 0, "e_furnace", VoltageTier.EV),
    MICROWAVE_IV("microwave", null, 1, 1, 0, "e_furnace", VoltageTier.IV),
    // D110: Machine_LuV_* — как IV, кроме смесителя (48000), наливщика (96000) и фильтра (56000); сборщик схем IV и LuV (нужны Master и Ultimate)
    CHEMICAL_REACTOR_LUV("chemical_reactor", GtRecipeMap.CHEMICAL_REACTOR, 3, 3, 16000, "chemicalreactor", VoltageTier.LUV),
    ASSEMBLER_LUV("assembler", GtRecipeMap.ASSEMBLER, 6, 1, 16000, "assembler", VoltageTier.LUV),
    THERMAL_CENTRIFUGE_LUV("thermal_centrifuge", GtRecipeMap.THERMAL_CENTRIFUGE, 1, 3, 0, "thermalcentrifuge", VoltageTier.LUV),
    MACERATOR_LUV("macerator", GtRecipeMap.MACERATOR, 1, 4, 0, "macerator1", VoltageTier.LUV),
    ELECTRIC_FURNACE_LUV("electric_furnace", null, 1, 1, 0, "e_furnace", VoltageTier.LUV),
    ALLOY_SMELTER_LUV("alloy_smelter", GtRecipeMap.ALLOY_SMELTER, 6, 1, 0, "alloysmelter", VoltageTier.LUV),
    COMPRESSOR_LUV("compressor", GtRecipeMap.COMPRESSOR, 1, 1, 0, "compressor", VoltageTier.LUV),
    EXTRACTOR_LUV("extractor", GtRecipeMap.EXTRACTOR, 1, 1, 0, "extractor", VoltageTier.LUV),
    FORGE_HAMMER_LUV("forge_hammer", GtRecipeMap.FORGE_HAMMER, 1, 1, 0, "hammer", VoltageTier.LUV),
    WIREMILL_LUV("wiremill", GtRecipeMap.WIREMILL, 1, 1, 0, "wiremill", VoltageTier.LUV),
    BENDER_LUV("bender", GtRecipeMap.BENDER, 2, 1, 0, "bender", VoltageTier.LUV),
    LATHE_LUV("lathe", GtRecipeMap.LATHE, 1, 2, 0, "lathe", VoltageTier.LUV),
    MIXER_LUV("mixer", GtRecipeMap.MIXER, 6, 1, 48000, "mixer", VoltageTier.LUV),
    POLARIZER_LUV("polarizer", GtRecipeMap.POLARIZER, 1, 1, 0, "polarizer", VoltageTier.LUV),
    ORE_WASHER_LUV("ore_washer", GtRecipeMap.ORE_WASHER, 2, 3, 16000, "orewasher", VoltageTier.LUV),
    CENTRIFUGE_LUV("centrifuge", GtRecipeMap.CENTRIFUGE, 2, 6, 64000, "centrifuge", VoltageTier.LUV),
    SIFTER_LUV("sifter", GtRecipeMap.SIFTER, 1, 9, 0, "sifter", VoltageTier.LUV),
    ELECTROMAGNETIC_SEPARATOR_LUV("electromagnetic_separator", GtRecipeMap.ELECTROMAGNETIC_SEPARATOR, 1, 3, 0, "electromagneticseparator", VoltageTier.LUV),
    AUTOCLAVE_LUV("autoclave", GtRecipeMap.AUTOCLAVE, 1, 1, 8000, "autoclave", VoltageTier.LUV),
    CUTTER_LUV("cutter", GtRecipeMap.CUTTER, 1, 2, 8000, "cutter", VoltageTier.LUV),
    CANNER_LUV("canner", GtRecipeMap.CANNER, 2, 2, 0, "canner", VoltageTier.LUV),
    ELECTROLYZER_LUV("electrolyzer", GtRecipeMap.ELECTROLYZER, 2, 6, 64000, "electrolyzer", VoltageTier.LUV),
    FLUID_EXTRACTOR_LUV("fluid_extractor", GtRecipeMap.FLUID_EXTRACTOR, 1, 1, 16000, "fluidextractor", VoltageTier.LUV),
    FLUID_SOLIDIFIER_LUV("fluid_solidifier", GtRecipeMap.FLUID_SOLIDIFIER, 1, 1, 16000, "fluidsolidifier", VoltageTier.LUV),
    DISTILLERY_LUV("distillery", GtRecipeMap.DISTILLERY, 1, 1, 1000, "distillery", VoltageTier.LUV),
    CHEMICAL_BATH_LUV("chemical_bath", GtRecipeMap.CHEMICAL_BATH, 1, 3, 8000, "chemicalbath", VoltageTier.LUV),
    EXTRUDER_LUV("extruder", GtRecipeMap.EXTRUDER, 2, 1, 0, "extruder", VoltageTier.LUV),
    FORMING_PRESS_LUV("forming_press", GtRecipeMap.FORMING_PRESS, 2, 1, 0, "press", VoltageTier.LUV),
    FLUID_HEATER_LUV("fluid_heater", GtRecipeMap.FLUID_HEATER, 1, 0, 8000, "fluidheater", VoltageTier.LUV),
    ELECTRIC_OVEN_LUV("electric_oven", null, 1, 1, 0, "e_oven", VoltageTier.LUV),
    ARC_FURNACE_LUV("arc_furnace", GtRecipeMap.ARC_FURNACE, 1, 6, 16000, "arcfurnace", VoltageTier.LUV),
    LASER_ENGRAVER_LUV("laser_engraver", GtRecipeMap.LASER_ENGRAVER, 2, 1, 0, "laserengraver", VoltageTier.LUV),
    BREWERY_LUV("brewery", GtRecipeMap.BREWERY, 1, 0, 750, "potionbrewer", VoltageTier.LUV),
    FLUID_CANNER_LUV("fluid_canner", GtRecipeMap.FLUID_CANNER, 1, 1, 96000, "fluidcanner", VoltageTier.LUV),
    ROCK_BREAKER_LUV("rock_breaker", GtRecipeMap.ROCK_BREAKER, 2, 1, 0, "rockbreaker", VoltageTier.LUV),
    PACKAGER_LUV("packager", GtRecipeMap.PACKAGER, 2, 1, 0, "packager", VoltageTier.LUV),
    UNPACKAGER_LUV("unpackager", GtRecipeMap.UNPACKAGER, 1, 2, 0, "unpackager", VoltageTier.LUV),
    FILTER_LUV("filter", GtRecipeMap.FILTER, 1, 6, 56000, "filter", VoltageTier.LUV),
    MICROWAVE_LUV("microwave", null, 1, 1, 0, "e_furnace", VoltageTier.LUV),
    CIRCUIT_ASSEMBLER_IV("circuit_assembler", GtRecipeMap.CIRCUIT_ASSEMBLER, 6, 1, 16000, "circuitassembler", VoltageTier.IV),
    CIRCUIT_ASSEMBLER_LUV("circuit_assembler", GtRecipeMap.CIRCUIT_ASSEMBLER, 6, 1, 16000, "circuitassembler", VoltageTier.LUV),
    // D110: GT_MetaTileEntity_Scanner: 1 вход, 1 выход, флешка — в особом слоте; только исследования сборочной линии
    SCANNER("scanner", null, 1, 1, 0, "scanner"),
    SCANNER_MV("scanner", null, 1, 1, 0, "scanner", VoltageTier.MV),
    SCANNER_HV("scanner", null, 1, 1, 0, "scanner", VoltageTier.HV),
    SCANNER_EV("scanner", null, 1, 1, 0, "scanner", VoltageTier.EV),
    SCANNER_IV("scanner", null, 1, 1, 0, "scanner", VoltageTier.IV),
    SCANNER_LUV("scanner", null, 1, 1, 0, "scanner", VoltageTier.LUV);

    public final String id;
    public final GtRecipeMap recipeMap;
    public final int inputs, outputs, tank;
    public final String gui;
    /** GT mTier: напряжение входа, ёмкость (V × 64) и разгон. */
    public final VoltageTier tier;

    LvMachineType(String id, GtRecipeMap recipeMap, int inputs, int outputs, int tank, String gui) {
        this(id, recipeMap, inputs, outputs, tank, gui, VoltageTier.LV);
    }

    LvMachineType(String id, GtRecipeMap recipeMap, int inputs, int outputs, int tank, String gui, VoltageTier tier) {
        this.id = id;
        this.recipeMap = recipeMap;
        this.inputs = inputs;
        this.outputs = outputs;
        this.tank = tank;
        this.gui = gui;
        this.tier = tier;
    }

    public String blockId() {
        return id + "_" + tier.name().toLowerCase(java.util.Locale.ROOT);
    }

    public long voltage() {
        return tier.voltage();
    }

    /** GT_Container_BasicMachine: координаты входных слотов (1..9). */
    public int[][] inputSlots() {
        return switch (inputs) {
            case 1 -> new int[][]{{53, 25}};
            case 2 -> new int[][]{{35, 25}, {53, 25}};
            case 3 -> new int[][]{{17, 25}, {35, 25}, {53, 25}};
            case 4 -> new int[][]{{35, 16}, {53, 16}, {35, 34}, {53, 34}};
            case 5 -> new int[][]{{17, 16}, {35, 16}, {53, 16}, {35, 34}, {53, 34}};
            case 6 -> new int[][]{{17, 16}, {35, 16}, {53, 16}, {17, 34}, {35, 34}, {53, 34}};
            default -> first(inputs, new int[][]{{17, 7}, {35, 7}, {53, 7}, {17, 25}, {35, 25}, {53, 25}, {17, 43}, {35, 43}, {53, 43}});
        };
    }

    /** GT_Container_BasicMachine: координаты выходных слотов (1..9). */
    public int[][] outputSlots() {
        return switch (outputs) {
            case 1, 2, 3 -> first(outputs, new int[][]{{107, 25}, {125, 25}, {143, 25}});
            case 4 -> new int[][]{{107, 16}, {125, 16}, {107, 34}, {125, 34}};
            case 5, 6 -> first(outputs, new int[][]{{107, 16}, {125, 16}, {143, 16}, {107, 34}, {125, 34}, {143, 34}});
            default -> first(outputs, new int[][]{{107, 7}, {125, 7}, {143, 7}, {107, 25}, {125, 25}, {143, 25}, {107, 43}, {125, 43}, {143, 43}});
        };
    }

    private static int[][] first(int n, int[][] all) {
        int[][] r = new int[n][];
        System.arraycopy(all, 0, r, 0, n);
        return r;
    }
}
