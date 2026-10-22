package ru.iis.bio;

import org.junit.jupiter.api.Test;
import ru.iis.bio.core.ColonyState;
import ru.iis.bio.core.ColonyState.Action;
import ru.iis.bio.core.ColonyState.Mood;

import static org.junit.jupiter.api.Assertions.*;

class ColonyStateTest {
    private static ColonyState fresh() {
        return new ColonyState(ColonyState.Params.DEFAULT);
    }

    @Test
    void sleepingUntilFed() {
        ColonyState c = fresh();
        assertEquals(Mood.DORMANT, c.mood());
        for (int i = 0; i < 100; i++) assertEquals(Action.NONE, c.tickSecond(true));
        assertEquals(0, c.nutrition());
        assertEquals(Mood.DORMANT, c.mood());
    }

    @Test
    void feedingWakesAndRespectsCap() {
        ColonyState c = fresh();
        assertEquals(40, c.feed(40));
        assertTrue(c.awake());
        assertEquals(960, c.feed(5000));
        assertEquals(1000, c.nutrition());
        assertTrue(c.isFull());
        assertEquals(0, c.feed(10));
    }

    @Test
    void drainTakesOnlyWhatIsThere() {
        ColonyState c = fresh();
        c.feed(30);
        assertEquals(10, c.drain(10), 1e-9);
        assertEquals(20, c.drain(100), 1e-9);
        assertEquals(0, c.nutrition(), 1e-9);
    }

    @Test
    void upkeepConsumesNutritionAndCausesHunger() {
        ColonyState c = fresh();
        c.feed(40); // 40 ед., расход 0.5/с, тканей нет, расти нечем (frontier нет)
        for (int i = 0; i < 39; i++) c.tickSecond(false);
        assertEquals(20.5, c.nutrition(), 1e-9);
        assertEquals(Mood.CONTENT, c.mood());
        c.tickSecond(false);
        c.tickSecond(false);
        assertEquals(Mood.HUNGRY, c.mood());
    }

    @Test
    void growsOnlyWhenFedAndSpendsNutrition() {
        ColonyState c = fresh();
        c.feed(44); // порог роста: 15 + 30 = 45
        assertEquals(Action.NONE, c.tickSecond(true));
        assertNotEquals(Mood.GROWING, c.mood());
        c.feed(60);
        assertEquals(Action.GROW, c.tickSecond(true));
        assertTrue(c.nutrition() < 100);
    }

    @Test
    void growthRespectsIntervalAndTileCap() {
        ColonyState c = fresh();
        c.feed(1000);
        assertEquals(Action.GROW, c.tickSecond(true));
        c.setTiles(1);
        // интервал 4 с: следующие 3 секунды роста нет
        for (int i = 0; i < 3; i++) assertEquals(Action.NONE, c.tickSecond(true));
        assertEquals(Action.GROW, c.tickSecond(true));
        c.setTiles(ColonyState.Params.DEFAULT.maxTiles());
        for (int i = 0; i < 10; i++) assertEquals(Action.NONE, c.tickSecond(true));
        assertEquals(Mood.CONTENT, c.mood());
    }

    @Test
    void noGrowthWithoutFrontier() {
        ColonyState c = fresh();
        c.feed(1000);
        for (int i = 0; i < 20; i++) assertNotEquals(Action.GROW, c.tickSecond(false));
        assertEquals(Mood.CONTENT, c.mood());
    }

    @Test
    void starvationRetreatsTissueGradually() {
        ColonyState c = fresh();
        c.feed(10);
        c.setTiles(10);
        int retreats = 0;
        for (int i = 0; i < 200; i++) {
            if (c.tickSecond(true) == Action.RETREAT) {
                retreats++;
                c.setTiles(c.tiles() - 1);
            }
        }
        assertEquals(0, c.nutrition());
        assertEquals(Mood.HUNGRY, c.mood());
        assertEquals(10, retreats, "вся ткань должна отступить, по одной клетке за раз");
        assertEquals(0, c.tiles());
    }

    @Test
    void feedingStopsRetreat() {
        ColonyState c = fresh();
        c.feed(1);
        c.setTiles(5);
        for (int i = 0; i < 29; i++) assertNotEquals(Action.RETREAT, c.tickSecond(false));
        c.feed(200);
        for (int i = 0; i < 10; i++) assertNotEquals(Action.RETREAT, c.tickSecond(false));
        assertEquals(0, c.starvedSeconds());
    }

    @Test
    void alarmIsTemporary() {
        ColonyState c = fresh();
        c.feed(500);
        c.alarm();
        assertEquals(Mood.ALARMED, c.mood());
        for (int i = 0; i < ColonyState.Params.DEFAULT.alarmSec(); i++) c.tickSecond(false);
        assertNotEquals(Mood.ALARMED, c.mood());
    }

    @Test
    void snapshotRoundTrip() {
        ColonyState a = fresh();
        a.feed(300);
        a.setTiles(7);
        a.tickSecond(true);
        ColonyState b = fresh();
        b.restore(a.snapshot());
        assertEquals(a.snapshot(), b.snapshot());
    }

    @Test
    void upkeepScalesWithTiles() {
        ColonyState c = fresh();
        c.feed(100);
        double base = c.upkeep();
        c.setTiles(50);
        assertEquals(base + 5.0, c.upkeep(), 1e-9);
    }
}
