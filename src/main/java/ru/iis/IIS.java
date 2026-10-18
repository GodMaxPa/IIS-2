package ru.iis;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import ru.iis.command.IISCommand;

/**
 * Точка входа мода Ideal Industrial 2.0.
 * Здесь только подключение подсистем; игровая логика живёт в своих пакетах.
 */
@Mod(IIS.MODID)
public final class IIS {
    public static final String MODID = "iis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IIS(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(IISCommand::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Маркер для автоматической проверки запуска (scripts/smoke-server.sh ищет эту строку).
        LOGGER.info("[IIS] Ideal Industrial 2.0 loaded");
    }
}
