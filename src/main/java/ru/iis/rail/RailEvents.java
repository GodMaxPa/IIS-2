package ru.iis.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import ru.iis.rail.block.LogicTrackBlock;
import ru.iis.rail.cart.TrainLinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** События железной дороги: действие путей на вагонетку, режим высокой скорости, сцепка монтировкой (D102). */
public final class RailEvents {
    private RailEvents() {}

    /** Вагонетка, выбранная монтировкой (первый щелчок), по игроку. */
    private static final Map<UUID, UUID> SELECTED = new ConcurrentHashMap<>();

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractMinecart cart) || !(cart.level() instanceof ServerLevel level) || cart.isRemoved()) return;
        var data = cart.getPersistentData();
        int cd = data.getIntOr(LogicTrackBlock.LOCK_COOLDOWN, 0);
        if (cd > 0) {
            if (cd == 1) data.remove(LogicTrackBlock.LOCK_COOLDOWN);
            else data.putInt(LogicTrackBlock.LOCK_COOLDOWN, cd - 1);
        }
        BlockPos pos = cart.getCurrentBlockPosOrRailBelow();
        BlockState state = level.getBlockState(pos);
        boolean onRail = state.getBlock() instanceof IisRail;
        if (onRail) ((IisRail) state.getBlock()).onCartPass(state, level, pos, cart);
        else if (data.contains(LogicTrackBlock.LOCK_DIR) && cd == 0) data.remove(LogicTrackBlock.LOCK_DIR);
        // MinecartHooks#onMinecartUpdate: режим высокой скорости гаснет ниже 0.39, вне скоростного пути — взрыв
        if (RailSpeed.isHighSpeed(cart) && !cart.isRemoved()) {
            if (RailSpeed.slowerThan(cart, TrackKind.SPEED_CUTOFF)) RailSpeed.setHighSpeed(cart, false);
            else if (!RailSpeed.highSpeedCapable(level, pos)) RailSpeed.explode(level, cart);
        }
        if (!cart.isRemoved()) TrainLinks.tick(level, cart);
    }

    /** Разбитая вагонетка освобождает сцепки соседей. */
    /**
     * Очки путейца ({@code ItemGoggles}): надетые, раз в 10 тиков показывают над хотбаром сведения о сигнале под
     * прицелом — тип, показание, с чем спарен (вместо «ауры» линий Railcraft).
     */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (!(p.level() instanceof ServerLevel sl) || p.tickCount % 10 != 0) return;
        if (!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(RailContent.GOGGLES.get())) return;
        if (!(p.pick(p.blockInteractionRange(), 0, false) instanceof net.minecraft.world.phys.BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        if (!(sl.getBlockEntity(hit.getBlockPos()) instanceof ru.iis.rail.signal.SignalBlockEntity be)) return;
        BlockPos partner = be.partner();
        Component aspect = Component.translatable("signal.iis.aspect." + be.aspect().getSerializedName());
        Component pair = partner == null ? Component.translatable("message.iis.goggles.unpaired")
                : Component.translatable("message.iis.goggles.paired", partner.getX(), partner.getY(), partner.getZ());
        p.sendOverlayMessage(Component.translatable("message.iis.goggles", aspect, pair));
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractMinecart cart && event.getLevel() instanceof ServerLevel level
                && cart.getRemovalReason() != null && cart.getRemovalReason().shouldDestroy()) {
            TrainLinks.breakAll(level, cart);
        }
    }

    /**
     * Монтировка по вагонетке (Railcraft {@code CrowbarHandler}): первый щелчок выбирает, второй по другой вагонетке
     * сцепляет их или, если уже сцеплены, расцепляет.
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(event.getTarget() instanceof AbstractMinecart cart) || !stack.is(RailContent.CROWBARS)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Player player = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        UUID prev = SELECTED.remove(player.getUUID());
        if (prev == null || prev.equals(cart.getUUID()) || !(level.getEntity(prev) instanceof AbstractMinecart first)) {
            SELECTED.put(player.getUUID(), cart.getUUID());
            player.sendOverlayMessage(Component.translatable("message.iis.rail.link_selected"));
            return;
        }
        String msg;
        if (TrainLinks.areLinked(first, cart)) {
            TrainLinks.breakLink(first, cart);
            msg = "message.iis.rail.unlinked";
        } else if (TrainLinks.createLink(first, cart)) {
            msg = "message.iis.rail.linked";
            if (!player.getAbilities().instabuild) stack.hurtAndBreak(1, player, event.getHand());
        } else {
            msg = "message.iis.rail.link_failed";
        }
        player.sendOverlayMessage(Component.translatable(msg));
    }
}
