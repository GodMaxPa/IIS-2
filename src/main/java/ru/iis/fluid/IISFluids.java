package ru.iis.fluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ru.iis.IIS;

/**
 * Жидкости мода. Пар (GT5: Materials.Water.mGas / "steam") — газ без блока и ведра:
 * его нельзя вылить в мир, только передавать по трубам и хранить в машинах.
 */
public final class IISFluids {
    private IISFluids() {}

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, IIS.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, IIS.MODID);

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = TYPES.register("steam", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.iis.steam")
            .density(-1000).viscosity(200).temperature(375)
            .canSwim(false).canDrown(false).canPushEntity(false).canExtinguish(false).canHydrate(false).supportsBoating(false)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM = FLUIDS.register("steam", () -> new BaseFlowingFluid.Source(steamProps()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> STEAM_FLOWING = FLUIDS.register("steam_flowing", () -> new BaseFlowingFluid.Flowing(steamProps()));

    private static BaseFlowingFluid.Properties steamProps() {
        return new BaseFlowingFluid.Properties(STEAM_TYPE, STEAM, STEAM_FLOWING);
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}
