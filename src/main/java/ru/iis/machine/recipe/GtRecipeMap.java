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
    // EBF slice: sBlastRecipes (2 входа, 2 выхода, по жидкости на вход и выход; temp — нагрев катушек)
    BLAST("blast", 2, 2);

    public final String id;
    public final int maxInputs, maxOutputs;

    GtRecipeMap(String id, int maxInputs, int maxOutputs) {
        this.id = id;
        this.maxInputs = maxInputs;
        this.maxOutputs = maxOutputs;
    }
}
