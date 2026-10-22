package ru.iis.bio.block;

import net.minecraft.util.StringRepresentable;

/** Состояние клетки ткани: только что выросла / живая / голодающая (увядает). */
public enum Vitality implements StringRepresentable {
    FRESH("fresh"), ALIVE("alive"), STARVING("starving");

    private final String id;

    Vitality(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
