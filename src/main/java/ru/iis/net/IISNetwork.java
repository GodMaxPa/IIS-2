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

    /** Клавиша ночного зрения (D118). */
    public record NightVisionKey() implements CustomPacketPayload {
        public static final Type<NightVisionKey> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IIS.MODID, "night_vision_key"));
        public static final StreamCodec<ByteBuf, NightVisionKey> CODEC = StreamCodec.unit(new NightVisionKey());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клавиши оружия и машины: 0 — перезарядка, 1 — пушка машины (D128). */
    public record GunKey(int action) implements CustomPacketPayload {
        public static final Type<GunKey> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IIS.MODID, "gun_key"));
        public static final StreamCodec<ByteBuf, GunKey> CODEC = ByteBufCodecs.VAR_INT.map(GunKey::new, GunKey::action);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** След выстрела: кто стрелял и откуда куда (D128). */
    public record ShotFx(int shooter, int weapon, float x0, float y0, float z0, float x1, float y1, float z1, boolean kick) implements CustomPacketPayload {
        public static final Type<ShotFx> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IIS.MODID, "shot_fx"));
        public static final StreamCodec<ByteBuf, ShotFx> CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.shooter);
            ByteBufCodecs.VAR_INT.encode(buf, m.weapon);
            for (float f : new float[]{m.x0, m.y0, m.z0, m.x1, m.y1, m.z1}) buf.writeFloat(f);
            buf.writeBoolean(m.kick);
        }, buf -> new ShotFx(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Покупка в магазине наград (D130). */
    public record ShopBuy(String id, int times) implements CustomPacketPayload {
        public static final Type<ShopBuy> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IIS.MODID, "shop_buy"));
        public static final StreamCodec<ByteBuf, ShopBuy> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ShopBuy::id, ByteBufCodecs.VAR_INT, ShopBuy::times, ShopBuy::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void gunKey(Player player, int action) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (action == 0) ru.iis.equipment.GunActions.reloadKey(sp);
        else if (action == 1 && sp.getRootVehicle() instanceof ru.iis.equipment.vehicle.Driveable d) d.vehicleKey(sp);
    }

    public static void register(IEventBus modBus) {
        // D147: the held-Ctrl mark of a player who left does not stay in memory
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> CTRL.remove(e.getEntity().getUUID()));
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToServer(WrenchKeys.TYPE, WrenchKeys.CODEC, (msg, ctx) -> setCtrl(ctx.player(), msg.ctrl()))
                .playToServer(NightVisionKey.TYPE, NightVisionKey.CODEC, (msg, ctx) -> ru.iis.gear.GearEvents.toggleNightVision(ctx.player()))
                .playToServer(GunKey.TYPE, GunKey.CODEC, (msg, ctx) -> gunKey(ctx.player(), msg.action()))
                .playToServer(ShopBuy.TYPE, ShopBuy.CODEC, (msg, ctx) -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof ru.iis.shop.ShopContent.ShopMenu) ru.iis.shop.ShopContent.buy(sp, msg.id(), msg.times());
                })
                .playToClient(ShotFx.TYPE, ShotFx.CODEC, (msg, ctx) -> ru.iis.equipment.client.ShotFxClient.accept(msg)));
    }

    public static void setCtrl(Player player, boolean ctrl) {
        if (ctrl) CTRL.add(player.getUUID());
        else CTRL.remove(player.getUUID());
    }

    public static boolean isCtrlPressed(Player player) {
        return CTRL.contains(player.getUUID());
    }
}
