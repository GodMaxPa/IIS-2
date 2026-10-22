package ru.iis.bio.item;

import net.minecraft.world.item.Item;

/**
 * Каменный разделочный нож Био (W02). Сам предмет простой: тушу даёт {@link Butchery} при убийстве этим ножом,
 * соскоб культуры — блок живой ткани, разделку — площадка (износ 1 за партию).
 */
public class BioKnifeItem extends Item {
    public BioKnifeItem(Properties properties) {
        super(properties);
    }
}
