package ru.iis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import ru.iis.core.material.IISMaterial;
import ru.iis.fluid.IISFluids;
import ru.iis.machine.IISMachines;
import ru.iis.machine.client.BbfScreen;
import ru.iis.machine.client.BoilerScreen;
import ru.iis.machine.client.SteamMachineScreen;
import ru.iis.world.ore.OreBlocks;

import java.util.List;

/** Клиентская часть: окраска руд и труб цветом материала, экраны машин, модель пара, описания машин. */
@Mod(value = IIS.MODID, dist = Dist.CLIENT)
public final class IISClient {
    public IISClient(IEventBus modBus) {
        modBus.addListener(IISClient::registerBlockTints);
        modBus.addListener(IISClient::registerScreens);
        modBus.addListener(IISClient::registerFluidModels);
        NeoForge.EVENT_BUS.addListener(IISClient::machineTooltips);
        // раньше JEI: он читает рецепты в том же событии
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, ru.iis.machine.client.ClientRecipes::onReceived);
    }

    private static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        java.util.function.BiConsumer<String, java.util.EnumMap<ru.iis.world.ore.OreHost, net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block>>> tint = (materialId, byHost) -> {
            IISMaterial m = OreBlocks.material(materialId);
            int argb = 0xFF000000 | m.color();
            byHost.values().forEach(b -> event.register(List.of(BlockTintSources.constant(argb)), b.get()));
        };
        OreBlocks.all().forEach(tint);
        OreBlocks.allSmall().forEach(tint);
        IISMachines.PIPES.values().forEach(b -> {
            IISMaterial m = OreBlocks.material(b.get().spec.material());
            int rgb = m != null ? m.color() : b.get().spec.color();
            event.register(List.of(BlockTintSources.constant(0xFF000000 | rgb)), b.get());
        });
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(IISMachines.BOILER_MENU.get(), BoilerScreen::new);
        event.register(IISMachines.STEAM_MACHINE_MENU.get(), SteamMachineScreen::new);
        event.register(IISMachines.BBF_MENU.get(), BbfScreen::new);
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        Material steam = new Material(Identifier.fromNamespaceAndPath(IIS.MODID, "block/fluid/steam"));
        event.register(new FluidModel.Unbaked(steam, steam, null, (FluidTintSource) null), IISFluids.STEAM, IISFluids.STEAM_FLOWING);
    }

    /** Строки описания из перевода: block.iis.<id>.tooltip.0, .1, ... (генерирует tools/gen/steam.py из GregTech.lang.ru). */
    private static void machineTooltips(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof BlockItem bi)) return;
        Identifier id = BuiltInRegistries.BLOCK.getKey(bi.getBlock());
        if (!IIS.MODID.equals(id.getNamespace())) return;
        String base = "block.iis." + id.getPath() + ".tooltip.";
        for (int i = 0; I18n.exists(base + i); i++) {
            event.getToolTip().add(1 + i, Component.translatable(base + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
