package ru.iis.lv;

/**
 * Расчёты электроинструментов GT5 без Minecraft (GT_MetaGenerated_Tool#doDamage, #onBlockDestroyed; GT_Shaped_Recipe).
 * <p>Любое действие стоит столько EU, сколько обычный инструмент потерял бы единиц прочности GT. Если заряда хватает,
 * с шансом 1/25 головка всё же теряет эти единицы. Если не хватает — забирается остаток, и инструмент становится
 * разряженным. Единица прочности у нас — 50 единиц GT (D25).
 */
public final class ElectricToolLogic {
    private ElectricToolLogic() {}

    /** Единиц GT в одной нашей единице прочности (D25). */
    public static final int GT_PER_POINT = 50;
    /** GT_MetaGenerated_Tool#doDamage: nextInt(0, 25) == 0. */
    public static final int WEAR_CHANCE = 25;

    /**
     * @param charge новый заряд, EU
     * @param wear   потеря прочности в наших единицах (0 — головка цела)
     * @param worked заряда хватило на действие целиком
     */
    public record Result(long charge, int wear, boolean worked) {}

    /** Действие ценой gtAmount единиц GT (= EU); roll — случайное число 0…24. */
    public static Result use(long charge, long gtAmount, int roll) {
        if (gtAmount <= 0) return new Result(charge, 0, true);
        if (charge >= gtAmount) {
            int wear = roll == 0 ? (int) Math.max(1, gtAmount / GT_PER_POINT) : 0;
            return new Result(charge - gtAmount, wear, true);
        }
        return new Result(0, 0, false);
    }

    /** GT onBlockDestroyed: (int) max(1, твёрдость блока × урон за блок). */
    public static long blockCost(float hardness, int gtPerBlock) {
        return (long) Math.max(1.0f, hardness * gtPerBlock);
    }

    /** GT_Shaped_Recipe#getCraftingResult: заряд нового инструмента — сумма зарядов батарей в сетке, не больше ёмкости. */
    public static long craftedCharge(long batteries, long capacity) {
        return Math.max(0, Math.min(batteries, capacity));
    }
}
