package ru.iis.machine.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** Выход рецепта GT: предмет и шанс в десятитысячных (10000 = 100%). */
public record ChanceOutput(ItemStackTemplate stack, int chance) {
    public static final int ALWAYS = 10000;

    public static final Codec<ChanceOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.MAP_CODEC.forGetter(ChanceOutput::stack),
            Codec.intRange(1, ALWAYS).optionalFieldOf("chance", ALWAYS).forGetter(ChanceOutput::chance)
    ).apply(i, ChanceOutput::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChanceOutput> STREAM_CODEC = StreamCodec.composite(
            ItemStackTemplate.STREAM_CODEC, ChanceOutput::stack,
            ByteBufCodecs.VAR_INT, ChanceOutput::chance,
            ChanceOutput::new);

    public ItemStack create() {
        return stack.create();
    }
}
