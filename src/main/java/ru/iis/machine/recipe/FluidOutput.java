package ru.iis.machine.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Жидкость на выходе рецепта: id + литры. FluidStack.CODEC при загрузке рецептов в 26.1 падает
 * («Fluid does not have components yet»), поэтому стопка создаётся только при выдаче.
 */
public record FluidOutput(Fluid fluid, int amount) {
    public static final Codec<FluidOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FluidOutput::fluid),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(FluidOutput::amount)
    ).apply(i, FluidOutput::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidOutput> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), FluidOutput::fluid,
            ByteBufCodecs.VAR_INT, FluidOutput::amount,
            FluidOutput::new);

    public FluidStack create() {
        return new FluidStack(fluid, amount);
    }
}
