package ru.iis.bio.production;

/**
 * Ручные места и органы первого среза Б-1 (спецификация v0.7 §22.3, §22.6, D70): раскладка слотов и баков.
 * Одно место — одна партия за раз; партия запускается сама, когда в слотах есть всё для рецепта (игроку не нужно кликать).
 *
 * @param inputs    входные слоты предметов
 * @param outputs   выходные слоты
 * @param toolSlot  есть ли слот инструмента (нож, оправка)
 * @param inTanks   ёмкости входных баков, мБ
 * @param outTanks  ёмкости баков продукта, мБ
 * @param modes     число режимов (переключаются кнопкой)
 * @param colony    живое место: работает только рядом с колонией и питается её N0
 * @param lvN       LV-орган (§21.4, §21.8): рабочий расход N1 из своего буфера 250 мБ, мБ/т (в простое — 25%); 0 — не LV
 * @param lvE       рабочий расход E1 из своего буфера 250 мБ, мБ/т; 0 — без буфера E1
 */
public enum Station {
    /** W01 разделочная площадка (+ нож): туши, волокна, шнур, кости, каркас, подложка. */
    BUTCHER("butcher", 4, 4, true, new int[]{}, new int[]{}, 1, false),
    /** W03 ступа с пестиком: массы и костный порошок. */
    MORTAR("mortar", 1, 1, false, new int[]{}, new int[]{}, 1, false),
    /** W04 каменный варочный сосуд на очаге W05: гель, жидкое связующее. */
    POT("pot", 2, 1, false, new int[]{2000}, new int[]{1000, 1000}, 1, false),
    /** W06 мерный смесительный сосуд: взвесь, G0. */
    MIXER("mixer", 1, 0, false, new int[]{1000, 1000, 1000, 1000, 1000}, new int[]{1000, 1000}, 1, false),
    /** W08 ручной отжим: растительный экстракт (D72). */
    PRESS("press", 2, 1, false, new int[]{1000}, new int[]{1000, 500}, 1, false),
    /** W10 гравитационный фильтр со сменной прокладкой W09: вода W0 (D72). */
    FILTER("filter", 0, 0, true, new int[]{1000}, new int[]{1000, 500}, 1, false),
    /** Пищеварительный карман ядра: N0, ростовая фракция, гидролизат, посев. Пятый бак — N0 самой колонии. */
    POCKET("pocket", 4, 2, false, new int[]{2000, 1000}, new int[]{500, 1000, 1000}, 3, false),
    /** Ручная ростовая подложка: мягкая ткань, мембрана, первое ростовое ложе. */
    SUBSTRATE("substrate", 6, 1, true, new int[]{1000}, new int[]{}, 1, true),
    /** A01 ростовое ложе ULV: ткани, мембраны, ULV-органы (пасть-кормушка). */
    BED("bed", 6, 1, true, new int[]{1000}, new int[]{}, 2, true),
    /** A03 формовочное гнездо LV (§21.8, D73): одна позиция, оправка, 1 000 мБ G1; N1 0,020 и E1 0,010 мБ/т. */
    NEST("nest", 6, 1, true, new int[]{1000}, new int[]{}, 2, false, 0.020, 0.010),
    /** F06 питательная железа LV (§21.3): шесть входов, продукт 2 000, дренаж 1 000; N1 0,020 и E1 0,020 мБ/т. */
    GLAND("gland", 0, 0, false, new int[]{1000, 1000, 1000, 1000, 1000, 1000}, new int[]{2000, 1000}, 1, false, 0.020, 0.020),
    /** F09 мембранный фильтр LV: вода → W0 за 20 с, сменная прокладка B27; корпус N1 0,005 мБ/т. */
    MEMBRANE_FILTER("membrane_filter", 0, 0, true, new int[]{1000}, new int[]{1000, 500}, 1, false, 0.005, 0),
    // --- черновик Б-2 (D148): числа не утверждены; органы MV и HV пока питаются теми же буферами N1/E1, что и LV ---------------
    /** Отходная яма (неживая): сток и остатки → компост. */
    SUMP("sump", 1, 1, false, new int[]{4000}, new int[]{}, 1, false),
    /** Разрыхлитель LV (§6): руда → дроблёная руда. */
    LOOSENER("loosener", 1, 2, false, new int[]{}, new int[]{}, 1, false, 0.020, 0.020),
    /** Уплотнитель LV (§6): хитиновые чешуйки → пластина, оболочки следующего тира. */
    COMPACTOR("compactor", 3, 1, false, new int[]{1000}, new int[]{}, 1, false, 0.020, 0.020),
    /** Рудный желудок MV (§6): дроблёная руда + пищеварительная кислота → пыль и крошечные кучки, кислый сток. */
    ORE_STOMACH("ore_stomach", 1, 2, false, new int[]{1000}, new int[]{1000}, 1, false, 0.030, 0.030),
    /** Скрещиватель MV (§6): два посева расходуются, выходит гибридный посев. */
    CROSSBREEDER("crossbreeder", 3, 1, false, new int[]{1000}, new int[]{}, 1, false, 0.030, 0.020),
    /** Инкубатор MV (§6): зародыши и культуры; режим 1 — органы следующего класса. */
    INCUBATOR("incubator", 6, 1, false, new int[]{1000}, new int[]{}, 2, false, 0.030, 0.010),
    /** Изолятор культур MV (§6): чистые ферментные культуры и кислота. */
    ISOLATOR("isolator", 2, 1, false, new int[]{1000, 1000}, new int[]{1000}, 1, false, 0.020, 0),
    /** Ускоритель роста HV (§6): ткани и мембраны в несколько раз быстрее. */
    ACCELERATOR("accelerator", 6, 1, true, new int[]{1000}, new int[]{}, 1, false, 0.050, 0.050),
    /** Биохимический реактор HV (§6): выщелачивание руды ферментом, минеральное связующее. */
    BIOREACTOR("bioreactor", 2, 2, false, new int[]{1000, 1000}, new int[]{1000, 1000}, 1, false, 0.050, 0.050),
    /** Разделитель растворов HV (§6): сток → вода и осадок. */
    SEPARATOR("separator", 1, 1, false, new int[]{2000}, new int[]{1000, 1000}, 1, false, 0.040, 0.020);

    public final String id;
    public final int inputs, outputs, modes;
    public final boolean toolSlot, colony;
    public final int[] inTanks, outTanks;
    public final double lvN, lvE;

    /** Буфер поддержания LV-органа, мБ (§21.4). */
    public static final int LV_BUFFER = 250;

    Station(String id, int inputs, int outputs, boolean toolSlot, int[] inTanks, int[] outTanks, int modes, boolean colony) {
        this(id, inputs, outputs, toolSlot, inTanks, outTanks, modes, colony, 0, 0);
    }

    Station(String id, int inputs, int outputs, boolean toolSlot, int[] inTanks, int[] outTanks, int modes, boolean colony, double lvN, double lvE) {
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
        this.toolSlot = toolSlot;
        this.inTanks = inTanks;
        this.outTanks = outTanks;
        this.modes = modes;
        this.colony = colony;
        this.lvN = lvN;
        this.lvE = lvE;
    }

    public boolean lv() {
        return lvN > 0;
    }

    /** Ёмкость бака k в порядке StationLogic: входы, продукт, затем буферы N1/E1 (LV) или N0 колонии (карман). */
    public int tankCapacity(int k) {
        if (k < inTanks.length) return inTanks[k];
        k -= inTanks.length;
        if (k < outTanks.length) return outTanks[k];
        if (lv()) return LV_BUFFER;
        return ru.iis.bio.core.ColonyState.Params.DEFAULT.nutritionCap();
    }

    /** Индекс бака-буфера N1 (или -1). */
    public int n1Buffer() {
        return lv() ? inTanks.length + outTanks.length : -1;
    }

    public int e1Buffer() {
        return lvE > 0 ? inTanks.length + outTanks.length + 1 : -1;
    }

    public int slots() {
        return inputs + outputs + (toolSlot ? 1 : 0);
    }

    public int tanks() {
        return inTanks.length + outTanks.length + (this == POCKET ? 1 : 0) + (lv() ? 1 : 0) + (lvE > 0 ? 1 : 0);
    }

    public int toolIndex() {
        return toolSlot ? inputs + outputs : -1;
    }

    public static Station byId(String id) {
        for (Station s : values()) if (s.id.equals(id)) return s;
        throw new IllegalArgumentException("unknown bio station " + id);
    }
}
