package ru.iis.gear;

import net.minecraft.world.item.ItemStack;
import ru.iis.lv.item.BatteryItem;

/** Электрическая деталь брони IC2: заряд вместо прочности. Ранцы-батареи отдают заряд инструментам, остальные только заряжаются. */
public class GearArmorItem extends BatteryItem {
    /** Доля поглощения всей брони этого вида: нано 0,9, квант 1; 0 — не защищает (очки, ранцы). */
    public final double ratio;
    public final long euPerDamage;
    private final boolean battery;

    public GearArmorItem(long capacity, long transfer, int tier, double ratio, long euPerDamage, boolean battery, Properties p) {
        super(capacity, transfer, tier, p);
        this.ratio = ratio;
        this.euPerDamage = euPerDamage;
        this.battery = battery;
    }

    @Override
    public boolean isBattery() {
        return battery;
    }

    /** Шлем видит в темноте, когда прибор включён. */
    public boolean nightVision() {
        return false;
    }

    /** Включён ли прибор (ночное зрение). */
    public static boolean on(ItemStack stack) {
        return stack.getOrDefault(GearContent.MODE.get(), 0) != 0;
    }
}
