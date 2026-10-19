package ru.iis.lv;

import ru.iis.energy.logic.CableSpec;

import java.util.ArrayList;
import java.util.List;

/** Каталог проводов и кабелей LV (без зависимостей от Minecraft; tools/gen/lv.py пишет тот же список в iis/cables.json). */
public final class CableSpecs {
    private CableSpecs() {}

    /** id, материал, толщина (1/2/4), изоляция, предельное напряжение, ампераж, потеря на блок, ширина в пикселях. */
    public record Spec(String id, String material, int size, boolean insulated, long voltage, long amperage, long loss, double thickness) {
        public CableSpec logic() {
            return new CableSpec(voltage, amperage, loss, insulated);
        }
    }

    /** GT_Loader_MetaTileEntities#makeWires (HardCoreCableLoss=false): материал, потеря изол./голый, ампер на 1x, напряжение. */
    private static final Object[][] WIRES = {
            {"red_alloy", 0, 1, 1, 8L}, {"lead", 2, 4, 2, 32L}, {"tin", 1, 2, 1, 32L},
            {"copper", 2, 4, 1, 128L}, {"annealed_copper", 1, 2, 1, 128L}};
    /** Ширина GT: провод 1x/2x/4x = 0.125/0.25/0.375 блока, кабель 0.25/0.375/0.5 (в пикселях ×16). */
    private static final double[][] THICK = {{2, 4}, {4, 6}, {6, 8}};

    public static final List<Spec> ALL;
    static {
        List<Spec> all = new ArrayList<>();
        int[] sizes = {1, 2, 4};
        for (Object[] w : WIRES) {
            for (int i = 0; i < 3; i++) {
                for (boolean ins : new boolean[]{false, true}) {
                    String id = (ins ? "cable_" : "wire_") + w[0] + "_" + sizes[i] + "x";
                    all.add(new Spec(id, (String) w[0], sizes[i], ins, (Long) w[4], (long) (Integer) w[3] * sizes[i],
                            ins ? (Integer) w[1] : (Integer) w[2], THICK[i][ins ? 1 : 0]));
                }
            }
        }
        ALL = List.copyOf(all);
    }
}
