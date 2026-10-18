package ru.iis.energy;

/**
 * Уровни напряжения GregTech 5 (перенесено из GT_Values.V / VN, GT5-II).
 * Значения совпадают с оригиналом: каждый следующий уровень в 4 раза выше.
 * В оригинале MAX = Integer.MAX_VALUE; оставляем так же.
 */
public enum VoltageTier {
    ULV("ULV", "Ultra Low Voltage", 8),
    LV("LV", "Low Voltage", 32),
    MV("MV", "Medium Voltage", 128),
    HV("HV", "High Voltage", 512),
    EV("EV", "Extreme Voltage", 2048),
    IV("IV", "Insane Voltage", 8192),
    LUV("LuV", "Ludicrous Voltage", 32768),
    ZPM("ZPM", "ZPM Voltage", 131072),
    UV("UV", "Ultimate Voltage", 524288),
    MAX("MAX", "Maximum Voltage", Integer.MAX_VALUE);

    private final String shortName;
    private final String longName;
    private final long voltage;

    VoltageTier(String shortName, String longName, long voltage) {
        this.shortName = shortName;
        this.longName = longName;
        this.voltage = voltage;
    }

    public String shortName() { return shortName; }
    public String longName() { return longName; }
    /** Максимальное напряжение пакета для этого уровня, EU/t. */
    public long voltage() { return voltage; }
    /** Номер уровня как в GT5 (ULV = 0, LV = 1, ...). */
    public int tier() { return ordinal(); }

    public static VoltageTier byTier(int tier) {
        VoltageTier[] all = values();
        if (tier < 0 || tier >= all.length) throw new IllegalArgumentException("Unknown voltage tier: " + tier);
        return all[tier];
    }

    /** Наименьший уровень, способный принять пакет данного напряжения. */
    public static VoltageTier forVoltage(long voltage) {
        if (voltage < 0) throw new IllegalArgumentException("Negative voltage: " + voltage);
        for (VoltageTier t : values()) if (voltage <= t.voltage) return t;
        return MAX;
    }
}
