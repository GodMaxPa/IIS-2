package ru.iis.cover;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** GT_Cover_Fluidfilter#letsFluidIn / #letsFluidOut: 8 режимов (D80). */
class FluidFilterTest {
    @Test
    void modesZeroToThreeFilterTheInput() {
        assertTrue(FluidFilterLogic.in(0, true, true), "0: the chosen fluid goes in");
        assertFalse(FluidFilterLogic.in(0, true, false), "0: other fluids do not");
        assertFalse(FluidFilterLogic.out(0, true, true), "0: no output");
        assertFalse(FluidFilterLogic.in(1, true, true), "1: inverted input");
        assertTrue(FluidFilterLogic.in(1, true, false));
        assertTrue(FluidFilterLogic.out(2, true, false), "2: any output");
        assertTrue(FluidFilterLogic.out(3, true, true), "3: any output");
        assertTrue(FluidFilterLogic.in(3, true, false) && !FluidFilterLogic.in(3, true, true));
    }

    @Test
    void modesFourToSevenFilterTheOutput() {
        assertFalse(FluidFilterLogic.in(4, true, true), "4: no input");
        assertTrue(FluidFilterLogic.out(4, true, true) && !FluidFilterLogic.out(4, true, false));
        assertFalse(FluidFilterLogic.in(5, true, false), "5: no input");
        assertTrue(FluidFilterLogic.out(5, true, false) && !FluidFilterLogic.out(5, true, true));
        assertTrue(FluidFilterLogic.in(6, true, false), "6: any input");
        assertTrue(FluidFilterLogic.out(6, true, true) && !FluidFilterLogic.out(6, true, false));
        assertTrue(FluidFilterLogic.in(7, true, true), "7: any input");
        assertTrue(FluidFilterLogic.out(7, true, false) && !FluidFilterLogic.out(7, true, true));
    }

    @Test
    void unknownFluidAlwaysPasses() {
        for (int mode = 0; mode < 8; mode++) {
            assertTrue(FluidFilterLogic.in(mode, false, false), "mode " + mode);
            assertTrue(FluidFilterLogic.out(mode, false, false), "mode " + mode);
        }
    }
}
