package ru.iis.machine.recipe;

/** Карты рецептов GT5 (GT_Recipe.GT_Recipe_Map), перенесённые в эпоху пара. */
public enum GtRecipeMap {
    MACERATOR("macerator", 1, 3),
    FORGE_HAMMER("forge_hammer", 1, 1),
    COMPRESSOR("compressor", 1, 1),
    EXTRACTOR("extractor", 1, 1),
    ALLOY_SMELTER("alloy_smelter", 2, 1),
    PRIMITIVE_BLAST("primitive_blast", 3, 3);

    public final String id;
    public final int maxInputs, maxOutputs;

    GtRecipeMap(String id, int maxInputs, int maxOutputs) {
        this.id = id;
        this.maxInputs = maxInputs;
        this.maxOutputs = maxOutputs;
    }
}
