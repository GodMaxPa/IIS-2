package ru.iis.multi;

import org.junit.jupiter.api.Test;
import ru.iis.multi.logic.LargeTurbineLogic;
import ru.iis.multi.logic.LargeTurbineLogic.Size;

import static org.junit.jupiter.api.Assertions.*;

/** Большая паровая турбина и роторы GT5-II: числа оригинала (сталь: прочность 512, скорость 6, качество 2). */
class LargeTurbineLogicTest {
    @Test
    void steelSmallRotor() {
        assertEquals(300, LargeTurbineLogic.optimalFlow(Size.SMALL, 6.0f));
        assertEquals(7000, LargeTurbineLogic.baseEfficiency(Size.SMALL, 2));
        assertEquals(51200, LargeTurbineLogic.maxDamage(Size.SMALL, 512));
        assertEquals(375, LargeTurbineLogic.flowLimit(300));
        assertEquals(300, LargeTurbineLogic.shownOptimalFlow(300));
    }

    @Test
    void biggerRotors() {
        // нержавейка (скорость 7, качество 2): обычный — 700 л/т, КПД 95 %; большой — 1050 л/т, 120 %
        assertEquals(700, LargeTurbineLogic.optimalFlow(Size.NORMAL, 7.0f));
        assertEquals(9500, LargeTurbineLogic.baseEfficiency(Size.NORMAL, 2));
        assertEquals(1050, LargeTurbineLogic.optimalFlow(Size.LARGE, 7.0f));
        assertEquals(12000, LargeTurbineLogic.baseEfficiency(Size.LARGE, 2));
        assertEquals(10000, LargeTurbineLogic.baseEfficiency(Size.HUGE, 2));
        assertEquals(100L * 1920, LargeTurbineLogic.maxDamage(Size.LARGE, 640));
    }

    @Test
    void steamPowerAroundOptimum() {
        assertEquals(105, LargeTurbineLogic.steamPower(300, 300, 7000));  // оптимум: 2 л пара на EU × КПД
        assertEquals(105, LargeTurbineLogic.steamPower(375, 300, 7000));  // больше оптимума — не больше, но без штрафа
        assertEquals(26, LargeTurbineLogic.steamPower(150, 300, 7000));   // половина потока → половина × 0,5
        assertEquals(1, LargeTurbineLogic.steamPower(1, 300, 7000));      // хоть что-то
        assertEquals(0, LargeTurbineLogic.steamPower(0, 300, 7000));
    }

    @Test
    void outputChangesSmoothly() {
        assertEquals(10, LargeTurbineLogic.approach(0, 105));
        assertEquals(105, LargeTurbineLogic.approach(100, 105));
        assertEquals(50, LargeTurbineLogic.approach(0, 5000));            // 1 % разницы
        assertEquals(95, LargeTurbineLogic.approach(105, 0));
    }

    @Test
    void rotorWear() {
        assertEquals(16, LargeTurbineLogic.wear(105));                     // min(21, 105^0,6 = 16,3)
        assertEquals(2, LargeTurbineLogic.wear(10));                       // min(2, 3,98)
        assertEquals(1, LargeTurbineLogic.damagePercent(0, 51200));
        assertEquals(51, LargeTurbineLogic.damagePercent(25600, 51200));
    }
}
