package ru.iis.rail.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Геодезист ({@code ItemSignalBlockSurveyor}): ПКМ по двум блок-сигналам — спарить их.
 * Тюнер ({@code ItemSignalTuner}): ПКМ по контроллеру (коробке-контроллеру или блок-сигналу), затем по приёмнику —
 * настроить приёмник на него. Shift+ПКМ сбрасывает выбор.
 */
public class PairingToolItem extends Item {
    private static final Map<UUID, BlockPos> FIRST = new ConcurrentHashMap<>();

    public final boolean tuner;

    public PairingToolItem(boolean tuner, Properties p) {
        super(p);
        this.tuner = tuner;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof SignalBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos().immutable();
        String tool = tuner ? "tuner" : "surveyor";
        if (player.isShiftKeyDown()) {
            FIRST.remove(player.getUUID());
            player.sendOverlayMessage(Component.translatable("message.iis.signal.cleared"));
            return InteractionResult.SUCCESS;
        }
        BlockPos first = FIRST.get(player.getUUID());
        boolean firstOk = tuner ? be.isController() : be.type() == SignalBlock.Type.BLOCK_SIGNAL;
        if (first == null || first.equals(pos) || !(level.getBlockEntity(first) instanceof SignalBlockEntity a)) {
            if (!firstOk) {
                player.sendOverlayMessage(Component.translatable("message.iis.signal." + tool + ".wrong_first"));
                return InteractionResult.FAIL;
            }
            FIRST.put(player.getUUID(), pos);
            player.sendOverlayMessage(Component.translatable("message.iis.signal." + tool + ".first"));
            return InteractionResult.SUCCESS;
        }
        if (first.distManhattan(pos) > SignalBlockEntity.MAX_PAIR_DISTANCE) {
            player.sendOverlayMessage(Component.translatable("message.iis.signal.too_far", SignalBlockEntity.MAX_PAIR_DISTANCE));
            return InteractionResult.FAIL;
        }
        if (tuner) {
            if (be.type() != SignalBlock.Type.RECEIVER_BOX) {
                player.sendOverlayMessage(Component.translatable("message.iis.signal.tuner.wrong_second"));
                return InteractionResult.FAIL;
            }
            be.setPartner(first);
        } else {
            if (be.type() != SignalBlock.Type.BLOCK_SIGNAL) {
                player.sendOverlayMessage(Component.translatable("message.iis.signal.surveyor.wrong_first"));
                return InteractionResult.FAIL;
            }
            unpair(level, a.partner(), first);
            unpair(level, be.partner(), pos);
            a.setPartner(pos);
            be.setPartner(first);
        }
        FIRST.remove(player.getUUID());
        player.sendOverlayMessage(Component.translatable("message.iis.signal.paired"));
        return InteractionResult.SUCCESS;
    }

    /** Старая пара блок-сигнала теряет ссылку на него. */
    private static void unpair(Level level, BlockPos oldPartner, BlockPos self) {
        if (oldPartner != null && level.getBlockEntity(oldPartner) instanceof SignalBlockEntity o && self.equals(o.partner())) o.setPartner(null);
    }

    /** Для тестов: спарить два блока без игрока. */
    public static boolean pair(Level level, BlockPos a, BlockPos b, boolean tuner) {
        if (!(level.getBlockEntity(a) instanceof SignalBlockEntity x) || !(level.getBlockEntity(b) instanceof SignalBlockEntity y)) return false;
        if (tuner) {
            if (!x.isController() || y.type() != SignalBlock.Type.RECEIVER_BOX) return false;
            y.setPartner(a);
        } else {
            if (x.type() != SignalBlock.Type.BLOCK_SIGNAL || y.type() != SignalBlock.Type.BLOCK_SIGNAL) return false;
            x.setPartner(b);
            y.setPartner(a);
        }
        return true;
    }
}
