package ru.iis.energy.logic;

/**
 * Передача пакета EU приёмнику FE (D125), чистая логика.
 * Обычный приёмник: GT5 GT_MetaPipeEntity_Cable:306–327, блок «RF Compat». IIS OverpoweredStuff.cfg: OutputRF=true, 100EUtoRF=360, RFExplosions=false,
 * InputRF=false: 1 EU = 3,6 FE, обратно энергия не идёт, взрывов нет. Берётся один ампер за вызов, недобранный остаток ждёт на проводе.
 * AE2: в ИИС 1 приёмник энергии AE2 принимал EU напрямую, блок «IC2 Compat» (:292–303) стоит раньше RF и берёт амперы, пока есть спрос.
 * appeng.api.config.PowerUnits: 1 EU = 2 AE; в новом AE2 1 AE = 2 FE, отсюда 4 FE за 1 EU.
 */
public final class FeTransfer {
    private FeTransfer() {}

    public static final int RATE_DEFAULT = 360;
    public static final int RATE_AE2 = 400;

    /** Приёмник FE. */
    public interface Sink {
        /** Принять до {@code amount} FE, вернуть сколько принято. */
        long insert(long amount, boolean simulate);
    }

    /** Обычный приёмник: GT RF Compat. Возвращает использованные амперы. */
    public static long rf(Sink sink, long voltage, CableState cable) {
        long out = voltage * RATE_DEFAULT / 100;
        if (sink.insert(out, true) == out) {
            sink.insert(out, false);
            return 1;
        }
        if (sink.insert(out, true) > 0) {
            if (cable.feRest == 0) {
                cable.feRest = out - sink.insert(out, false);
                return 1;
            }
            cable.feRest -= sink.insert(cable.feRest, false);
        }
        return 0;
    }

    /** AE2: GT IC2 Compat, амперы берутся, пока приёмник просит. */
    public static long ae2(Sink sink, long voltage, long amperes) {
        long out = voltage * RATE_AE2 / 100;
        long used = 0;
        while (used < amperes && sink.insert(out, true) > 0) {
            sink.insert(out, false);
            used++;
        }
        return used;
    }
}
