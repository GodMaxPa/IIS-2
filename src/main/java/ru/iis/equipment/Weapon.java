package ru.iis.equipment;

/**
 * Оружие Кибера по эпохам (D128): порох в паре и LV, патроны в MV–EV, лазеры в EV–LuV, плазма в ZPM–UV. Чистые числа, без Minecraft.
 * В ИИС 1 этого не было в таком виде (Flan's Mod жил отдельно), поэтому все числа — предложение и правятся здесь, одной строкой.
 */
public enum Weapon {
    //                 id                 эпоха     вид              урон  дробь разброс темп магазин перезарядка дальность патрон   EU за выстрел ёмкость тир  автомат зум   ход  отдача пробой радиус
    FLINTLOCK_PISTOL("flintlock_pistol", "steam", Kind.BULLET,     8, 1, 2.5, 10, 1, 40, 24, Ammo.POWDER, 0, 0, 0, false, 1.0F, 1.0F, 5.0F, 0, 0),
    MUSKET("musket", "steam", Kind.BULLET,                        14, 1, 1.0, 10, 1, 60, 48, Ammo.POWDER, 0, 0, 0, false, 1.3F, 0.8F, 6.0F, 0, 0),
    RIFLED_MUSKET("rifled_musket", "lv", Kind.BULLET,             18, 1, 0.4, 10, 1, 50, 72, Ammo.POWDER, 0, 0, 0, false, 1.6F, 0.8F, 5.0F, 0, 0),
    BLUNDERBUSS("blunderbuss", "lv", Kind.BULLET,                  3, 7, 7.0, 10, 1, 50, 14, Ammo.POWDER, 0, 0, 0, false, 1.0F, 0.8F, 8.0F, 0, 0),

    PISTOL("pistol", "mv", Kind.BULLET,                            6, 1, 1.2, 5, 12, 30, 40, Ammo.PISTOL, 0, 0, 0, false, 1.2F, 1.0F, 1.6F, 0, 0),
    SMG("smg", "mv", Kind.BULLET,                                  4, 1, 3.0, 2, 30, 40, 32, Ammo.PISTOL, 0, 0, 0, true, 1.2F, 1.0F, 0.7F, 0, 0),
    SHOTGUN("shotgun", "hv", Kind.BULLET,                        3.5, 8, 5.0, 16, 6, 60, 18, Ammo.SHELL, 0, 0, 0, false, 1.2F, 0.85F, 6.0F, 0, 0),
    ASSAULT_RIFLE("assault_rifle", "hv", Kind.BULLET,              7, 1, 1.2, 3, 30, 45, 64, Ammo.RIFLE, 0, 0, 0, true, 1.5F, 0.9F, 1.0F, 0, 0),
    GRENADE_LAUNCHER("grenade_launcher", "hv", Kind.GRENADE,      14, 1, 1.0, 24, 6, 70, 40, Ammo.GRENADE, 0, 0, 0, false, 1.3F, 0.85F, 4.0F, 0, 3.0F),
    SNIPER_RIFLE("sniper_rifle", "ev", Kind.BULLET,               36, 1, 0.05, 28, 5, 70, 160, Ammo.RIFLE, 0, 0, 0, false, 4.0F, 0.75F, 6.0F, 1, 0),
    MACHINE_GUN("machine_gun", "ev", Kind.BULLET,                  8, 1, 2.0, 2, 100, 120, 64, Ammo.RIFLE, 0, 0, 0, true, 1.3F, 0.6F, 0.9F, 0, 0),
    ROCKET_LAUNCHER("rocket_launcher", "ev", Kind.ROCKET,         40, 1, 0.3, 40, 1, 80, 96, Ammo.ROCKET, 0, 0, 0, false, 1.6F, 0.6F, 5.0F, 0, 4.5F),

    LASER_PISTOL("laser_pistol", "ev", Kind.LASER,                12, 1, 0.1, 5, 0, 0, 64, null, 1_500, 300_000, 4, false, 1.2F, 1.0F, 0.5F, 0, 0),
    LASER_RIFLE("laser_rifle", "iv", Kind.LASER,                  16, 1, 0.1, 3, 0, 0, 96, null, 3_000, 2_000_000, 5, true, 1.6F, 0.9F, 0.3F, 1, 0),
    LASER_CANNON("laser_cannon", "luv", Kind.LASER,              120, 1, 0.0, 20, 0, 0, 128, null, 60_000, 16_000_000, 6, false, 2.0F, 0.7F, 2.0F, 8, 0),

    PLASMA_RIFLE("plasma_rifle", "zpm", Kind.PLASMA,              30, 1, 0.4, 4, 0, 0, 96, null, 40_000, 64_000_000, 7, true, 1.6F, 0.9F, 0.8F, 0, 2.0F),
    PLASMA_CANNON("plasma_cannon", "uv", Kind.PLASMA,            300, 1, 0.1, 30, 0, 0, 128, null, 1_200_000, 512_000_000, 8, false, 2.0F, 0.6F, 4.0F, 0, 6.0F);

    public enum Kind { BULLET, GRENADE, ROCKET, LASER, PLASMA }

    /** Патрон: один предмет — один выстрел (дробовик и мушкетон тратят один заряд на всю дробь). */
    public enum Ammo {
        POWDER("powder_cartridge"), PISTOL("pistol_round"), RIFLE("rifle_round"), SHELL("shotgun_shell"), GRENADE("grenade_round"), ROCKET("rocket_round"), TANK("tank_shell");

        public final String id;

        Ammo(String id) {
            this.id = id;
        }
    }

    public final String id, era;
    public final Kind kind;
    public final double damage, spread;
    public final int pellets, interval, magazine, reload, range;
    public final Ammo ammo;
    public final long euPerShot, capacity;
    public final int tier;
    public final boolean automatic;
    public final float zoom, walkSpeed, recoil, blast;
    public final int pierce;

    Weapon(String id, String era, Kind kind, double damage, int pellets, double spread, int interval, int magazine, int reload, int range, Ammo ammo,
           long euPerShot, long capacity, int tier, boolean automatic, float zoom, float walkSpeed, float recoil, int pierce, float blast) {
        this.id = id;
        this.era = era;
        this.kind = kind;
        this.damage = damage;
        this.pellets = pellets;
        this.spread = spread;
        this.interval = interval;
        this.magazine = magazine;
        this.reload = reload;
        this.range = range;
        this.ammo = ammo;
        this.euPerShot = euPerShot;
        this.capacity = capacity;
        this.tier = tier;
        this.automatic = automatic;
        this.zoom = zoom;
        this.walkSpeed = walkSpeed;
        this.recoil = recoil;
        this.pierce = pierce;
        this.blast = blast;
    }

    public boolean electric() {
        return euPerShot > 0;
    }

    public boolean projectile() {
        return kind == Kind.GRENADE || kind == Kind.ROCKET || kind == Kind.PLASMA;
    }

    /** Скорость снаряда, блоков за тик. */
    public double projectileSpeed() {
        return switch (kind) {
            case GRENADE -> 1.4;
            case ROCKET -> 1.8;
            case PLASMA -> 2.6;
            default -> 0;
        };
    }

    public double projectileGravity() {
        return kind == Kind.GRENADE ? 0.05 : 0;
    }

    /** Урон на расстоянии: до 60 % дальности полный, дальше падает до 40 % на пределе. */
    public double damageAt(double distance) {
        double full = range * 0.6;
        if (distance <= full) return damage;
        double t = Math.min(1, (distance - full) / (range - full));
        return damage * (1 - 0.6 * t);
    }

    /** Урон взрыва на расстоянии от центра: от полного до четверти на краю, за краем ноль. */
    public double blastAt(double distance) {
        if (blast <= 0 || distance >= blast) return 0;
        return damage * (1 - 0.75 * distance / blast);
    }

    /** Разброс: прицеливание (присесть) уменьшает вдвое. */
    public double spreadDegrees(boolean aiming) {
        return aiming ? spread / 2 : spread;
    }

    /** Сколько патронов заберёт перезарядка: сколько не хватает, но не больше запаса. */
    public int reloadAmount(int loaded, int available) {
        return Math.max(0, Math.min(magazine - loaded, available));
    }

    /** Урон в секунду в упор, для сверки эпох между собой. */
    public double dps() {
        double perShot = damage * pellets;
        double cycle = interval;
        if (!electric() && magazine > 0) cycle = (interval * Math.max(0, magazine - 1) + Math.max(interval, reload)) / (double) magazine;
        return perShot * 20 / cycle;
    }
}
