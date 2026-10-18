package ru.iis.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.iis.IIS;
import ru.iis.world.vein.VeinFeature;

/**
 * Регистрация генерации мира. Сам feature подключается к биомам данными:
 * data/iis/neoforge/biome_modifier/add_gt_ore_veins.json (генерирует tools/gen/gen_ores.py).
 */
public final class IISWorldgen {
    private IISWorldgen() {}

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, IIS.MODID);

    public static final DeferredHolder<Feature<?>, VeinFeature> GT_ORE_VEINS =
            FEATURES.register("gt_ore_veins", () -> new VeinFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
