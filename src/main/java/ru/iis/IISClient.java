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
        ru.iis.gear.GearClient.register(modBus);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.ItemTintSources e) -> {
            e.register(Identifier.fromNamespaceAndPath(IIS.MODID, "fluid_content"), ru.iis.fluid.client.FluidContentTint.CODEC);
            e.register(Identifier.fromNamespaceAndPath(IIS.MODID, "rotor_material"), ru.iis.multi.client.RotorTint.CODEC);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent e) ->
                e.register(Identifier.fromNamespaceAndPath(IIS.MODID, "battery_charge"), ru.iis.lv.client.BatteryChargeProperty.CODEC));
        modBus.addListener(IISClient::registerFluidModels);
        modBus.addListener(IISClient::registerCoverRenderers);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(ru.iis.space.client.SpaceClient.ROCKET_LAYER, ru.iis.space.client.SpaceClient::rocketLayer));
        NeoForge.EVENT_BUS.addListener(IISClient::machineTooltips);
        NeoForge.EVENT_BUS.addListener(ru.iis.client.WrenchGrid::onExtract);
        // GT_Packet_Key: перед ПКМ гаечным ключом сообщить серверу, зажат ли Ctrl (выход жидкостей машины, D101)
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, true, net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, e -> {
            if (e.getLevel().isClientSide() && ru.iis.machine.block.Wrench.is(e.getItemStack())) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                        new ru.iis.net.IISNetwork.WrenchKeys(net.minecraft.client.Minecraft.getInstance().hasControlDown()));
            }
        });
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
        event.register(List.of(BlockTintSources.constant(0xFF5F8F3F)), ru.iis.lv.LvContent.RUBBER_LEAVES.get());
        // пути Railcraft (D102): ванильные текстуры рельсов, окрашенные по типу пути
        ru.iis.rail.RailContent.TRACKS.values().forEach(b -> event.register(
                List.of(BlockTintSources.constant(0xFF000000 | ((ru.iis.rail.IisRail) b.get()).kind().color)), b.get()));
    }

    /** Покрытия GT на гранях электроблоков. */
    private static void registerCoverRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ru.iis.lv.LvContent.ELECTRIC_MACHINE_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        event.registerBlockEntityRenderer(ru.iis.lv.LvContent.GENERATOR_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        event.registerBlockEntityRenderer(ru.iis.lv.LvContent.BATTERY_BUFFER_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        event.registerBlockEntityRenderer(ru.iis.lv.LvContent.TRANSFORMER_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        // покрытия на паровых машинах, трубах и люках (D80)
        event.registerBlockEntityRenderer(ru.iis.machine.IISMachines.STEAM_MACHINE_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        event.registerBlockEntityRenderer(ru.iis.machine.IISMachines.PIPE_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        event.registerBlockEntityRenderer(ru.iis.multi.MultiContent.ITEM_BUS_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        event.registerBlockEntityRenderer(ru.iis.multi.MultiContent.FLUID_HATCH_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        event.registerBlockEntityRenderer(ru.iis.multi.MultiContent.ENERGY_HATCH_BE.get(), c -> new ru.iis.cover.client.CoverRenderer<>());
        // локомотивы Railcraft (D102): вагонетка с блоком котла или буфера батарей
        event.registerEntityRenderer(ru.iis.space.SpaceContent.ROCKET.get(), ru.iis.space.client.SpaceClient.RocketRenderer::new);
        event.registerEntityRenderer(ru.iis.rail.RailContent.STEAM_LOCO.get(),
                c -> new net.minecraft.client.renderer.entity.MinecartRenderer(c, net.minecraft.client.model.geom.ModelLayers.MINECART));
        event.registerEntityRenderer(ru.iis.rail.RailContent.ELECTRIC_LOCO.get(),
                c -> new net.minecraft.client.renderer.entity.MinecartRenderer(c, net.minecraft.client.model.geom.ModelLayers.MINECART));
        event.registerEntityRenderer(ru.iis.rail.RailContent.TRACK_LAYER_CART.get(),
                c -> new net.minecraft.client.renderer.entity.MinecartRenderer(c, net.minecraft.client.model.geom.ModelLayers.MINECART));
        event.registerEntityRenderer(ru.iis.rail.RailContent.TUNNEL_BORE_CART.get(),
                c -> new net.minecraft.client.renderer.entity.MinecartRenderer(c, net.minecraft.client.model.geom.ModelLayers.MINECART));
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(IISMachines.BOILER_MENU.get(), BoilerScreen::new);
        event.register(IISMachines.STEAM_MACHINE_MENU.get(), SteamMachineScreen::new);
        event.register(IISMachines.BBF_MENU.get(), BbfScreen::new);
        event.register(ru.iis.lv.LvContent.ELECTRIC_MACHINE_MENU.get(), ru.iis.lv.client.ElectricMachineScreen::new);
        event.register(ru.iis.lv.LvContent.GENERATOR_MENU.get(), ru.iis.lv.client.GeneratorScreen::new);
        event.register(ru.iis.lv.LvContent.BATTERY_BUFFER_MENU.get(), ru.iis.lv.client.BatteryBufferScreen::new);
        event.register(ru.iis.lv.LvContent.COKE_OVEN_MENU.get(), ru.iis.lv.client.CokeOvenScreen::new);
        event.register(ru.iis.lv.LvContent.COVER_MENU.get(), ru.iis.cover.client.CoverScreen::new);
        event.register(ru.iis.multi.MultiContent.ITEM_BUS_MENU.get(), ru.iis.multi.client.MultiScreens.Bus::new);
        event.register(ru.iis.multi.MultiContent.FLUID_HATCH_MENU.get(), ru.iis.multi.client.MultiScreens.FluidHatch::new);
        event.register(ru.iis.multi.MultiContent.EBF_MENU.get(), ru.iis.multi.client.MultiScreens.Ebf::new);
        event.register(ru.iis.multi.MultiContent.VACUUM_FREEZER_MENU.get(), ru.iis.multi.client.MultiScreens.Freezer::new);
        event.register(ru.iis.multi.MultiContent.LARGE_BOILER_MENU.get(), ru.iis.multi.client.MultiScreens.LargeBoiler::new);
        event.register(ru.iis.multi.MultiContent.LARGE_TURBINE_MENU.get(), ru.iis.multi.client.MultiScreens.LargeTurbine::new);
        event.register(ru.iis.multi.MultiContent.HEAT_EXCHANGER_MENU.get(), ru.iis.multi.client.MultiScreens.HeatExchanger::new);
        event.register(ru.iis.multi.MultiContent.PROCESSING_ARRAY_MENU.get(), ru.iis.multi.client.MultiScreens.ProcessingArray::new);
        event.register(ru.iis.multi.MultiContent.FUSION_MENU.get(), ru.iis.multi.client.MultiScreens.Fusion::new);
        event.register(ru.iis.multi.MultiContent.ASSEMBLY_LINE_MENU.get(), ru.iis.multi.client.MultiScreens.AssemblyLine::new);
        event.register(ru.iis.multi.MultiContent.CLEANROOM_MENU.get(), ru.iis.multi.client.MultiScreens.Cleanroom::new);
        event.register(ru.iis.multi.MultiContent.DRILL_MENU.get(), ru.iis.multi.client.MultiScreens.Drill::new);
        event.register(ru.iis.auto.AutoContent.AUTOMATION_MENU.get(), ru.iis.auto.AutoScreens.Automation::new);
        event.register(ru.iis.auto.AutoContent.QUANTUM_CHEST_MENU.get(), ru.iis.auto.AutoScreens.QuantumChest::new);
        event.register(ru.iis.auto.AutoContent.BASIC_DRILLER_MENU.get(), ru.iis.auto.AutoScreens.Driller::new);
        event.register(ru.iis.auto.AutoContent.ADJUSTABLE_TRANSFORMER_MENU.get(), ru.iis.auto.AutoScreens.Transformer::new);
        event.register(ru.iis.bio.BioContent.STATION_MENU.get(), ru.iis.bio.client.BioStationScreen::new);
        event.register(ru.iis.bio.BioContent.HEARTH_MENU.get(), ru.iis.bio.client.HearthScreen::new);
        event.register(ru.iis.reactor.ReactorContent.REACTOR_MENU.get(), ru.iis.reactor.ReactorScreen::new);
        event.register(ru.iis.space.SpaceContent.NASA_MENU.get(), ru.iis.space.client.SpaceClient.NasaScreen::new);
        event.register(ru.iis.space.SpaceContent.ROCKET_MENU.get(), ru.iis.space.client.SpaceClient.RocketScreen::new);
        event.register(ru.iis.rail.RailContent.LOCO_MENU.get(), ru.iis.rail.client.LocomotiveScreen::new);
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        Material steam = new Material(Identifier.fromNamespaceAndPath(IIS.MODID, "block/fluid/steam"));
        event.register(new FluidModel.Unbaked(steam, steam, null, (FluidTintSource) null), IISFluids.STEAM, IISFluids.STEAM_FLOWING);
        Material creosote = new Material(Identifier.fromNamespaceAndPath(IIS.MODID, "block/fluid/creosote"));
        event.register(new FluidModel.Unbaked(creosote, creosote, null, (FluidTintSource) null), IISFluids.CREOSOTE, IISFluids.CREOSOTE_FLOWING);
        // жидкости GT5 (D72): общая текстура по состоянию, окраска цветом материала
        String[] byState = {"gt_solid", "gt_liquid", "gt_gas", "gt_plasma", "gt_molten"};
        for (ru.iis.fluid.GtFluids.Def d : ru.iis.fluid.GtFluids.all().values()) {
            Material m = new Material(Identifier.fromNamespaceAndPath(IIS.MODID, "block/fluid/" + byState[Math.max(0, Math.min(4, d.state()))]));
            event.register(new FluidModel.Unbaked(m, m, null, net.neoforged.neoforge.client.fluid.FluidTintSources.constant(0xFF000000 | d.color())), d.source(), d.flowing());
        }
        for (ru.iis.bio.fluid.BioFluids.Def d : ru.iis.bio.fluid.BioFluids.ALL.values()) {
            Material m = new Material(ru.iis.bio.fluid.BioFluids.still(d));
            event.register(new FluidModel.Unbaked(m, m, null, (FluidTintSource) null), d.source(), d.flowing());
        }
    }

    /** Строки описания из перевода: block.iis.<id>.tooltip.0, .1, ... (генерирует tools/gen/steam.py из GregTech.lang.ru). */
    private static void machineTooltips(ItemTooltipEvent event) {
        if (ru.iis.cover.Covers.kindOf(event.getItemStack()) != null) {
            for (int i = 0; i < 3; i++) event.getToolTip().add(Component.translatable("cover.iis.tooltip." + i).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        }
        if (!(event.getItemStack().getItem() instanceof BlockItem bi)) {
            // предметы Био: item.iis.<id>.tooltip.0, .1, ... (tools/gen/bio_b1.py)
            Identifier iid = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
            if (!IIS.MODID.equals(iid.getNamespace())) return;
            String ibase = "item.iis." + iid.getPath() + ".tooltip.";
            for (int i = 0; I18n.exists(ibase + i); i++) event.getToolTip().add(1 + i, Component.translatable(ibase + i).withStyle(ChatFormatting.GRAY));
            return;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(bi.getBlock());
        if (!IIS.MODID.equals(id.getNamespace())) return;
        String base = "block.iis." + id.getPath() + ".tooltip.";
        for (int i = 0; I18n.exists(base + i); i++) {
            event.getToolTip().add(1 + i, Component.translatable(base + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
