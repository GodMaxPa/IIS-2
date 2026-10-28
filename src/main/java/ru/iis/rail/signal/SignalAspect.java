package ru.iis.rail.signal;

import net.minecraft.util.StringRepresentable;

/** Показание сигнала Railcraft ({@code SignalAspect}): мигающий красный — сигнал не спарен или не видит путь. */
public enum SignalAspect implements StringRepresentable {
    GREEN, YELLOW, RED, BLINK_RED, OFF;

    /** Цвет лампы в модели (мигающий красный рисуется попеременно красным и выключенным). */
    public enum Lamp implements StringRepresentable {
        OFF, GREEN, YELLOW, RED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public Lamp lamp(boolean blinkPhase) {
        return switch (this) {
            case GREEN -> Lamp.GREEN;
            case YELLOW -> Lamp.YELLOW;
            case RED -> Lamp.RED;
            case BLINK_RED -> blinkPhase ? Lamp.RED : Lamp.OFF;
            case OFF -> Lamp.OFF;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
