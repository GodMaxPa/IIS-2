package ru.iis.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Блок, на который ставятся покрытия (GT ICoverable + IMachineProgress). Методы по умолчанию годятся для блок-сущности
 * без энергии и инвентаря (труба, люк): их переопределяет тот, у кого это есть.
 */
public interface CoverHost {
    CoverSet covers();

    default @Nullable Level coverLevel() {
        return this instanceof BlockEntity be ? be.getLevel() : null;
    }

    default BlockPos coverPos() {
        return this instanceof BlockEntity be ? be.getBlockPos() : BlockPos.ZERO;
    }

    /** Собственный инвентарь со стороны side, без фильтра покрытий. */
    default @Nullable ResourceHandler<ItemResource> rawItems(Direction side) {
        return null;
    }

    /** Собственный бак со стороны side (null — любой), без фильтра покрытий. */
    default @Nullable ResourceHandler<FluidResource> rawFluids(@Nullable Direction side) {
        return null;
    }

    /** Число слотов (для детектора предметов и манипулятора). */
    default int slotCount() {
        return 0;
    }

    default ItemStack slot(int i) {
        return ItemStack.EMPTY;
    }

    // --- энергия (GT getUniversalEnergyStored/Capacity; у паровых машин — пар) ---
    default long euStored() { return 0; }

    default long euCapacity() { return 0; }

    default void useEu(long eu) {}
    /** Энергия от солнечной панели (GT injectEnergyUnits). */
    default void addEu(long eu) {}

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
    default void coversChanged() {
        if (!(this instanceof BlockEntity be)) return;
        be.setChanged();
        be.invalidateCapabilities();
        Level level = be.getLevel();
        if (level != null) {
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
        }
    }

    /** Работа покрытий за тик хозяина; при смене красного сигнала — обновить соседей. */
    default void tickCovers(net.minecraft.server.level.ServerLevel level) {
        if (!covers().any() || !(this instanceof BlockEntity be)) return;
        if (covers().tick(this, level, level.getGameTime())) level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
    }
}
