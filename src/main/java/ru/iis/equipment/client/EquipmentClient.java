package ru.iis.equipment.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import ru.iis.IIS;
import ru.iis.equipment.EquipmentContent;
import ru.iis.equipment.GunActions;
import ru.iis.equipment.Weapon;
import ru.iis.equipment.vehicle.Driveable;
import ru.iis.equipment.vehicle.VehicleKind;
import ru.iis.equipment.vehicle.VehiclePhysics;
import ru.iis.net.IISNetwork;
import ru.iis.rail.RailContent;

/** Клиент оружия и транспорта: модели, управление, приборы, прицел. */
public final class EquipmentClient {
    private EquipmentClient() {}

    private static final KeyMapping RELOAD = new KeyMapping("key.iis.reload", InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY);
    private static final KeyMapping VEHICLE_FIRE = new KeyMapping("key.iis.vehicle_fire", InputConstants.KEY_G, KeyMapping.Category.GAMEPLAY);

    public static void register(IEventBus modBus) {
        modBus.addListener(EquipmentClient::renderers);
        modBus.addListener(ArmorExtrasLayer::addToPlayers);
        modBus.addListener((RegisterSpecialModelRendererEvent e) -> e.register(Identifier.fromNamespaceAndPath(IIS.MODID, "mesh"), MeshItemRenderer.Unbaked.CODEC));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(RELOAD);
            e.register(VEHICLE_FIRE);
        });
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(EquipmentContent.VEHICLE_MENU.get(), VehicleScreen::new);
            e.register(ru.iis.shop.ShopContent.SHOP_MENU.get(), ru.iis.shop.ShopScreen::new);
        });
        modBus.addListener(EquipmentClient::extensions);
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(IIS.MODID, "equipment_hud"), EquipmentClient::hud));
        Driveable.clientInput = EquipmentClient::input;
        NeoForge.EVENT_BUS.addListener(EquipmentClient::keys);
        NeoForge.EVENT_BUS.addListener(ShotFxClient::tick);
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent e) -> ShotFxClient.submit(e));
        NeoForge.EVENT_BUS.addListener(EquipmentClient::cameraDistance);
        NeoForge.EVENT_BUS.addListener(EquipmentClient::fov);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (VehicleKind k : VehicleKind.values()) {
            e.registerEntityRenderer(EquipmentContent.VEHICLE_TYPES.get(k).get(), c -> new VehicleRenderer(c, Math.min(2.2F, k.width * 0.55F)));
        }
        e.registerEntityRenderer(EquipmentContent.SHOT.get(), ShotRenderer::new);
        e.registerEntityRenderer(RailContent.STEAM_LOCO.get(), c -> new TrainRenderer<>(c, "steam_loco"));
        e.registerEntityRenderer(RailContent.ELECTRIC_LOCO.get(), c -> new TrainRenderer<>(c, "electric_loco"));
        e.registerEntityRenderer(RailContent.TRACK_LAYER_CART.get(), c -> new TrainRenderer<>(c, "track_layer"));
        e.registerEntityRenderer(RailContent.TUNNEL_BORE_CART.get(), c -> new TrainRenderer<>(c, "tunnel_bore"));
        e.registerEntityRenderer(EntityType.MINECART, c -> new TrainRenderer<>(c, "wagon_passenger"));
        e.registerEntityRenderer(EntityType.CHEST_MINECART, c -> new TrainRenderer<>(c, "wagon_box"));
        e.registerEntityRenderer(EntityType.HOPPER_MINECART, c -> new TrainRenderer<>(c, "wagon_hopper"));
        e.registerEntityRenderer(EntityType.FURNACE_MINECART, c -> new TrainRenderer<>(c, "wagon_furnace"));
    }

    /** Руки и отдача оружия. */
    private static void extensions(RegisterClientExtensionsEvent e) {
        Item[] guns = EquipmentContent.GUNS.values().stream().map(h -> (Item) h.get()).toArray(Item[]::new);
        e.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial, float equip, float swing) {
                int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                boolean aim = player.isShiftKeyDown();
                float k = ShotFxClient.kickO + (ShotFxClient.kick - ShotFxClient.kickO) * partial;
                // при прицеливании ствол к середине и выше, у глаза
                pose.translate(side * (aim ? 0.0F : 0.42F), (aim ? -0.30F : -0.40F) + equip * -0.6F, (aim ? -0.55F : -0.72F) + k * 0.10F);
                pose.mulPose(Axis.XP.rotationDegrees(k * 5F));
                return true;
            }
        }, guns);
    }

    /** Что нажато у местного игрока: для физики машины. */
    private static VehiclePhysics.Input input(Player p) {
        if (!(p instanceof LocalPlayer lp)) return VehiclePhysics.Input.NONE;
        var k = lp.input.keyPresses;
        boolean down = Minecraft.getInstance().options.keySprint.isDown();
        return new VehiclePhysics.Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), down, p.getYRot(), p.getXRot(), true, true);
    }

    private static void keys(ClientTickEvent.Post e) {
        while (RELOAD.consumeClick()) {
            if (Minecraft.getInstance().player != null) ClientPacketDistributor.sendToServer(new IISNetwork.GunKey(0));
        }
        while (VEHICLE_FIRE.consumeClick()) {
            if (Minecraft.getInstance().player != null) ClientPacketDistributor.sendToServer(new IISNetwork.GunKey(1));
        }
    }

    private static void cameraDistance(CalculateDetachedCameraDistanceEvent e) {
        Player p = Minecraft.getInstance().player;
        if (p != null && p.getRootVehicle() instanceof Driveable d) e.setDistance(d.kind.camera);
    }

    private static void fov(ComputeFovModifierEvent e) {
        Player p = e.getPlayer();
        if (p.isShiftKeyDown() && p.getMainHandItem().getItem() instanceof GunActions.Gun g && g.weapon().zoom > 1F) {
            e.setNewFovModifier(e.getFovModifier() / g.weapon().zoom);
        }
    }

    // --- приборы --------------------------------------------------------------------------------------------------------------
    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int x = g.guiWidth() / 2 + 96, y = g.guiHeight() - 20;
        ItemStack hand = mc.player.getMainHandItem();
        if (hand.getItem() instanceof GunActions.Gun gun) gunHud(g, mc, hand, gun.weapon(), x, y);
        if (mc.player.getVehicle() instanceof Driveable d) vehicleHud(g, mc, d);
    }

    private static void gunHud(GuiGraphicsExtractor g, Minecraft mc, ItemStack stack, Weapon w, int x, int y) {
        Component text;
        if (w.electric()) {
            text = Component.translatable("hud.iis.gun.shots", ru.iis.lv.item.BatteryItem.charge(stack) / w.euPerShot);
        } else if (w.plasma()) {
            var tank = ru.iis.equipment.PlasmaGunItem.tank(stack);
            text = tank.isEmpty() ? Component.translatable("hud.iis.gun.plasma_empty") : Component.translatable("hud.iis.gun.plasma", tank.fluidValue().getFluidType().getDescription(), tank.amount() / w.plasmaLiters());
        } else if (GunActions.reloading(stack, mc.level.getGameTime())) {
            text = Component.translatable("hud.iis.gun.reloading");
        } else {
            int spare = mc.player.getAbilities().instabuild ? 0 : w.magazineType() != null ? GunActions.roundsInMagazines(mc.player, w.magazineType()) : GunActions.countAmmo(mc.player, w.ammo);
            text = Component.literal(GunActions.loaded(stack) + " / " + w.magazine + "  (" + spare + ")");
        }
        // длинная строка плазмы не помещается справа от панели: над панелью, у края экрана
        if (x + mc.font.width(text) > g.guiWidth() - 2) {
            x = g.guiWidth() - mc.font.width(text) - 4;
            y -= 14;
        }
        g.text(mc.font, text, x, y, 0xFFFFFFFF, true);
    }

    private static void vehicleHud(GuiGraphicsExtractor g, Minecraft mc, Driveable d) {
        int x = g.guiWidth() - 120, y = g.guiHeight() - 46;
        double speed = Math.sqrt(sq(d.getX() - d.xo) + sq(d.getY() - d.yo) + sq(d.getZ() - d.zo));
        g.fill(x - 4, y - 4, x + 116, y + 40, 0x90000000);
        g.text(mc.font, Component.translatable("hud.iis.vehicle.speed", VehicleKind.kmh(speed)), x, y, 0xFFFFFFFF, true);
        bar(g, x, y + 11, 110, 1.0 * d.fuel() / d.kind.tank, 0xFFE0B040);
        bar(g, x, y + 16, 110, d.vehicleHealth() / d.kind.health, 0xFF60C060);
        Component second;
        if (d.fuel() <= 0) second = Component.translatable("hud.iis.vehicle.no_fuel").withStyle(ChatFormatting.RED);
        else if (d.kind.move == VehicleKind.Move.PLANE) second = Component.translatable("hud.iis.vehicle.throttle", (int) (d.physics().throttle * 100), (int) d.getY());
        else if (d.kind.move == VehicleKind.Move.HELICOPTER) second = Component.translatable("hud.iis.vehicle.rotor", (int) (d.physics().rotor * 100), (int) d.getY());
        else second = Component.translatable("hud.iis.vehicle.fuel", 100 * d.fuel() / d.kind.tank);
        g.text(mc.font, second, x, y + 23, 0xFFD0D0D0, true);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, double part, int color) {
        g.fill(x, y, x + w, y + 3, 0xFF202020);
        g.fill(x, y, x + (int) (w * Math.max(0, Math.min(1, part))), y + 3, color);
    }
}
