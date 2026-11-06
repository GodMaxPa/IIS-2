package ru.iis.equipment;

import ru.iis.gear.GearArmorItem;

/** Силовая и плазменная броня (D128): поглощает как нано и квант, только дешевле на единицу урона. Способности — в {@link EquipmentEvents}. */
public class PoweredArmorItem extends GearArmorItem {
    public final boolean plasma;

    public PoweredArmorItem(long capacity, long transfer, int tier, double ratio, long euPerDamage, boolean plasma, Properties p) {
        super(capacity, transfer, tier, ratio, euPerDamage, false, p);
        this.plasma = plasma;
    }

    @Override
    public boolean nightVision() {
        return true;
    }
}
