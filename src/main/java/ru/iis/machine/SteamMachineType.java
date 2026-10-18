package ru.iis.machine;

import ru.iis.machine.recipe.GtRecipeMap;

/** Паровые машины GT5-II (common/tileentities/machines/steam). recipeMap == null — печь на ванильной плавке. */
public enum SteamMachineType {
    MACERATOR("macerator", GtRecipeMap.MACERATOR, 1, "macerator"),
    FORGE_HAMMER("forge_hammer", GtRecipeMap.FORGE_HAMMER, 1, "hammer"),
    COMPRESSOR("compressor", GtRecipeMap.COMPRESSOR, 1, "compressor"),
    EXTRACTOR("extractor", GtRecipeMap.EXTRACTOR, 1, "extractor"),
    ALLOY_SMELTER("alloy_smelter", GtRecipeMap.ALLOY_SMELTER, 2, "alloysmelter"),
    FURNACE("furnace", null, 1, "furnace");

    public final String id;
    public final GtRecipeMap recipeMap;
    public final int inputs;
    /** Имя GUI-текстуры GT без префикса уровня (Bronze/Steel). */
    public final String gui;

    SteamMachineType(String id, GtRecipeMap recipeMap, int inputs, String gui) {
        this.id = id;
        this.recipeMap = recipeMap;
        this.inputs = inputs;
        this.gui = gui;
    }

    public enum Tier {
        BRONZE("bronze", false, 6.0F),
        STEEL("steel", true, 12.0F);

        public final String id;
        /** Высокое давление: EU/t рецепта ×3, время ×1 (бронза: EU/t ×1, время ×2). */
        public final boolean highPressure;
        /** Ожог при выпуске пара (GT5 getSteamDamage). */
        public final float ventDamage;

        Tier(String id, boolean highPressure, float ventDamage) {
            this.id = id;
            this.highPressure = highPressure;
            this.ventDamage = ventDamage;
        }
    }

    public String blockId(Tier tier) {
        return "steam_" + id + "_" + tier.id;
    }
}
