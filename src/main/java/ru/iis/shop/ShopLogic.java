package ru.iis.shop;

/** Деньги без Minecraft: жетон каждого достоинства стоит 8 предыдущих (рецепты GT D119). Платёж любыми жетонами со сдачей. */
public final class ShopLogic {
    private ShopLogic() {}

    public static final int TIERS = 8, RATE = 8;

    public static long value(int tier) {
        long v = 1;
        for (int i = 0; i < tier; i++) v *= RATE;
        return v;
    }

    public static long total(int[] coins) {
        long t = 0;
        for (int i = 0; i < coins.length; i++) t += coins[i] * value(i);
        return t;
    }

    /**
     * Что останется после оплаты: сначала мелочь, потом крупнее, сдача самыми крупными жетонами, что влезают.
     * @return новые количества; null — денег не хватает
     */
    public static int[] pay(int[] coins, long cost) {
        if (total(coins) < cost) return null;
        int[] out = coins.clone();
        long left = cost;
        for (int i = 0; i < out.length && left > 0; i++) {
            long use = Math.min(out[i], (left + value(i) - 1) / value(i));
            out[i] -= (int) use;
            left -= use * value(i);
        }
        long change = -left;
        for (int i = out.length - 1; i >= 0 && change > 0; i--) {
            long n = change / value(i);
            out[i] += (int) n;
            change -= n * value(i);
        }
        return out;
    }
}
