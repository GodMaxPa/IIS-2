package ru.iis.multi.logic;

import java.util.function.IntUnaryOperator;

/**
 * Техобслуживание многоблочной машины GT5-II (GT_MetaTileEntity_MultiBlockBase), без Minecraft.
 *
 * Правила оригинала:
 *  - шесть неисправностей, каждая чинится своим инструментом; новая машина — со всеми шестью;
 *  - без единого исправного пункта машина не работает; КПД не выше 100 % − 10 % за каждую неисправность;
 *  - на ходу: раз в 1000 тиков работы с шансом 1/6000 появляется случайная неисправность.
 */
public final class Maintenance {
    public enum Problem { WRENCH, SCREWDRIVER, SOFT_HAMMER, HARD_HAMMER, SOLDERING, CROWBAR }

    public static final int IDEAL = 6;

    /** true — исправно. */
    private final boolean[] ok = new boolean[6];
    private int runtime;

    public boolean ok(Problem p) {
        return ok[p.ordinal()];
    }

    public void fix(Problem p) {
        ok[p.ordinal()] = true;
    }

    public void breakDown(Problem p) {
        ok[p.ordinal()] = false;
    }

    public int repairStatus() {
        int n = 0;
        for (boolean b : ok) if (b) n++;
        return n;
    }

    public int problems() {
        return IDEAL - repairStatus();
    }

    /** Наибольший КПД в сотых долях процента (10000 = 100 %). */
    public int maxEfficiency() {
        return 10000 - problems() * 1000;
    }

    /** GT doRandomMaintenanceDamage: возвращает false, если машина работать не может. */
    public boolean randomDamage(IntUnaryOperator nextInt) {
        if (repairStatus() == 0) return false;
        if (runtime++ > 1000) {
            runtime = 0;
            if (nextInt.applyAsInt(6000) == 0) ok[nextInt.applyAsInt(6)] = false;
        }
        return true;
    }

    /** Битовая маска исправных пунктов (для сохранения и GUI). */
    public int mask() {
        int m = 0;
        for (int i = 0; i < 6; i++) if (ok[i]) m |= 1 << i;
        return m;
    }

    public void setMask(int m) {
        for (int i = 0; i < 6; i++) ok[i] = (m & (1 << i)) != 0;
    }

    public int runtime() {
        return runtime;
    }

    public void setRuntime(int r) {
        runtime = r;
    }
}
