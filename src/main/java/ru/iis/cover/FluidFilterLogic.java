package ru.iis.cover;

/**
 * Правила фильтра жидкости GT5 без Minecraft (GT_Cover_Fluidfilter#letsFluidIn / #letsFluidOut; D80).
 * mode — режим 0…7, known — известно, какая жидкость проходит, matches — это жидкость фильтра.
 */
public final class FluidFilterLogic {
    private FluidFilterLogic() {}

    /** Режимы 0–3 фильтруют вход (1, 3 — наоборот), 4–5 запрещают вход, 6–7 пропускают любой. */
    public static boolean in(int mode, boolean known, boolean matches) {
        if (!known) return true;
        if (mode == 4 || mode == 5) return false;
        if (mode == 6 || mode == 7) return true;
        return matches ? mode == 0 || mode == 2 : mode == 1 || mode == 3;
    }

    /** Режимы 0–1 запрещают выход, 2–3 пропускают любой, 4–7 фильтруют выход (5, 7 — наоборот). */
    public static boolean out(int mode, boolean known, boolean matches) {
        if (!known) return true;
        if (mode == 0 || mode == 1) return false;
        if (mode == 2 || mode == 3) return true;
        return matches ? mode == 4 || mode == 6 : mode == 5 || mode == 7;
    }
}
