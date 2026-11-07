package ru.iis.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShopLogicTest {
    @Test
    void valueOfCredits() {
        assertEquals(1, ShopLogic.value(0));
        assertEquals(8, ShopLogic.value(1));
        assertEquals(64, ShopLogic.value(2));
        assertEquals(1 + 16 + 64, ShopLogic.total(new int[]{1, 2, 1}));
    }

    @Test
    void paysExactlyWithSmallCoinsFirst() {
        assertArrayEquals(new int[]{2, 0, 1}, ShopLogic.pay(new int[]{5, 0, 1}, 3));
    }

    @Test
    void breaksABigCoinAndGivesChange() {
        // серебряный за медный: сдача 7 мелких не влезает в купроникель целиком, 63 = 7 по 8 и 7 по 1
        assertArrayEquals(new int[]{7, 7, 0}, ShopLogic.pay(new int[]{0, 0, 1}, 1));
    }

    @Test
    void keepsTheValueExact() {
        int[] coins = {3, 2, 1, 1};
        long before = ShopLogic.total(coins);
        for (long cost : new long[]{1, 7, 8, 9, 63, 64, 100, 511, before}) {
            assertEquals(before - cost, ShopLogic.total(ShopLogic.pay(coins, cost)), "цена " + cost);
        }
    }

    @Test
    void refusesWhenPoor() {
        assertNull(ShopLogic.pay(new int[]{7}, 8));
    }
}
