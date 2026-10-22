package ru.iis.bio.block;

import net.minecraft.util.StringRepresentable;
import ru.iis.bio.core.ColonyState;

/** Вид ядра в мире (свойство блока): по нему выбирается анимированная текстура. */
public enum CoreMood implements StringRepresentable {
    DORMANT("dormant", 2), HUNGRY("hungry", 3), CONTENT("content", 6), GROWING("growing", 9), ALARMED("alarmed", 11);

    private final String id;
    public final int light;

    CoreMood(String id, int light) {
        this.id = id;
        this.light = light;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static CoreMood of(ColonyState.Mood m) {
        return values()[m.ordinal()];
    }
}
