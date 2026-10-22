package ru.iis.bio.hydraulics;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * Насос, подключённый к сети сосудов (§20.3): характеристика {@code H = H0 × max(0, 1 − Q/Q_free)}, питание работы и
 * необязательный вход (откуда насос берёт жидкость; у ядра входа нет — оно только создаёт напор в сети).
 */
public interface Pump {
    double h0();

    double qFree();

    /** Хватает ли питания на работу этот шаг при наихудшей нагрузке. */
    boolean canRun(int ticks);

    /** Списать рабочее питание за шаг при фактических напоре и расходе. */
    void charge(double h, double q, int ticks);

    /** Откуда насос забирает жидкость (LV-насос — с задней стороны); null — только напор. */
    default @Nullable ResourceHandler<FluidResource> inlet() {
        return null;
    }

    Component name();
}
