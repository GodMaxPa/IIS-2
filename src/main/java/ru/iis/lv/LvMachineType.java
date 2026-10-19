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
    ORE_WASHER("ore_washer", GtRecipeMap.ORE_WASHER, 2, 3, 16000, "orewasher");

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

    /** GT_Container_BasicMachine: координаты входных слотов. */
    public int[][] inputSlots() {
        return switch (inputs) {
            case 1 -> new int[][]{{53, 25}};
            case 2 -> new int[][]{{35, 25}, {53, 25}};
            default -> new int[][]{{17, 16}, {35, 16}, {53, 16}, {17, 34}, {35, 34}, {53, 34}};
        };
    }

    public int[][] outputSlots() {
        int[][] all = {{107, 25}, {125, 25}, {143, 25}};
        int[][] r = new int[outputs][];
        System.arraycopy(all, 0, r, 0, outputs);
        return r;
    }
}
