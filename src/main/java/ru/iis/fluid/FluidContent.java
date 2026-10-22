package ru.iis.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Содержимое капсулы или резервуара в предмете: одна жидкость и её количество (мБ). */
public record FluidContent(String fluid, int amount) {
    public static final FluidContent EMPTY = new FluidContent("", 0);
    public static final Codec<FluidContent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("fluid").forGetter(FluidContent::fluid),
            Codec.INT.fieldOf("amount").forGetter(FluidContent::amount)).apply(i, FluidContent::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidContent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FluidContent::fluid, ByteBufCodecs.VAR_INT, FluidContent::amount, FluidContent::new);

    public static FluidContent of(Fluid f, int amount) {
        if (f == Fluids.EMPTY || amount <= 0) return EMPTY;
        return new FluidContent(BuiltInRegistries.FLUID.getKey(f).toString(), amount);
    }

    public Fluid fluidValue() {
        if (fluid.isEmpty() || amount <= 0) return Fluids.EMPTY;
        return BuiltInRegistries.FLUID.getValue(Identifier.parse(fluid));
    }

    public boolean isEmpty() {
        return amount <= 0 || fluidValue() == Fluids.EMPTY;
    }
}
