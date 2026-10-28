package ru.iis.fluid;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Клик капсулой или ведром на курсоре по иконке жидкости в интерфейсе машины (D101, как GT_Container_BasicTank:
 * полный контейнер заливается во входной бак, пустой набирает из выходного, а если он пуст — из входного).
 */
public final class GuiFluidClick {
    private GuiFluidClick() {}

    /** true — что-то перелилось; новый контейнер ложится на курсор (или в инвентарь, если на курсоре была стопка). */
    public static boolean click(Player player, AbstractContainerMenu menu, ResourceHandler<FluidResource> in, @Nullable ResourceHandler<FluidResource> out) {
        ItemStack carried = menu.getCarried();
        ItemStack result = result(carried, in, out);
        if (result == null) return false;
        if (carried.getCount() == 1) {
            menu.setCarried(result);
        } else {
            carried.shrink(1);
            if (!player.getInventory().add(result)) player.drop(result, false);
        }
        player.level().playSound(null, player.blockPosition(),
                result.is(Items.BUCKET) || (result.getItem() instanceof CapsuleItem && CapsuleItem.content(result).isEmpty())
                        ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    /** Перелить и вернуть новый контейнер (один предмет), null — ничего не перелилось. */
    public static @Nullable ItemStack result(ItemStack carried, ResourceHandler<FluidResource> in, @Nullable ResourceHandler<FluidResource> out) {
        if (carried.isEmpty()) return null;
        if (carried.getItem() instanceof CapsuleItem capsule) {
            FluidContent c = CapsuleItem.content(carried);
            if (!c.isEmpty()) {
                Fluid f = c.fluidValue();
                try (Transaction tx = Transaction.openRoot()) {
                    int moved = in.insert(FluidResource.of(f), c.amount(), tx);
                    if (moved <= 0 || (!capsule.universal && moved < c.amount())) return null;
                    tx.commit();
                    return CapsuleItem.withContent(carried, FluidContent.of(f, c.amount() - moved));
                }
            }
            for (ResourceHandler<FluidResource> h : sources(in, out)) {
                FluidResource r = h.getResource(0);
                if (r.isEmpty()) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    int got = h.extract(r, CapsuleItem.CAPACITY, tx);
                    if (got <= 0 || (!capsule.universal && got < CapsuleItem.CAPACITY)) continue;
                    tx.commit();
                    return CapsuleItem.withContent(carried, FluidContent.of(r.getFluid(), got));
                }
            }
            return null;
        }
        if (!(carried.getItem() instanceof BucketItem bucket)) return null;
        Fluid f = bucket.getContent();
        if (f != Fluids.EMPTY) {
            try (Transaction tx = Transaction.openRoot()) {
                if (in.insert(FluidResource.of(f), 1000, tx) != 1000) return null;
                tx.commit();
                return new ItemStack(Items.BUCKET);
            }
        }
        for (ResourceHandler<FluidResource> h : sources(in, out)) {
            FluidResource r = h.getResource(0);
            if (r.isEmpty() || r.getFluid().getBucket() == Items.AIR) continue;
            try (Transaction tx = Transaction.openRoot()) {
                if (h.extract(r, 1000, tx) != 1000) continue;
                tx.commit();
                return new ItemStack(r.getFluid().getBucket());
            }
        }
        return null;
    }

    private static java.util.List<ResourceHandler<FluidResource>> sources(ResourceHandler<FluidResource> in, @Nullable ResourceHandler<FluidResource> out) {
        return out == null ? java.util.List.of(in) : java.util.List.of(out, in);
    }
}
