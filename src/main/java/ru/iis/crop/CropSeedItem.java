package ru.iis.crop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Мешочек семян IC2 (ItemCropSeed, D139): культура, рост, урожайность, стойкость (0–31) и уровень анализа (0–4). Без анализа — «неизвестные
 * семена»; анализатор открывает название (1), уровень и автора (2), признаки (3), свойства (4).
 */
public class CropSeedItem extends Item {
    public record Seed(String crop, int growth, int gain, int resistance, int scan) {
        public static final Codec<Seed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("crop").forGetter(Seed::crop), Codec.INT.fieldOf("growth").forGetter(Seed::growth),
                Codec.INT.fieldOf("gain").forGetter(Seed::gain), Codec.INT.fieldOf("resistance").forGetter(Seed::resistance),
                Codec.INT.fieldOf("scan").forGetter(Seed::scan)).apply(i, Seed::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Seed> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Seed::crop, ByteBufCodecs.VAR_INT, Seed::growth, ByteBufCodecs.VAR_INT, Seed::gain,
                ByteBufCodecs.VAR_INT, Seed::resistance, ByteBufCodecs.VAR_INT, Seed::scan, Seed::new);

        public @Nullable CropCard card() {
            return CropCard.byId(crop);
        }

        public Seed withScan(int s) {
            return new Seed(crop, growth, gain, resistance, s);
        }
    }

    public CropSeedItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(CropCard card, int growth, int gain, int resistance, int scan) {
        ItemStack s = new ItemStack(CropContent.CROP_SEED.get());
        s.set(CropContent.SEED.get(), new Seed(card.id(), growth, gain, resistance, scan));
        return s;
    }

    public static @Nullable Seed seed(ItemStack stack) {
        return stack.get(CropContent.SEED.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        Seed s = seed(stack);
        if (s == null || s.scan() <= 0 || s.card() == null) return Component.translatable("item.iis.crop_seed.unknown");
        return Component.translatable("item.iis.crop_seed.named", s.card().displayName());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Seed s = seed(stack);
        if (s == null || s.card() == null) return;
        CropCard c = s.card();
        if (s.scan() >= 2) builder.accept(Component.translatable("crop.iis.seed.tier", c.tier(), c.discoveredBy()).withStyle(ChatFormatting.GRAY));
        if (s.scan() >= 3) builder.accept(Component.translatable("crop.iis.seed.attrs", String.join(", ", c.attributes())).withStyle(ChatFormatting.GRAY));
        if (s.scan() >= 4) builder.accept(Component.translatable("crop.iis.seed.stats", s.growth(), s.gain(), s.resistance()).withStyle(ChatFormatting.DARK_GREEN));
        if (s.scan() >= 4 && c.block() != null) builder.accept(Component.translatable("crop.iis.lens.block", c.block()).withStyle(ChatFormatting.LIGHT_PURPLE));
        builder.accept(Component.translatable("crop.iis.seed.scan", s.scan()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
