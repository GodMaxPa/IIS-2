package ru.iis.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import ru.iis.IIS;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Сетевые сообщения мода. */
public final class IISNetwork {
    private IISNetwork() {}

    /** Игроки, у которых при последнем ПКМ ключом был зажат Ctrl (GT_Packet_Key.isCtrlPressed). */
    private static final Set<UUID> CTRL = ConcurrentHashMap.newKeySet();

    /** GT_Packet_Key: клиент перед ПКМ ключом сообщает, зажат ли Ctrl (сервер этого не знает). */
    public record WrenchKeys(boolean ctrl) implements CustomPacketPayload {
        public static final Type<WrenchKeys> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IIS.MODID, "wrench_keys"));
        public static final StreamCodec<ByteBuf, WrenchKeys> CODEC = ByteBufCodecs.BOOL.map(WrenchKeys::new, WrenchKeys::ctrl);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToServer(WrenchKeys.TYPE, WrenchKeys.CODEC, (msg, ctx) -> setCtrl(ctx.player(), msg.ctrl())));
    }

    public static void setCtrl(Player player, boolean ctrl) {
        if (ctrl) CTRL.add(player.getUUID());
        else CTRL.remove(player.getUUID());
    }

    public static boolean isCtrlPressed(Player player) {
        return CTRL.contains(player.getUUID());
    }
}
