package ru.iis.lv;

import ru.iis.energy.logic.CableSpec;

import java.util.ArrayList;
import java.util.List;

/** Каталог проводов и кабелей (без зависимостей от Minecraft; tools/gen/lv.py пишет тот же список в iis/cables.json). */
public final class CableSpecs {
    private CableSpecs() {}

    /** id, материал, толщина (1/2/4/8/12/16), изоляция, предельное напряжение, ампераж, потеря на блок, ширина в пикселях. */
    public record Spec(String id, String material, int size, boolean insulated, long voltage, long amperage, long loss, double thickness) {
        public CableSpec logic() {
            return new CableSpec(voltage, amperage, loss, insulated);
        }
    }

    /**
     * GT_Loader_MetaTileEntities#makeWires:1468-1496 (HardCoreCableLoss=false): материал, потеря изол./голый, ампер на 1x, напряжение.
     * Только материалы, которые есть в ИИС 2 (D79).
     */
    private static final Object[][] WIRES = {
            {"red_alloy", 0, 1, 1, 8L}, {"cobalt", 2, 4, 2, 32L}, {"lead", 2, 4, 2, 32L}, {"tin", 1, 2, 1, 32L},
            {"zinc", 1, 2, 1, 32L}, {"soldering_alloy", 1, 2, 1, 32L}, {"iron", 3, 6, 2, 128L}, {"nickel", 3, 6, 3, 128L},
            {"cupronickel", 3, 6, 2, 128L}, {"copper", 2, 4, 1, 128L}, {"annealed_copper", 1, 2, 1, 128L},
            {"gold", 2, 4, 3, 512L}, {"electrum", 2, 4, 2, 512L}, {"silver", 1, 2, 1, 512L},
            {"steel", 2, 4, 2, 2048L}, {"aluminium", 1, 2, 1, 2048L}, {"platinum", 1, 2, 2, 8192L},
            {"kanthal", 3, 6, 4, 512L}};   // D93: катушки HV
    private static final int[] SIZES = {1, 2, 4, 8, 12, 16};
    /** Ширина GT: провод 0.125…0.75 блока, кабель 0.25…0.75 (в пикселях ×16); кабеля 16x в GT нет (закомментирован). */
    private static final double[][] THICK = {{2, 4}, {4, 6}, {6, 8}, {8, 10}, {10, 12}, {12, 0}};

    public static final List<Spec> ALL;
    static {
        List<Spec> all = new ArrayList<>();
        for (Object[] w : WIRES) {
            for (int i = 0; i < SIZES.length; i++) {
                for (boolean ins : new boolean[]{false, true}) {
                    if (ins && THICK[i][1] == 0) continue;
                    String id = (ins ? "cable_" : "wire_") + w[0] + "_" + SIZES[i] + "x";
                    all.add(new Spec(id, (String) w[0], SIZES[i], ins, (Long) w[4], (long) (Integer) w[3] * SIZES[i],
                            ins ? (Integer) w[1] : (Integer) w[2], THICK[i][ins ? 1 : 0]));
                }
            }
        }
        ALL = List.copyOf(all);
    }
}
