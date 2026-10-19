package ru.iis.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** Блок, на который ставятся покрытия (GT ICoverable + IMachineProgress). */
public interface CoverHost {
    CoverSet covers();

    @Nullable Level coverLevel();

    BlockPos coverPos();

    /** Собственный инвентарь со стороны side, без фильтра покрытий. */
    @Nullable ResourceHandler<ItemResource> rawItems(Direction side);

    /** Собственный бак со стороны side (null — любой), без фильтра покрытий. */
    default @Nullable ResourceHandler<FluidResource> rawFluids(@Nullable Direction side) {
        return null;
    }

    /** Число слотов (для детектора предметов и манипулятора). */
    int slotCount();

    net.minecraft.world.item.ItemStack slot(int i);

    // --- энергия (GT getUniversalEnergyStored/Capacity) ---
    long euStored();

    long euCapacity();

    void useEu(long eu);

    default long avgEuIn() { return 0; }

    default long avgEuOut() { return 0; }

    default long inputVoltage() { return 0; }

    default long outputVoltage() { return 0; }

    /** Заряд батарей внутри (для детектора энергии «с батареями»): {заряд, ёмкость}. */
    default long[] batteryCharge() { return new long[]{0, 0}; }

    // --- работа (IMachineProgress) ---
    default boolean isMachine() { return false; }

    default boolean allowedToWork() { return true; }

    default void setAllowedToWork(boolean allowed) {}

    default int progress() { return 0; }

    default int maxProgress() { return 0; }

    /** Покрытия изменились: сохранить и разослать клиенту. */
    void coversChanged();
}
