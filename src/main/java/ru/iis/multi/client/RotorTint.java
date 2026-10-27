package ru.iis.multi.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import ru.iis.core.material.MaterialData;
import ru.iis.multi.TurbineRotorItem;

/** Цвет ротора турбины (слой 0 модели, GT_Tool_Turbine#getRGBa): цвет материала из компонента {@code iis:rotor_material}. */
public record RotorTint() implements ItemTintSource {
    public static final MapCodec<RotorTint> CODEC = MapCodec.unit(new RotorTint());

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        MaterialData.ToolStats m = TurbineRotorItem.material(stack);
        return (m == null ? 0xFFFFFF : m.color()) | 0xFF000000;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return CODEC;
    }
}
