package ru.iis;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import ru.iis.command.IISCommand;
import ru.iis.world.IISWorldgen;
import ru.iis.world.ore.OreBlocks;
import ru.iis.core.item.MaterialItems;
import ru.iis.tool.GtTools;
import ru.iis.tool.HammerDrops;
import ru.iis.fluid.IISFluids;
import ru.iis.machine.IISMachines;
import ru.iis.machine.recipe.GtRecipes;

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
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, IISConfig.SPEC);
        OreBlocks.register(modEventBus);
        IISWorldgen.register(modEventBus);
        MaterialItems.register(modEventBus);
        modEventBus.addListener(IISPacks::addPacks);
        GtTools.register(modEventBus);
        IISFluids.register(modEventBus);
        IISMachines.register(modEventBus);
        ru.iis.lv.LvContent.register(modEventBus);
        ru.iis.multi.MultiContent.register(modEventBus);
        GtRecipes.register(modEventBus, NeoForge.EVENT_BUS);
        ru.iis.gametest.IISGameTests.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(HammerDrops::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(ru.iis.pollution.Pollution::onLevelTick);
        NeoForge.EVENT_BUS.addListener(IISCommand::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Маркер для автоматической проверки запуска (scripts/smoke-server.sh ищет эту строку).
        LOGGER.info("[IIS] Ideal Industrial 2.0 loaded");
    }
}
