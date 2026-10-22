package ru.iis.bio.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

/**
 * Мерный ковш (D70, решение владельца Р6): одна жидкость до 1 000 мБ, порция 50 / 250 / 1 000 мБ.
 * ПКМ по воде — набрать воду; ПКМ в воздух — сменить порцию; Shift + ПКМ в воздух — вылить содержимое (без пользы).
 * Наливать и набирать из органов и ручных мест — ПКМ по ним (см. {@code ru.iis.bio.production.FluidHands}).
 */
public class LadleItem extends Item {
    public static final int CAPACITY = 1000;
    public static final int[] DOSES = {50, 250, 1000};

    public record Content(String fluid, int amount, int dose) {
        public static final Content EMPTY = new Content("", 0, 250);
        public static final Codec<Content> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("fluid").forGetter(Content::fluid),
                Codec.INT.fieldOf("amount").forGetter(Content::amount),
                Codec.INT.fieldOf("dose").forGetter(Content::dose)).apply(i, Content::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Content> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Content::fluid, ByteBufCodecs.VAR_INT, Content::amount, ByteBufCodecs.VAR_INT, Content::dose, Content::new);

        public Fluid fluidValue() {
            if (fluid.isEmpty() || amount <= 0) return Fluids.EMPTY;
            return BuiltInRegistries.FLUID.getValue(Identifier.parse(fluid));
        }

        public boolean empty() {
            return amount <= 0 || fluidValue() == Fluids.EMPTY;
        }

        public Content with(Fluid f, int newAmount) {
            if (newAmount <= 0 || f == Fluids.EMPTY) return new Content("", 0, dose);
            return new Content(BuiltInRegistries.FLUID.getKey(f).toString(), newAmount, dose);
        }
    }

    public LadleItem(Properties properties) {
        super(properties);
    }

    public static Content content(ItemStack stack) {
        Content c = stack.get(BioItems.LADLE.get());
        return c == null ? Content.EMPTY : c;
    }

    public static void set(ItemStack stack, Content c) {
        stack.set(BioItems.LADLE.get(), c);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Content c = content(stack);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)
                && (c.empty() || c.fluidValue() == Fluids.WATER) && c.amount() < CAPACITY) {
            if (!level.isClientSide()) {
                set(stack, c.with(Fluids.WATER, Math.min(CAPACITY, c.amount() + c.dose())));
                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                if (!c.empty()) {
                    set(stack, c.with(Fluids.EMPTY, 0));
                    level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.7f, 1.2f);
                    player.sendOverlayMessage(Component.translatable("message.iis.bio.ladle.poured"));
                }
            } else {
                int next = DOSES[0];
                for (int i = 0; i < DOSES.length; i++) if (DOSES[i] == c.dose()) next = DOSES[(i + 1) % DOSES.length];
                set(stack, new Content(c.fluid(), c.amount(), next));
                player.sendOverlayMessage(Component.translatable("message.iis.bio.ladle.dose", next));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Content c = content(stack);
        Component what = c.empty() ? Component.translatable("gui.iis.empty") : c.fluidValue().getFluidType().getDescription();
        builder.accept(Component.translatable("item.iis.measuring_ladle.content", what, c.amount(), CAPACITY).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.iis.measuring_ladle.dose", c.dose()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !content(stack).empty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * content(stack).amount() / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ru.iis.bio.fluid.BioFluids.color(content(stack).fluidValue()) & 0xFFFFFF;
    }
}
