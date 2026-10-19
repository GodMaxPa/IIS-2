package ru.iis.energy.logic;

/** Провод или кабель GT: предельное напряжение, ампераж и потеря напряжения на каждый блок (GT_MetaPipeEntity_Cable). */
public record CableSpec(long voltage, long amperage, long lossPerBlock, boolean insulated) {}
