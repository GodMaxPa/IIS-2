package ru.iis.bio.core;

/**
 * Состояние колонии Био: питание, рост и отступление живой ткани. Чистая логика без зависимостей от Minecraft
 * (docs/BIO_PATH.md §3, D64). Ядро вызывает {@link #tickSecond} раз в секунду (20 игровых тиков) и выполняет
 * возвращённое действие в мире; здесь только числа и правила.
 *
 * <p>Числа в {@link Params} — временные значения первого прототипа (баланс согласует владелец).
 */
public final class ColonyState {

    /** Видимое состояние организма (оно же — вид ядра в мире). */
    public enum Mood {
        /** Ещё ни разу не кормили: очаг спит. */
        DORMANT,
        /** Кормили, но запас кончается: ткань не растёт, увядает. */
        HUNGRY,
        /** Сыт, расти некуда или нечем (предел территории). */
        CONTENT,
        /** Сыт и растёт. */
        GROWING,
        /** Недавно повреждён (ткань сломали): кратковременная тревога. */
        ALARMED
    }

    /** Что ядро должно сделать в мире по итогам секунды. */
    public enum Action { NONE, GROW, RETREAT }

    /** Параметры питания и роста. */
    public record Params(int nutritionCap, double baseUpkeep, double upkeepPerTile, int growCost, int growReserve,
                         int growIntervalSec, int maxTiles, int hungryBelow, int retreatAfterSec, int retreatIntervalSec,
                         int alarmSec) {
        /** Значения прототипа (D66, единица — мБ N0 с D70): запас 1 000 мБ, ядро без ткани тратит 0,5 мБ/с, 100 клеток ткани — 10 мБ/с. */
        public static final Params DEFAULT = new Params(1000, 0.5, 0.1, 15, 30, 4, 160, 20, 30, 3, 6);

        /** Те же значения с другой скоростью голода: множитель расхода питания и время до отступления ткани (серверный конфиг). */
        public Params tuned(double upkeepMultiplier, int retreatAfterSec) {
            return new Params(nutritionCap, baseUpkeep * upkeepMultiplier, upkeepPerTile * upkeepMultiplier, growCost, growReserve, growIntervalSec,
                    maxTiles, hungryBelow, Math.max(1, retreatAfterSec), retreatIntervalSec, alarmSec);
        }
    }

    private final Params p;
    private double nutrition;
    private boolean awake;
    private int tiles;
    private int starvedSec;
    private int growCooldown;
    private int retreatCooldown;
    private int alarmLeft;
    private boolean canGrowNow;

    public ColonyState(Params params) {
        this.p = params;
    }

    public Params params() {
        return p;
    }

    // --- состояние ----------------------------------------------------------------------------------------------
    public double nutrition() {
        return nutrition;
    }

    public boolean awake() {
        return awake;
    }

    public int tiles() {
        return tiles;
    }

    public int starvedSeconds() {
        return starvedSec;
    }

    public int alarmSecondsLeft() {
        return alarmLeft;
    }

    /** Ткань изменилась снаружи (игрок сломал клетку, мир подтвердил рост): ядро сообщает фактическое число клеток. */
    public void setTiles(int tiles) {
        this.tiles = Math.max(0, tiles);
    }

    public boolean isFull() {
        return nutrition >= p.nutritionCap;
    }

    /** Накормить: единицы питания добавляются до предела запаса, возвращается принятое количество. */
    public int feed(int units) {
        if (units <= 0) return 0;
        int accepted = (int) Math.min(units, Math.floor(p.nutritionCap - nutrition));
        if (accepted <= 0) return 0;
        nutrition += accepted;
        awake = true;
        starvedSec = 0;
        return accepted;
    }

    /** Забрать питание (N0, мБ) на поддержание органа или ковшом; возвращает взятое. */
    public double drain(double mb) {
        double n = Math.max(0, Math.min(mb, nutrition));
        nutrition -= n;
        return n;
    }

    /** Прямо задать запас (откат транзакции переноса жидкости). */
    public void setNutrition(double mb) {
        nutrition = Math.max(0, Math.min(p.nutritionCap, mb));
        if (nutrition > 0) awake = true;
    }

    /** Ткань повредили: колония тревожится на несколько секунд. */
    public void alarm() {
        alarmLeft = p.alarmSec;
    }

    public Mood mood() {
        if (alarmLeft > 0 && awake) return Mood.ALARMED;
        if (!awake) return Mood.DORMANT;
        if (nutrition < p.hungryBelow) return Mood.HUNGRY;
        return canGrowNow ? Mood.GROWING : Mood.CONTENT;
    }

    /** Расход питания за секунду на поддержание ядра и ткани. */
    public double upkeep() {
        return awake ? p.baseUpkeep + p.upkeepPerTile * tiles : 0;
    }

    /**
     * Одна секунда жизни колонии.
     *
     * @param frontierAvailable есть ли в мире место, куда ткань может вырасти (ядро проверяет территорию)
     * @return действие, которое нужно выполнить в мире
     */
    public Action tickSecond(boolean frontierAvailable) {
        if (alarmLeft > 0) alarmLeft--;
        if (growCooldown > 0) growCooldown--;
        if (retreatCooldown > 0) retreatCooldown--;
        if (!awake) {
            canGrowNow = false;
            return Action.NONE;
        }

        nutrition = Math.max(0, nutrition - upkeep());
        if (nutrition <= 0) starvedSec++;
        else starvedSec = 0;

        // голод: ткань отступает
        if (starvedSec >= p.retreatAfterSec && tiles > 0) {
            canGrowNow = false;
            if (retreatCooldown == 0) {
                retreatCooldown = p.retreatIntervalSec;
                return Action.RETREAT;
            }
            return Action.NONE;
        }

        canGrowNow = nutrition >= p.growCost + p.growReserve && tiles < p.maxTiles && frontierAvailable;
        if (canGrowNow && growCooldown == 0) {
            nutrition -= p.growCost;
            growCooldown = p.growIntervalSec;
            return Action.GROW;
        }
        return Action.NONE;
    }

    /** Рост не удался (место занято в последний момент): вернуть питание. */
    public void refundGrowth() {
        nutrition = Math.min(p.nutritionCap, nutrition + p.growCost);
        growCooldown = 0;
    }

    // --- сохранение ---------------------------------------------------------------------------------------------
    public record Snapshot(double nutrition, boolean awake, int tiles, int starvedSec, int growCooldown, int alarmLeft) {}

    public Snapshot snapshot() {
        return new Snapshot(nutrition, awake, tiles, starvedSec, growCooldown, alarmLeft);
    }

    public void restore(Snapshot s) {
        this.nutrition = Math.max(0, Math.min(p.nutritionCap, s.nutrition));
        this.awake = s.awake;
        this.tiles = Math.max(0, s.tiles);
        this.starvedSec = Math.max(0, s.starvedSec);
        this.growCooldown = Math.max(0, s.growCooldown);
        this.alarmLeft = Math.max(0, s.alarmLeft);
    }
}
