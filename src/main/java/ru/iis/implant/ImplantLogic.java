package ru.iis.implant;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Правила киберимплантов (чистая логика, без Minecraft; D157, CONCEPT §16; ответы владельца 13.11 и 16.11).
 * <p>
 * Тело — семь анатомических областей. В каждой — один имплант ступени HV или EV. Импланты питаются запасом EU самого тела:
 * его даёт внутренняя батарея (область CELL), остальные области тратят заряд, пока работают. Заряд кончился или батареи нет —
 * бонусы выключаются, но импланты остаются. Все числа — черновик ИИ.
 */
public final class ImplantLogic {
    private ImplantLogic() {}

    public enum Slot {
        CELL, EYES, BRAIN, HEART, ARMS, LEGS, SKIN;

        public final String id = name().toLowerCase(Locale.ROOT);

        public static Slot byId(String id) {
            for (Slot s : values()) if (s.id.equals(id)) return s;
            return null;
        }
    }

    public enum Tier {
        HV(1, 1_000_000L, 100_000L), EV(2, 4_000_000L, 400_000L);

        /** Множитель бонуса и расхода. */
        public final int level;
        /** Ёмкость внутренней батареи этой ступени, EU. */
        public final long capacity;
        /** Сколько EU хирургический стол тратит на установку или снятие импланта этой ступени. */
        public final long surgeryEu;
        public final String id = name().toLowerCase(Locale.ROOT);

        Tier(int level, long capacity, long surgeryEu) {
            this.level = level;
            this.capacity = capacity;
            this.surgeryEu = surgeryEu;
        }
    }

    public record Kind(Slot slot, Tier tier) {
        public String id() {
            return "implant_" + slot.id + "_" + tier.id;
        }

        /** Расход заряда тела в работе, EU/т. */
        public int drain() {
            return slot == Slot.CELL ? 0 : (slot == Slot.EYES ? 1 : 2) * tier.level;
        }
    }

    public static final List<Kind> KINDS = Arrays.stream(Slot.values()).flatMap(s -> Arrays.stream(Tier.values()).map(t -> new Kind(s, t))).toList();

    /** Длительность операции, тиков; урон от неё (игрок не умирает: остаётся не меньше половины сердца); слабость после, тиков. */
    public static final int SURGERY_TICKS = 200, WEAKNESS_TICKS = 1200;
    public static final float SURGERY_DAMAGE = 6.0F;
    /** Зарядка тела: от стола рядом и от батарей в инвентаре, EU/т. */
    public static final long TABLE_CHARGE = 2048, BATTERY_CHARGE = 512;

    public static Kind byId(String id) {
        for (Kind k : KINDS) if (k.id().equals(id)) return k;
        return null;
    }

    /** Ёмкость тела: по установленной батарее, без неё — ноль. */
    public static long capacity(Kind[] body) {
        Kind cell = body[Slot.CELL.ordinal()];
        return cell == null ? 0 : cell.tier().capacity;
    }

    /** Суммарный расход всех областей, EU/т. */
    public static int drain(Kind[] body) {
        int d = 0;
        for (Kind k : body) if (k != null) d += k.drain();
        return d;
    }

    /** Итог шага длиной ticks: работают ли импланты и сколько заряда остаётся. Работают только при батарее и полном покрытии расхода. */
    public record Step(boolean active, long charge) {}

    public static Step step(Kind[] body, long charge, int ticks) {
        long cap = capacity(body);
        charge = Math.max(0, Math.min(charge, cap));
        long need = (long) drain(body) * ticks;
        if (cap == 0 || charge < need) return new Step(false, charge);
        return new Step(true, charge - need);
    }

    public static Kind[] parse(List<String> ids) {
        Kind[] body = new Kind[Slot.values().length];
        for (int i = 0; i < body.length && i < ids.size(); i++) body[i] = byId(ids.get(i));
        return body;
    }

    public static List<String> ids(Kind[] body) {
        return Arrays.stream(body).map(k -> k == null ? "" : k.id()).toList();
    }
}
