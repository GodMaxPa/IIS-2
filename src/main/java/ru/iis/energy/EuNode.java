package ru.iis.energy;

import net.minecraft.core.Direction;

/**
 * Блок-сущность, принимающая EU из сети (GT5 IEnergyConnected#injectEnergyUnits).
 * Возвращает принятые амперы; перенапряжение обрабатывает сама (взрыв).
 */
public interface EuNode {
    long injectEnergy(Direction from, long voltage, long amperes);
}
