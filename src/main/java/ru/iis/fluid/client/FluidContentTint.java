package ru.iis.fluid.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import ru.iis.fluid.FluidColors;
import ru.iis.fluid.FluidContainers;
import ru.iis.fluid.FluidContent;

/** Цвет жидкости в капсуле (слой 1 модели): по компоненту содержимого; пустая — прозрачный слой. */
public record FluidContentTint() implements ItemTintSource {
    public static final MapCodec<FluidContentTint> CODEC = MapCodec.unit(new FluidContentTint());

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        FluidContent c = stack.get(FluidContainers.CONTENT.get());
        if (c == null || c.isEmpty()) return 0x00FFFFFF;
        return FluidColors.argb(c.fluidValue()) | 0xFF000000;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return CODEC;
    }
}
