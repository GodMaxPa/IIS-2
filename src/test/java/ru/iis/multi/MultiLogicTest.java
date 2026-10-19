package ru.iis.multi;

import org.junit.jupiter.api.Test;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.logic.Maintenance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Электрическая доменная печь и техобслуживание: числа GT5-II. */
class MultiLogicTest {
    @Test
    void twoLvHatchesGiveMvTier() {
        assertEquals(1, BlastLogic.machineTier(32));
        assertEquals(2, BlastLogic.machineTier(64));
        assertEquals(1, BlastLogic.machineTier(0));
    }

    @Test
    void aluminiumAtMvTierIsNotOverclocked() {
        // 120 EU/t > V[1]=32: без разгона; нагрев 1800 − 1700 < 900: без скидки
        BlastLogic.Run r = BlastLogic.overclock(120, 1700, 2, 1800, 1700);
        assertEquals(120, r.eut());
        assertEquals(1700, r.duration());
    }

    @Test
    void heatBonusAndLowVoltageRecipes() {
        // сталь 1000 K на катушках 1800: запас 800 < 900 — без скидки
        assertEquals(120, BlastLogic.overclock(120, 1000, 2, 1800, 1000).eut());
        // гипотетический рецепт 1000 K на 2700 K: запас 1700 → 1 шаг ×0,95
        assertEquals(114, BlastLogic.overclock(120, 1000, 2, 2700, 1000).eut());
        // рецепт ≤ 16 EU/t на уровне MV: ×4 потребление, ÷2 время
        BlastLogic.Run r = BlastLogic.overclock(16, 100, 2, 1800, 1000);
        assertEquals(64, r.eut());
        assertEquals(50, r.duration());
    }

    @Test
    void efficiencyRaisesEnergyUse() {
        assertEquals(120, BlastLogic.drainPerTick(120, 10000));
        assertEquals(240, BlastLogic.drainPerTick(120, 5000));
        assertEquals(1200, BlastLogic.drainPerTick(120, 0));
    }

    @Test
    void newMachineHasAllProblems() {
        Maintenance m = new Maintenance();
        assertEquals(6, m.problems());
        assertFalse(m.randomDamage(new Random(1)::nextInt));
        m.fix(Maintenance.Problem.WRENCH);
        assertEquals(5000, m.maxEfficiency());
        for (Maintenance.Problem p : Maintenance.Problem.values()) m.fix(p);
        assertEquals(10000, m.maxEfficiency());
        assertEquals(63, m.mask());
        Maintenance c = new Maintenance();
        c.setMask(m.mask());
        assertEquals(0, c.problems());
    }

    @Test
    void randomDamageIsRare() {
        Maintenance m = new Maintenance();
        m.setMask(63);
        Random r = new Random(42);
        // 1001 тик работы — одна проверка с шансом 1/6000
        int broken = 0;
        for (int run = 0; run < 2000; run++) {
            Maintenance x = new Maintenance();
            x.setMask(63);
            for (int t = 0; t < 1002; t++) x.randomDamage(r::nextInt);
            if (x.problems() > 0) broken++;
        }
        assertTrue(broken < 10, "broken " + broken);
    }
}
