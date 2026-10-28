package ru.iis.rail;

/**
 * Типы путей Railcraft 9.12 (сборка ИИС 1, D102). Скорости — предел вагонетки на пути, блок/тик:
 * {@code SpeedControllerSlow} 0.12, ванильный 0.4, {@code SpeedControllerReinforced} 0.499 (на повороте 0.4),
 * скоростной — до {@code max.speed} конфига ИИС 1 (0.8), на уклоне 0.45, без разгона ускорителем — 0.39.
 * Цвет — окраска ванильной текстуры рельсов (свои текстуры, лицензия Railcraft не даёт брать его png).
 */
public enum TrackKind {
    WOODEN("wooden", 0.12, 0.12, 0x9C7A4E),
    STANDARD("standard", 0.4, 0.4, 0xFFFFFF),
    REINFORCED("reinforced", 0.499, 0.4, 0x6E7180),
    HIGH_SPEED("high_speed", 0.8, 0.45, 0xE0C060),
    ELECTRIC("electric", 0.4, 0.4, 0xD9884A);

    /** Railcraft {@code TrackSpeed.SPEED_CUTOFF}: ниже — вагонетка теряет режим высокой скорости. */
    public static final double SPEED_CUTOFF = 0.39;

    public final String id;
    /** Предел на прямом пути (у скоростного — в режиме высокой скорости). */
    public final double maxSpeed;
    /** Предел на повороте (у скоростного — на уклоне). */
    public final double cornerSpeed;
    public final int color;

    TrackKind(String id, double maxSpeed, double cornerSpeed, int color) {
        this.id = id;
        this.maxSpeed = maxSpeed;
        this.cornerSpeed = cornerSpeed;
        this.color = color;
    }

    /** Скоростной и переходный пути выдерживают высокую скорость (Railcraft {@code isHighSpeedTrackAt}). */
    public boolean highSpeedCapable() {
        return this == HIGH_SPEED;
    }
}
