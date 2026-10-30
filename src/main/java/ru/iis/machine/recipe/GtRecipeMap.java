package ru.iis.machine.recipe;

/** Карты рецептов GT5 (GT_Recipe.GT_Recipe_Map), перенесённые в эпоху пара. */
public enum GtRecipeMap {
    MACERATOR("macerator", 1, 3),
    FORGE_HAMMER("forge_hammer", 1, 1),
    COMPRESSOR("compressor", 1, 1),
    EXTRACTOR("extractor", 1, 1),
    ALLOY_SMELTER("alloy_smelter", 6, 1),
    PRIMITIVE_BLAST("primitive_blast", 3, 3),
    // LV (slice 4)
    WIREMILL("wiremill", 1, 2),
    BENDER("bender", 2, 1),
    LATHE("lathe", 1, 2),
    ASSEMBLER("assembler", 6, 1),
    MIXER("mixer", 6, 1),
    POLARIZER("polarizer", 1, 1),
    ORE_WASHER("ore_washer", 2, 3),
    COKE_OVEN("coke_oven", 1, 1),
    // LV II: доводка руды и резка
    CENTRIFUGE("centrifuge", 2, 6),
    SIFTER("sifter", 1, 9),
    ELECTROMAGNETIC_SEPARATOR("electromagnetic_separator", 1, 3),
    AUTOCLAVE("autoclave", 1, 1),
    CUTTER("cutter", 1, 2),
    CANNER("canner", 2, 2),
    // EBF slice: sBlastRecipes (2 входа, 2 выхода, по жидкости на вход и выход; temp — нагрев катушек)
    BLAST("blast", 2, 2),
    // LV III: химия и расплавы (D76): sElectrolyzerRecipes, sChemicalRecipes, sFluidExtractionRecipes, sFluidSolidficationRecipes
    ELECTROLYZER("electrolyzer", 2, 6),
    CHEMICAL_REACTOR("chemical_reactor", 3, 3),
    FLUID_EXTRACTOR("fluid_extractor", 1, 1),
    FLUID_SOLIDIFIER("fluid_solidifier", 1, 1),
    // LV IV: sDistilleryRecipes (схема — вход, 1 жидкость → 1 жидкость + предмет), sChemicalBathRecipes (1 вход, 3 выхода, жидкость)
    DISTILLERY("distillery", 1, 1),
    CHEMICAL_BATH("chemical_bath", 1, 3),
    // MV (D81): sThermalCentrifugeRecipes (1 вход, 3 выхода), sVacuumRecipes (1 → 1)
    THERMAL_CENTRIFUGE("thermal_centrifuge", 1, 3),
    VACUUM_FREEZER("vacuum_freezer", 1, 1),
    // D83: sExtruderRecipes (материал + насадка, насадка не расходуется — поле keep)
    EXTRUDER("extruder", 2, 1),
    // sPressRecipes (вход + форма, форма не расходуется), sFluidHeaterRecipes (схема, жидкость → жидкость)
    FORMING_PRESS("forming_press", 2, 1),
    FLUID_HEATER("fluid_heater", 1, 0),
    // sArcFurnaceRecipes: 1 вход, до 6 выходов, кислород (литров — сколько тиков длится рецепт)
    ARC_FURNACE("arc_furnace", 1, 6),
    // D86: sLaserEngraverRecipes (заготовка + линза, линза не расходуется)
    LASER_ENGRAVER("laser_engraver", 2, 1),
    // D92: sBrewingRecipes (предмет + 750 л жидкости → 750 л), sCircuitAssemblerRecipes (до 6 входов + припой → 1)
    BREWERY("brewery", 1, 0),
    CIRCUIT_ASSEMBLER("circuit_assembler", 6, 1),
    // D101: sFluidCannerRecipes (предмет + жидкость → предмет), одноразовые батарейки GT
    FLUID_CANNER("fluid_canner", 1, 1),
    // D103: sRockBreakerFakeRecipes (по программной схеме: булыжник, камень, обсидиан из красной пыли), sBoxinatorRecipes,
    // sUnboxinatorRecipes
    ROCK_BREAKER("rock_breaker", 2, 1),
    PACKAGER("packager", 2, 1),
    UNPACKAGER("unpackager", 1, 2),
    // D104: многоблоки — sImplosionRecipes, sPyrolyseRecipes, sDistillationRecipes (до 11 жидкостей на выходе — по этажу на
    // каждую), sCrackingRecipes (две жидкости на входе)
    IMPLOSION("implosion", 2, 2),
    PYROLYSE("pyrolyse", 2, 1),
    DISTILLATION_TOWER("distillation_tower", 0, 1, 1, 11),
    CRACKING("cracking", 0, 0, 2, 1);

    public final String id;
    public final int maxInputs, maxOutputs;
    /** Сколько жидкостей на входе и выходе (у одноблочных машин GT — по одной). */
    public final int maxFluidInputs, maxFluidOutputs;

    GtRecipeMap(String id, int maxInputs, int maxOutputs) {
        this(id, maxInputs, maxOutputs, 1, 1);
    }

    GtRecipeMap(String id, int maxInputs, int maxOutputs, int maxFluidInputs, int maxFluidOutputs) {
        this.id = id;
        this.maxInputs = maxInputs;
        this.maxOutputs = maxOutputs;
        this.maxFluidInputs = maxFluidInputs;
        this.maxFluidOutputs = maxFluidOutputs;
    }
}
