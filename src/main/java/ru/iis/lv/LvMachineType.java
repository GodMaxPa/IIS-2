package ru.iis.lv;

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
    CUTTER("cutter", GtRecipeMap.CUTTER, 1, 2, 8000, "cutter");

    public final String id;
    public final GtRecipeMap recipeMap;
    public final int inputs, outputs, tank;
    public final String gui;

    LvMachineType(String id, GtRecipeMap recipeMap, int inputs, int outputs, int tank, String gui) {
        this.id = id;
        this.recipeMap = recipeMap;
        this.inputs = inputs;
        this.outputs = outputs;
        this.tank = tank;
        this.gui = gui;
    }

    public String blockId() {
        return id + "_lv";
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
