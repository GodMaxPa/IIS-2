package ru.iis.multi.logic;

/**
 * Вакуумный морозильник GT5-II (GT_MetaTileEntity_VacuumFreezer), без Minecraft (D81).
 *
 * Правила оригинала:
 *  - куб 3×3×3, середина пустая, контроллер — в середине стенки; на остальных 25 местах люки (энергия, входные и выходные
 *    шины и люки, обслуживание) где угодно, прочее — морозостойкие корпуса, не меньше 16;
 *  - уровень — как у ЭДП (сумма напряжений энергетических люков, не ниже LV);
 *  - разгон — как у ЭДП без нагрева: до 16 EU/t ×4^(уровень−1) / ÷2^(уровень−1), иначе ×4 потребление и ÷2 время, пока
 *    EU/t ≤ V предыдущего уровня;
 *  - каждый входной предмет проверяется отдельно, выход один.
 */
public final class FreezerLogic {
    private FreezerLogic() {}

    public static final int MIN_CASINGS = 16;

    /** GT checkMachine: tAmount >= 16. */
    public static boolean enoughCasings(int casings) {
        return casings >= MIN_CASINGS;
    }

    public static BlastLogic.Run overclock(int recipeEut, int recipeDuration, int tier) {
        return BlastLogic.overclock(recipeEut, recipeDuration, tier, 0, 0);
    }

    /** ProcessingIngot:103: горячий слиток → слиток за max(масса × 3, 1) тиков (120 EU/t — GT_Recipe(вход, выход, время)). */
    public static int hotIngotTicks(long mass) {
        return (int) Math.max(mass * 3, 1);
    }
}
