package ru.iis;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Настройки сервера (world/serverconfig/iis-server.toml). По умолчанию — как в ИИС (GregTech.cfg, раздел machines).
 * Владелец может выключить отдельные опасности без пересборки мода.
 */
public final class IISConfig {
    private IISConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.comment("Electrical hazards of GT5 (IIS GregTech.cfg [machines]). Опасности электричества GT5.").push("hazards");
    }

    public static final ModConfigSpec.BooleanValue CABLE_BURNING = B
            .comment("Overloaded cable (voltage or amperage above its limit) overheats and burns. Перегруженный кабель сгорает.")
            .define("cableBurning", true);
    public static final ModConfigSpec.BooleanValue OVERVOLTAGE_EXPLOSIONS = B
            .comment("A machine explodes from a packet above its input voltage. Взрыв машины от перенапряжения.")
            .define("overvoltageExplosions", true);
    public static final ModConfigSpec.BooleanValue WEATHER_EXPLOSIONS = B
            .comment("Rain, water, lava, fire and thunder near a charged machine (rain_causes_explosions etc.). Дождь, вода, огонь, гроза.")
            .define("weatherExplosions", true);
    public static final ModConfigSpec.BooleanValue WIRE_FIRE = B
            .comment("An exploding machine releases its energy into the wires (wirefire_on_explosion). Выброс энергии при взрыве.")
            .define("wireFireOnExplosion", true);
    public static final ModConfigSpec.BooleanValue SHOCK = B
            .comment("Bare (uninsulated) wires shock living things. Удар током от голых проводов.")
            .define("bareWireShock", true);

    static {
        B.pop();
        B.comment("GT5 pollution (IIS GregTech.cfg [pollution] EnablePollution=true). Загрязнение.").push("pollution");
    }

    public static final ModConfigSpec.BooleanValue POLLUTION = B
            .comment("Chunk pollution from boilers, furnaces, generators, multiblocks, explosions. Загрязнение чанков.")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue SMOG_LIMIT = B.defineInRange("smogLimit", 500000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue POISON_LIMIT = B.defineInRange("poisonLimit", 750000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue VEGETATION_LIMIT = B.defineInRange("vegetationLimit", 1000000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SOUR_RAIN_LIMIT = B.defineInRange("sourRainLimit", 2000000, 0, Integer.MAX_VALUE);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    /** Значение или умолчание, если конфиг ещё не загружен (юнит-тесты, ранний старт). */
    public static boolean on(ModConfigSpec.BooleanValue v) {
        try {
            return v.getAsBoolean();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }
}
