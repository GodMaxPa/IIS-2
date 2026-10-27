package ru.iis.fluid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Капсулы ИИС 1 (D72). Обычная капсула GT/IC2 (Cell_Empty и «Капсула с X») — ровно 1 000 мБ одной жидкости: наполняется и
 * сливается только целиком. Универсальная капсула IC2 (Cell_Universal_Fluid) — любая жидкость любыми порциями до 1 000 мБ.
 * ПКМ по источнику воды/лавы — набрать; ПКМ по машине, трубе, резервуару, органу — налить или набрать (обработчик
 * {@link FluidContainers#onRightClickBlock}); ПКМ по пустому месту полной капсулой с водой/лавой — вылить в мир.
 */
public class CapsuleItem extends Item {
    public static final int CAPACITY = 1000;
    public final boolean universal;

    public CapsuleItem(boolean universal, Properties properties) {
        super(properties);
        this.universal = universal;
    }

    /** Как у капсул IC2/GT: полная капсула в верстаке (клей в текстолитовой плате GT, D92) оставляет пустую. */
    @Override
    public net.minecraft.world.item.@Nullable ItemStackTemplate getCraftingRemainder(net.minecraft.world.item.ItemInstance instance) {
        if (!(instance instanceof ItemStack stack) || stack.isEmpty() || content(stack).isEmpty()) return null;
        return net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(withContent(stack, FluidContent.EMPTY));
    }

    public static FluidContent content(ItemStack stack) {
        FluidContent c = stack.get(FluidContainers.CONTENT.get());
        return c == null ? FluidContent.EMPTY : c;
    }

    public static ItemStack withContent(ItemStack template, FluidContent c) {
        ItemStack s = template.copyWithCount(1);
        if (c.isEmpty()) s.remove(FluidContainers.CONTENT.get());
        else s.set(FluidContainers.CONTENT.get(), c);
        return s;
    }

    /** Отдать игроку изменённую капсулу (одну из стопки). */
    static void replaceOne(Player player, InteractionHand hand, ItemStack stack, ItemStack result) {
        if (player.getAbilities().instabuild) {
            if (!player.getInventory().add(result)) player.drop(result, false);
            return;
        }
        if (stack.getCount() == 1) {
            player.setItemInHand(hand, result);
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(result)) player.drop(result, false);
        }
    }

    /** Перелить между капсулой и обработчиком; true — что-то перелилось. */
    public boolean transfer(Level level, Player player, InteractionHand hand, ItemStack stack, ResourceHandler<FluidResource> h) {
        FluidContent c = content(stack);
        if (!c.isEmpty()) {
            Fluid f = c.fluidValue();
            try (Transaction tx = Transaction.openRoot()) {
                int moved = h.insert(FluidResource.of(f), c.amount(), tx);
                if (moved <= 0 || (!universal && moved < c.amount())) return false;
                tx.commit();
                replaceOne(player, hand, stack, withContent(stack, FluidContent.of(f, c.amount() - moved)));
                level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.6f, 1.4f);
                return true;
            }
        }
        for (int i = 0; i < h.size(); i++) {
            FluidResource r = h.getResource(i);
            if (r.isEmpty()) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int got = h.extract(r, CAPACITY, tx);
                if (got <= 0 || (!universal && got < CAPACITY)) continue;
                tx.commit();
                replaceOne(player, hand, stack, withContent(stack, FluidContent.of(r.getFluid(), got)));
                level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.6f, 1.4f);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        FluidContent c = content(stack);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, c.isEmpty() ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if (c.isEmpty()) {
            BlockState st = level.getBlockState(pos);
            FluidState fs = level.getFluidState(pos);
            if (!fs.isSource() || !(st.getBlock() instanceof BucketPickup pickup)) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                Fluid f = fs.getType();
                if (pickup.pickupBlock(player, level, pos, st).isEmpty()) return InteractionResult.FAIL;
                replaceOne(player, hand, stack, withContent(stack, FluidContent.of(f, CAPACITY)));
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        // вылить в мир — только целую капсулу жидкости, у которой есть блок (вода, лава)
        Fluid f = c.fluidValue();
        BlockState fluidBlock = f.defaultFluidState().createLegacyBlock();
        if (c.amount() < CAPACITY || fluidBlock.isAir() || !(f instanceof net.minecraft.world.level.material.FlowingFluid)) return InteractionResult.PASS;
        BlockPos target = level.getBlockState(pos).canBeReplaced() ? pos : pos.relative(hit.getDirection());
        if (!level.getBlockState(target).canBeReplaced()) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            if (level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, target) && f == Fluids.WATER) {
                level.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f);
            } else {
                level.setBlock(target, fluidBlock, 11);
                level.playSound(null, target, f == Fluids.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            replaceOne(player, hand, stack, withContent(stack, FluidContent.EMPTY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidContent c = content(stack);
        if (c.isEmpty()) return Component.translatable(getDescriptionId() + ".empty");
        return Component.translatable(getDescriptionId() + ".filled", c.fluidValue().getFluidType().getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FluidContent c = content(stack);
        if (!c.isEmpty()) builder.accept(Component.translatable("item.iis.capsule.amount", c.amount(), CAPACITY).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(universal ? "item.iis.universal_capsule.hint" : "item.iis.capsule.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    static @Nullable ResourceHandler<FluidResource> handlerAt(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
    }

    static boolean isServer(Level level) {
        return level instanceof ServerLevel;
    }
}
