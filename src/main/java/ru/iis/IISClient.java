package ru.iis;

import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import ru.iis.core.material.IISMaterial;
import ru.iis.world.ore.OreBlocks;

import java.util.List;

/** Клиентская часть: окраска руд цветом материала (как в GT5 — серая текстура × цвет). */
@Mod(value = IIS.MODID, dist = Dist.CLIENT)
public final class IISClient {
    public IISClient(IEventBus modBus) {
        modBus.addListener(IISClient::registerBlockTints);
    }

    private static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        java.util.function.BiConsumer<String, java.util.EnumMap<ru.iis.world.ore.OreHost, net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block>>> tint = (materialId, byHost) -> {
            IISMaterial m = OreBlocks.material(materialId);
            int argb = 0xFF000000 | m.color();
            byHost.values().forEach(b -> event.register(List.of(BlockTintSources.constant(argb)), b.get()));
        };
        OreBlocks.all().forEach(tint);
        OreBlocks.allSmall().forEach(tint);
    }
}
