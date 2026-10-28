package ru.iis.rail.cart;

import net.minecraft.world.item.Item;

/** Буровая головка Railcraft ({@code ItemBoreHead*}): прочность 1500 / 3000 / 6000, уровень 2 / 2 / 3, скорость 1.0 / 1.2 / 1.4. */
public class BoreHeadItem extends Item {
    public final int harvestLevel;
    public final float digModifier;

    public BoreHeadItem(int harvestLevel, float digModifier, Properties p) {
        super(p);
        this.harvestLevel = harvestLevel;
        this.digModifier = digModifier;
    }
}
