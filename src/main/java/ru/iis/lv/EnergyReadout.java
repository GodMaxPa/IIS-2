package ru.iis.lv;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import ru.iis.energy.logic.CableState;
import ru.iis.energy.logic.EnergyNet;
import ru.iis.lv.block.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Показания мультиметра (по образцу GT5 Portable Scanner: напряжение, ток, запас, средний поток EU).
 * Подробный отчёт — в чат, короткая строка — над панелью быстрого доступа.
 */
public final class EnergyReadout {
    private EnergyReadout() {}

    private static MutableComponent t(String key, Object... args) {
        return Component.translatable("meter.iis." + key, args);
    }

    /** Ток в проводе за последнюю секунду (0, если пакетов не было). */
    private static long[] cableNow(ServerLevel sl, CableBlockEntity be) {
        CableState s = be.state;
        if (sl.getGameTime() - s.lastTick() > 20) return new long[]{0, 0};
        return new long[]{s.voltageLast20, s.amperageLast20};
    }

    public static List<Component> report(ServerLevel sl, BlockPos pos) {
        List<Component> r = new ArrayList<>();
        var state = sl.getBlockState(pos);
        var be = sl.getBlockEntity(pos);
        r.add(state.getBlock().getName().copy().withStyle(ChatFormatting.GOLD));
        if (state.getBlock() instanceof CableBlock cb && be instanceof CableBlockEntity c) {
            var spec = cb.spec;
            long[] now = cableNow(sl, c);
            r.add(t("cable.limits", spec.voltage(), spec.amperage(), spec.loss()));
            r.add(t("cable.now", now[0], now[1]).withStyle(now[1] > spec.amperage() || now[0] > spec.voltage() ? ChatFormatting.RED : ChatFormatting.GREEN));
            r.add(t("cable.heat", c.state.overheat, ru.iis.energy.logic.CableState.HEATING_LIMIT)
                    .withStyle(c.state.overheat > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
            if (!spec.insulated() && now[0] > 0) r.add(t("cable.shock", EnergyNet.shockDamage(now[0], now[1])).withStyle(ChatFormatting.RED));
            return r;
        }
        if (be instanceof ElectricBlockEntity e) {
            r.add(t("stored", e.energy.stored, e.energy.capacity));
            if (e.energy.inputVoltage > 0) r.add(t("input", e.energy.inputVoltage, e.energy.maxAmperesIn));
            if (e.outputVoltage() > 0) r.add(t("output", e.outputVoltage(), e.outputAmperes()));
            r.add(t("flow", e.avgEuIn, e.avgEuOut, e.lastVoltageIn));
            if (e instanceof ElectricMachineBlockEntity m) {
                var l = m.logic();
                if (l.maxProgress > 0) r.add(t("progress", Math.max(0, l.progress), l.maxProgress, l.eut));
                else r.add(t("idle"));
                if (l.stuttering) r.add(t("stutter").withStyle(ChatFormatting.RED));
                if (m.type().tank > 0) r.add(t("tank", m.tank().amount(), m.type().tank));
            } else if (e instanceof GeneratorBlockEntity g) {
                r.add(t("tank", g.tank().amount(), g.type().tank));
            }
            if (e.energy.stored >= e.energy.capacity / 5 && e.energy.capacity > 0) r.add(t("danger").withStyle(ChatFormatting.YELLOW));
            return r;
        }
        if (be instanceof CokeOvenBlockEntity o) {
            r.add(t(o.formed() ? "formed" : "not_formed"));
            r.add(t("tank", o.tank().amount(), CokeOvenBlockEntity.TANK));
            return r;
        }
        r.add(t("nothing").withStyle(ChatFormatting.GRAY));
        return r;
    }

    /** Короткая строка для «живого» замера. */
    public static Component line(ServerLevel sl, BlockPos pos) {
        var state = sl.getBlockState(pos);
        var be = sl.getBlockEntity(pos);
        if (state.getBlock() instanceof CableBlock cb && be instanceof CableBlockEntity c) {
            long[] now = cableNow(sl, c);
            boolean over = now[1] > cb.spec.amperage() || now[0] > cb.spec.voltage();
            return t("line.cable", now[0], now[1], cb.spec.voltage(), cb.spec.amperage(), c.state.overheat)
                    .withStyle(over ? ChatFormatting.RED : now[1] > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        }
        if (be instanceof ElectricBlockEntity e) {
            return t("line.machine", e.energy.stored, e.energy.capacity, e.avgEuIn, e.avgEuOut).withStyle(ChatFormatting.AQUA);
        }
        return null;
    }
}
