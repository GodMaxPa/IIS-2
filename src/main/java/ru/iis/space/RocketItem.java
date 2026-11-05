package ru.iis.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** Ракета предметом: ставится на середину взлётной площадки 3×3. Хранит размер груза и остаток топлива. */
public class RocketItem extends Item {
    public final int tier;

    public record Stored(int cargo, int fuel) {
        public static final Stored EMPTY = new Stored(0, 0);
        public static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("cargo").forGetter(Stored::cargo), Codec.INT.fieldOf("fuel").forGetter(Stored::fuel)).apply(i, Stored::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Stored> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Stored::cargo, ByteBufCodecs.VAR_INT, Stored::fuel, Stored::new);
    }

    public RocketItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** Центр и восемь соседей — площадка. */
    public static boolean fullPad(Level level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getBlockState(center.offset(dx, 0, dz)).is(SpaceContent.LAUNCH_PAD.get())) return false;
            }
        }
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(SpaceContent.LAUNCH_PAD.get())) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!fullPad(level, pos)) {
            if (context.getPlayer() != null) context.getPlayer().sendOverlayMessage(Component.translatable("message.iis.rocket.pad"));
            return InteractionResult.FAIL;
        }
        RocketEntity rocket = SpaceContent.ROCKET.get().create((ServerLevel) level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (rocket == null) return InteractionResult.FAIL;
        rocket.setPos(pos.getX() + 0.5, pos.getY() + LaunchPadBlock.HEIGHT, pos.getZ() + 0.5);
        rocket.setup(context.getItemInHand().getOrDefault(SpaceContent.ROCKET_DATA.get(), Stored.EMPTY));
        level.addFreshEntity(rocket);
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Stored s = stack.getOrDefault(SpaceContent.ROCKET_DATA.get(), Stored.EMPTY);
        builder.accept(Component.translatable("item.iis.rocket_t1.hint").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.iis.rocket.cargo", s.cargo()).withStyle(ChatFormatting.GRAY));
        if (s.fuel() > 0) builder.accept(Component.translatable("item.iis.rocket.fuel", s.fuel()).withStyle(ChatFormatting.GRAY));
    }
}
