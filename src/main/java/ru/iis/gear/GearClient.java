package ru.iis.gear;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import ru.iis.net.IISNetwork;

/** Клавиша ночного зрения и прыжок для ранца. */
public final class GearClient {
    private GearClient() {}

    private static final KeyMapping NIGHT_VISION = new KeyMapping("key.iis.night_vision", InputConstants.KEY_N, KeyMapping.Category.GAMEPLAY);

    public static void register(IEventBus modBus) {
        GearEvents.clientJump = p -> p instanceof LocalPlayer lp && lp.input.keyPresses.jump();
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(NIGHT_VISION));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (NIGHT_VISION.consumeClick()) {
                if (Minecraft.getInstance().player != null) ClientPacketDistributor.sendToServer(new IISNetwork.NightVisionKey());
            }
        });
    }
}
